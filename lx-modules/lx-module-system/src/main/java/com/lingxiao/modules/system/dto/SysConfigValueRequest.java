package com.lingxiao.modules.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 批量改值中的单个参数。
 *
 * <p>只带 id 与新值：键名、控件类型、选项都不允许通过批量接口修改，
 * 避免设置页绕过内置参数约束。</p>
 */
@Data
public class SysConfigValueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "{valid.config.id.required}")
    private Long configId;

    @NotBlank(message = "{valid.config.value.required}")
    @Size(max = 500, message = "{valid.config.value.max}")
    private String configValue;
}
