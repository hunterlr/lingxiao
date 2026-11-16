package com.lingxiao.modules.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.job.dto.JobHandlerVO;
import com.lingxiao.modules.job.dto.JobLastRunVO;
import com.lingxiao.modules.job.entity.SysJob;
import com.lingxiao.modules.job.mapper.SysJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务管理。
 *
 * <p>保存前做三件事，缺一不可：</p>
 * <ol>
 *   <li><b>校验 cron</b>：{@link CronExpression#parse} 只接受 6 段表达式
 *       （秒 分 时 日 月 周），5 段 UNIX cron 直接拒绝，避免“保存成功但永不触发”；</li>
 *   <li><b>校验执行器</b>：必须能在 {@link JobHandlerRegistry} 里找到，
 *       否则任务注定失败；</li>
 *   <li><b>校验重名</b>：同名任务在日志里无法区分，运维排查会互相误导。</li>
 * </ol>
 *
 * <p>新增任务默认“暂停”：cron 写错、执行器有副作用时，不会因为一次保存就在凌晨跑起来。
 * 想让它跑，必须显式点“启动”。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class SysJobService {

    public static final String STATUS_NORMAL = "0";
    public static final String STATUS_PAUSED = "1";

    private static final String DEFAULT_GROUP = "DEFAULT";
    private static final String DEFAULT_CONCURRENT = "1";
    private static final String DEFAULT_MISFIRE = "3";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysJobMapper jobMapper;
    private final JobLockService lockService;
    private final JobHandlerRegistry handlerRegistry;
    private final JobScheduler jobScheduler;

    public PageResult<SysJob> page(long pageNum, long pageSize, String jobName,
                                   String jobGroup, String status) {
        Page<SysJob> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<SysJob>()
                .like(StringUtils.hasText(jobName), SysJob::getJobName, jobName)
                .eq(StringUtils.hasText(jobGroup), SysJob::getJobGroup, jobGroup)
                .eq(StringUtils.hasText(status), SysJob::getStatus, status)
                .orderByDesc(SysJob::getCreateTime);
        Page<SysJob> result = jobMapper.selectPage(page, wrapper);

        Map<Long, LocalDateTime> lastRunMap = new HashMap<>();
        for (JobLastRunVO item : jobMapper.selectLastRunTimes()) {
            lastRunMap.put(item.getJobId(), item.getLastRunTime());
        }
        for (SysJob job : result.getRecords()) {
            fillRuntimeInfo(job, lastRunMap.get(job.getJobId()));
        }

        PageResult<SysJob> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    public SysJob detail(Long jobId) {
        SysJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "定时任务不存在");
        }
        fillRuntimeInfo(job, null);
        return job;
    }

    /**
     * 新增任务。
     *
     * @return 任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysJob job) {
        normalize(job, true);
        validate(job, null);
        job.setJobId(null);
        jobMapper.insert(job);
        // 锁行与任务一起建，避免第一次执行时才补行；补行失败也不阻断（执行前会兜底）
        lockService.ensureLockRow(job.getJobId());
        jobScheduler.refresh(job);
        log.info("新增定时任务「{}」cron={} 状态={}", job.getJobName(), job.getCronExpression(), job.getStatus());
        return job.getJobId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long jobId, SysJob job) {
        SysJob existing = jobMapper.selectById(jobId);
        if (existing == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "定时任务不存在");
        }
        normalize(job, false);
        validate(job, jobId);
        job.setJobId(jobId);
        job.setJobGroup(StringUtils.hasText(job.getJobGroup()) ? job.getJobGroup() : existing.getJobGroup());
        job.setVersion(existing.getVersion());
        if (jobMapper.updateById(job) == 0) {
            throw new ServiceException(ErrorCode.FAIL, "任务已被他人修改，请刷新后重试");
        }
        SysJob saved = jobMapper.selectById(jobId);
        jobScheduler.refresh(saved);
        log.info("修改定时任务「{}」cron={} 状态={}", saved.getJobName(), saved.getCronExpression(), saved.getStatus());
    }

    /**
     * 删除任务。
     *
     * <p>只删任务定义和锁行，<b>不删历史日志</b>：出问题时要能查到“谁在什么时候删了它、
     * 删之前跑过什么”。日志由 {@code jobLogClean} 按保留天数统一清理。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long jobId) {
        SysJob existing = jobMapper.selectById(jobId);
        if (existing == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "定时任务不存在");
        }
        jobScheduler.cancel(jobId);
        jobMapper.deleteById(jobId);
        lockService.remove(jobId);
        log.info("删除定时任务「{}」({})", existing.getJobName(), jobId);
    }

    /**
     * 启停。
     *
     * <p>状态与 cron 分两个接口：启停是高频操作，不应该连带把整个表单提交一遍，
     * 否则并发编辑时容易用旧值覆盖别人的 cron 修改。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long jobId, String status) {
        if (!STATUS_NORMAL.equals(status) && !STATUS_PAUSED.equals(status)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "任务状态只能是 0（正常）或 1（暂停）");
        }
        SysJob existing = jobMapper.selectById(jobId);
        if (existing == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "定时任务不存在");
        }
        SysJob update = new SysJob();
        update.setJobId(jobId);
        update.setStatus(status);
        update.setVersion(existing.getVersion());
        if (jobMapper.updateById(update) == 0) {
            throw new ServiceException(ErrorCode.FAIL, "任务已被他人修改，请刷新后重试");
        }

        SysJob saved = jobMapper.selectById(jobId);
        jobScheduler.refresh(saved);
        log.info("{}定时任务「{}」({})", STATUS_NORMAL.equals(status) ? "启动" : "暂停",
                saved.getJobName(), jobId);
    }

    /** 立即执行一次：异步提交，接口不等任务跑完。 */
    public void runOnce(Long jobId) {
        SysJob existing = jobMapper.selectById(jobId);
        if (existing == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "定时任务不存在");
        }
        jobScheduler.runNow(jobId);
        log.info("手动触发定时任务「{}」({})", existing.getJobName(), jobId);
    }

    public List<JobHandlerVO> handlers() {
        return handlerRegistry.options();
    }

    private void normalize(SysJob job, boolean creating) {
        job.setJobName(trim(job.getJobName()));
        job.setInvokeTarget(trim(job.getInvokeTarget()));
        job.setCronExpression(trim(job.getCronExpression()));
        job.setJobGroup(trim(job.getJobGroup()));
        job.setRemark(trim(job.getRemark()));

        if (!StringUtils.hasText(job.getJobGroup())) {
            job.setJobGroup(DEFAULT_GROUP);
        }
        if (creating && !StringUtils.hasText(job.getStatus())) {
            job.setStatus(STATUS_PAUSED);
        }
        if (!StringUtils.hasText(job.getConcurrent())) {
            job.setConcurrent(DEFAULT_CONCURRENT);
        }
        if (!StringUtils.hasText(job.getMisfirePolicy())) {
            job.setMisfirePolicy(DEFAULT_MISFIRE);
        }
    }

    private void validate(SysJob job, Long excludeId) {
        if (!StringUtils.hasText(job.getCronExpression())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "Cron 表达式不能为空");
        }
        try {
            CronExpression.parse(job.getCronExpression());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "Cron 表达式不合法，请使用 6 段格式（秒 分 时 日 月 周）：" + e.getMessage());
        }

        if (handlerRegistry.get(job.getInvokeTarget()) == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "执行器不存在：" + job.getInvokeTarget() + "，请从下拉列表中选择");
        }

        if (!STATUS_NORMAL.equals(job.getStatus()) && !STATUS_PAUSED.equals(job.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "任务状态只能是 0（正常）或 1（暂停）");
        }
        if (!"0".equals(job.getConcurrent()) && !"1".equals(job.getConcurrent())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "并发策略只能是 0（允许）或 1（禁止）");
        }
        if (!"1".equals(job.getMisfirePolicy()) && !"2".equals(job.getMisfirePolicy())
                && !"3".equals(job.getMisfirePolicy())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "错失策略只能是 1 / 2 / 3");
        }

        Long sameName = jobMapper.selectCount(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getJobName, job.getJobName())
                .ne(excludeId != null, SysJob::getJobId, excludeId));
        if (sameName != null && sameName > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "任务名称已存在：" + job.getJobName());
        }
    }

    private void fillRuntimeInfo(SysJob job, LocalDateTime lastRunTime) {
        if (lastRunTime != null) {
            job.setLastRunTime(FORMATTER.format(lastRunTime));
        }
        if (STATUS_NORMAL.equals(job.getStatus())) {
            job.setNextRunTime(jobScheduler.nextExecutionTime(job.getCronExpression()));
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
