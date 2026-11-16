package com.lingxiao.modules.job.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 可选执行器。
 *
 * <p>任务表单不能让人手填执行器名称：拼错不会在保存时报错，
 * 只会在真正执行时失败。这里把容器里的 {@code JobHandler} 全部列出来给下拉用。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobHandlerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 写进 sys_job.invoke_target */
    private String name;

    /** 中文说明 */
    private String description;
}
