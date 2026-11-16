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

    @NotBlank(message = "{valid.file.name.required}")
    @Size(max = 255, message = "{valid.file.name.max}")
    private String displayName;
}
