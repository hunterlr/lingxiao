package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我发起的列表行。
 *
 * <p>{@code status} 取值固定为 running / completed / rejected / canceled：
 * rejected 与 canceled 都来自删除实例的原因前缀（reject: / withdraw:），
 * 引擎本身不区分这两种“非正常结束”。</p>
 */
@Data
public class InstanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String processInstanceId;

    private String processKey;

    private String processName;

    private String title;

    private String status;

    private String currentTaskName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String remark;
}