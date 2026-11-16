package com.lingxiao.modules.job.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务上次执行时间聚合结果。
 *
 * <p>用一次 {@code GROUP BY job_id} 查完，避免任务列表每一行都查一次日志表。</p>
 */
@Data
public class JobLastRunVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long jobId;

    private LocalDateTime lastRunTime;
}
