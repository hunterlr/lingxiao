package com.lingxiao.modules.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.job.dto.JobLastRunVO;
import com.lingxiao.modules.job.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务 Mapper。
 *
 * <p>自定义 SQL 必须自己带 {@code del_flag = '0'}：MyBatis-Plus 的逻辑删除
 * 只对 {@code selectById / selectList} 等内置方法生效，手写 SQL 不经过它。</p>
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {

    /** 启动时加载“参与调度”的任务：未删除 + 状态正常。 */
    @Select("SELECT * FROM sys_job WHERE del_flag = '0' AND status = '0' ORDER BY job_id")
    List<SysJob> selectEnabled();

    /** 每个任务的上次执行时间；一次聚合，避免列表 N+1。 */
    @Select("SELECT job_id AS jobId, MAX(create_time) AS lastRunTime FROM sys_job_log GROUP BY job_id")
    List<JobLastRunVO> selectLastRunTimes();
}
