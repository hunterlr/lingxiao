package com.lingxiao.modules.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 定时任务（sys_job）。
 *
 * <p>字段沿用 RuoYi 风格，便于从旧系统迁移；执行语义由本模块解释：</p>
 * <ul>
 *   <li>{@code invokeTarget}：执行器名称，对应 {@code JobHandler.name()}，
 *       <b>不是</b> “类名.方法名”，避免反射调用任意 Bean；</li>
 *   <li>{@code cronExpression}：Spring 6 段表达式，秒 分 时 日 月 周；</li>
 *   <li>{@code status}：0 正常（参与调度）/ 1 暂停；</li>
 *   <li>{@code concurrent}：0 允许并发 / 1 禁止（同一节点已有实例时跳过）；</li>
 *   <li>{@code misfirePolicy}：1 立即补偿 / 2 执行一次 / 3 放弃。
 *       v1.0 的 Spring Task 没有持久化触发器，停机期间的错失统一按“放弃”处理，
 *       字段保留是为了兼容旧数据与后续升级，不在页面夸大它的作用。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class SysJob extends BaseEntity {

    @TableId(value = "job_id", type = IdType.ASSIGN_ID)
    private Long jobId;

    @NotBlank(message = "{valid.job.name.required}")
    @Size(max = 64, message = "{valid.job.name.max}")
    private String jobName;

    /** 任务分组，v1.0 固定 DEFAULT，保留字段便于后续按业务线分组 */
    @Size(max = 64, message = "{valid.job.group.max}")
    private String jobGroup;

    @NotBlank(message = "{valid.job.invokeTarget.required}")
    @Size(max = 500, message = "{valid.job.invokeTarget.max}")
    private String invokeTarget;

    @NotBlank(message = "{valid.job.cron.required}")
    @Size(max = 255, message = "{valid.job.cron.max}")
    private String cronExpression;

    /** 1 立即 / 2 执行一次 / 3 放弃 */
    private String misfirePolicy;

    /** 0 允许 / 1 禁止 */
    private String concurrent;

    /** 0 正常 / 1 暂停 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;

    /**
     * 下次执行时间，不落库。
     *
     * <p>由 Service 用 {@code CronExpression.next(now)} 计算后填充：
     * Spring Task 的 {@code ScheduledFuture} 不暴露下次触发时间，
     * 与其反射内部字段，不如按同一份 cron 再算一次。</p>
     */
    @TableField(exist = false)
    private String nextRunTime;

    /** 上次执行时间，不落库；由 sys_job_log 聚合得到 */
    @TableField(exist = false)
    private String lastRunTime;
}
