package com.lingxiao.modules.workflow.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 表单新增 / 编辑请求。
 */
@Data
public class FormRequest {

    @NotBlank(message = "{valid.workflow.formKey.required}")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{1,63}$", message = "{valid.workflow.formKey.pattern}")
    private String formKey;

    @NotBlank(message = "{valid.workflow.formName.required}")
    @Size(max = 64, message = "{valid.workflow.formName.max}")
    private String name;

    @NotNull(message = "{valid.workflow.formSchema.required}")
    @Valid
    private FormSchema schema;

    @Size(max = 255, message = "{valid.remark.max}")
    private String remark;
}