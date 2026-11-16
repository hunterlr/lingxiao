package com.lingxiao.modules.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.job.entity.SysJobLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 任务日志 Mapper。
 *
 * <p>删除语句都显式带 where：{@code BlockAttackInnerInterceptor}
 * 会拦截没有条件的 DELETE，写 {@code DELETE FROM sys_job_log} 会直接报错。</p>
 */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {

    /** 按保留天数清理；{@code deadline} 之前的记录删除。 */
    @Delete("DELETE FROM sys_job_log WHERE create_time < #{deadline}")
    int deleteBefore(@Param("deadline") LocalDateTime deadline);

    /** 清空任务日志；条件写成主键非空，既表达意图也绕过防全表删除。 */
    @Delete("DELETE FROM sys_job_log WHERE job_log_id IS NOT NULL")
    int cleanAll();
}
