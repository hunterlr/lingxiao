package com.lingxiao.modules.job.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 集群锁 Mapper。
 *
 * <p>抢锁用单条 {@code UPDATE ... WHERE 条件}：数据库保证同一行只有一个事务能改成功，
 * 不需要 {@code SELECT ... FOR UPDATE}，也不依赖任何数据库特有的锁函数。</p>
 *
 * <p>释放锁带 {@code lock_owner} 条件：任务执行超过超时时间被别人接管时，
 * 旧节点执行完不能把新持有者的锁释放掉。</p>
 */
@Mapper
public interface SysJobLockMapper {

    /** 补锁行；任务创建 / 历史数据初始化时调用。 */
    @Insert("INSERT INTO sys_job_lock (job_id, lock_owner, lock_time) VALUES (#{jobId}, NULL, NULL)")
    int insertRow(@Param("jobId") Long jobId);

    /**
     * 抢锁。
     *
     * @return 1 抢到；0 已被别人持有且未过期
     */
    @Update("UPDATE sys_job_lock SET lock_owner = #{owner}, lock_time = #{now} "
            + "WHERE job_id = #{jobId} AND (lock_owner IS NULL OR lock_time < #{expire})")
    int tryAcquire(@Param("jobId") Long jobId,
                   @Param("owner") String owner,
                   @Param("now") LocalDateTime now,
                   @Param("expire") LocalDateTime expire);

    /** 释放自己持有的锁。 */
    @Update("UPDATE sys_job_lock SET lock_owner = NULL, lock_time = NULL "
            + "WHERE job_id = #{jobId} AND lock_owner = #{owner}")
    int release(@Param("jobId") Long jobId, @Param("owner") String owner);

    /** 删除任务时清理锁行。 */
    @Delete("DELETE FROM sys_job_lock WHERE job_id = #{jobId}")
    int deleteByJobId(@Param("jobId") Long jobId);
}
