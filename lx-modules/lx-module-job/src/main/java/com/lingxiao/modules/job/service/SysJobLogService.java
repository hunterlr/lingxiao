package com.lingxiao.modules.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.job.entity.SysJobLog;
import com.lingxiao.modules.job.mapper.SysJobLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 任务日志查询与清理。
 *
 * <p>日志只读：不提供编辑接口。清理分两种——按保留天数（定时任务调用）
 * 与页面“清空日志”（人工、单独权限码）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class SysJobLogService {

    private final SysJobLogMapper jobLogMapper;

    public PageResult<SysJobLog> page(long pageNum, long pageSize, Long jobId, String jobName,
                                      String status, LocalDateTime beginTime, LocalDateTime endTime) {
        Page<SysJobLog> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
                .eq(jobId != null, SysJobLog::getJobId, jobId)
                .like(StringUtils.hasText(jobName), SysJobLog::getJobName, jobName)
                .eq(StringUtils.hasText(status), SysJobLog::getStatus, status)
                .ge(beginTime != null, SysJobLog::getCreateTime, beginTime)
                .le(endTime != null, SysJobLog::getCreateTime, endTime)
                .orderByDesc(SysJobLog::getCreateTime);
        Page<SysJobLog> result = jobLogMapper.selectPage(page, wrapper);

        PageResult<SysJobLog> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    /**
     * 清空全部任务日志。
     *
     * @return 删除条数
     */
    public int clean() {
        int count = jobLogMapper.cleanAll();
        log.warn("清空定时任务日志: {} 条, 操作人={}", count, LoginHelper.getUsername());
        return count;
    }
}
