package com.lingxiao.modules.file.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 文件改名请求。
 *
 * <p>只改 display_name；original_name / file_name / path 永远不变。</p>
 */
@Data
public class RenameRequest {

    @NotBlank(message = "新名称不能为空")
    @Size(max = 255, message = "名称长度不能超过 255")
    private String displayName;
}
