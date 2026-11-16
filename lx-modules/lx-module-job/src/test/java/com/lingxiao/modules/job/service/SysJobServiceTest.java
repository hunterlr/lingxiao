package com.lingxiao.modules.job.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.core.job.JobHandler;
import com.lingxiao.modules.job.entity.SysJob;
import com.lingxiao.modules.job.mapper.SysJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 定时任务保存校验测试。
 *
 * <p>任务写错不会立刻报错，而是“凌晨静默失败”，所以保存前必须拦住：
 * cron 必须 6 段、执行器必须存在、状态 / 并发 / 错失策略枚举合法、名称不能重复。
 * 这里用 Mockito 隔离 Mapper 与调度器，不启动 Spring、不连数据库。</p>
 */
@DisplayName("SysJobService 任务校验")
class SysJobServiceTest {

    private SysJobMapper jobMapper;
    private JobLockService lockService;
    private JobHandlerRegistry handlerRegistry;
    private JobScheduler jobScheduler;
    private SysJobService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(SysJobMapper.class);
        lockService = mock(JobLockService.class);
        handlerRegistry = mock(JobHandlerRegistry.class);
        jobScheduler = mock(JobScheduler.class);
        service = new SysJobService(jobMapper, lockService, handlerRegistry, jobScheduler);
    }

    @Test
    @DisplayName("合法任务：新增成功、默认暂停、补齐分组/并发/错失策略")
    void createValidJob() {
        when(handlerRegistry.get("demoHandler")).thenReturn(mock(JobHandler.class));
        when(jobMapper.selectCount(any())).thenReturn(0L);
        when(jobMapper.insert(any(SysJob.class))).thenAnswer(invocation -> {
            SysJob saved = invocation.getArgument(0);
            saved.setJobId(123L);
            return 1;
        });

        SysJob job = job("  测试任务  ", "demoHandler", "0 0/5 * * * ?");
        Long id = service.create(job);

        assertThat(id).isEqualTo(123L);
        assertThat(job.getJobName()).isEqualTo("测试任务");
        assertThat(job.getStatus()).isEqualTo(SysJobService.STATUS_PAUSED);
        assertThat(job.getJobGroup()).isEqualTo("DEFAULT");
        assertThat(job.getConcurrent()).isEqualTo("1");
        assertThat(job.getMisfirePolicy()).isEqualTo("3");
        verify(lockService).ensureLockRow(123L);
        verify(jobScheduler).refresh(job);
    }

    @Test
    @DisplayName("空 cron：拒绝，提示不能为空")
    void rejectsBlankCron() {
        ServiceException e = catchThrowableOfType(
                () -> service.create(job("任务", "demoHandler", "   ")), ServiceException.class);

        assertThat(e.getCode()).isEqualTo(1001);
        assertThat(e.getMessage()).contains("Cron 表达式不能为空");
        assertNothingPersisted();
    }

    @Test
    @DisplayName("5 段 UNIX cron：拒绝，避免保存成功但永不触发")
    void rejectsFiveFieldCron() {
        ServiceException e = catchThrowableOfType(
                () -> service.create(job("任务", "demoHandler", "0 0/5 * * *")), ServiceException.class);

        assertThat(e.getCode()).isEqualTo(1001);
        assertThat(e.getMessage()).contains("Cron 表达式不合法").contains("6 段");
        assertNothingPersisted();
    }

    @Test
    @DisplayName("执行器不存在：拒绝，不把注定失败的任务留到运行时")
    void rejectsUnknownHandler() {
        when(handlerRegistry.get("missingHandler")).thenReturn(null);

        ServiceException e = catchThrowableOfType(
                () -> service.create(job("任务", "missingHandler", "0 0/5 * * * ?")),
                ServiceException.class);

        assertThat(e.getCode()).isEqualTo(1001);
        assertThat(e.getMessage()).contains("执行器不存在");
        assertNothingPersisted();
    }

    @Test
    @DisplayName("任务名重复：拒绝，否则日志无法区分")
    void rejectsDuplicateName() {
        when(handlerRegistry.get("demoHandler")).thenReturn(mock(JobHandler.class));
        when(jobMapper.selectCount(any())).thenReturn(1L);

        ServiceException e = catchThrowableOfType(
                () -> service.create(job("测试任务", "demoHandler", "0 0/5 * * * ?")),
                ServiceException.class);

        assertThat(e.getCode()).isEqualTo(1001);
        assertThat(e.getMessage()).contains("任务名称已存在");
        verify(jobMapper, never()).insert(any(SysJob.class));
        verifyNoInteractions(lockService, jobScheduler);
    }

    @Test
    @DisplayName("状态枚举非法：拒绝")
    void rejectsInvalidStatus() {
        when(handlerRegistry.get("demoHandler")).thenReturn(mock(JobHandler.class));
        when(jobMapper.selectCount(any())).thenReturn(0L);

        SysJob job = job("任务", "demoHandler", "0 0/5 * * * ?");
        job.setStatus("9");

        ServiceException e = catchThrowableOfType(() -> service.create(job), ServiceException.class);

        assertThat(e.getCode()).isEqualTo(1001);
        assertThat(e.getMessage()).contains("任务状态只能是");
    }

    @Test
    @DisplayName("并发策略非法：拒绝")
    void rejectsInvalidConcurrent() {
        when(handlerRegistry.get("demoHandler")).thenReturn(mock(JobHandler.class));
        when(jobMapper.selectCount(any())).thenReturn(0L);

        SysJob job = job("任务", "demoHandler", "0 0/5 * * * ?");
        job.setConcurrent("9");

        ServiceException e = catchThrowableOfType(() -> service.create(job), ServiceException.class);

        assertThat(e.getCode()).isEqualTo(1001);
        assertThat(e.getMessage()).contains("并发策略只能是");
    }

    private void assertNothingPersisted() {
        verify(jobMapper, never()).insert(any(SysJob.class));
        verifyNoInteractions(lockService, jobScheduler);
    }

    private static SysJob job(String name, String target, String cron) {
        SysJob job = new SysJob();
        job.setJobName(name);
        job.setInvokeTarget(target);
        job.setCronExpression(cron);
        return job;
    }
}
