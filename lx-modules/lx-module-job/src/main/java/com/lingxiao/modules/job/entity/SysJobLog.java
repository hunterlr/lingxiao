package com.lingxiao.modules.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务执行日志（sys_job_log）。
 *
 * <p>不继承 {@code BaseEntity}：日志只追加，没有“修改人 / 逻辑删除 / 版本号”，
 * 硬套公共字段会让表结构和语义都对不上。</p>
 *
 * <p>{@code jobName / jobGroup / invokeTarget} 是执行时的<b>快照</b>：
 * 任务后来改名、改执行器甚至被删除，历史日志仍要能读懂当时跑了什么。</p>
 */
@Data
@TableName("sys_job_log")
public class SysJobLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "job_log_id", type = IdType.ASSIGN_ID)
    private Long jobLogId;

    /** 所属任务；任务删除后仍保留，便于追溯 */
    private Long jobId;

    private String jobName;

    private String jobGroup;

    private String invokeTarget;

    /** CRON 调度 / MANUAL 手动执行 */
    private String triggerType;

    /** 成功时的结果摘要，失败时的错误摘要 */
    private String jobMessage;

    /** 0 成功 / 1 失败 */
    private String status;

    private String exceptionInfo;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private LocalDateTime createTime;
}
