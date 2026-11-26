package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 新增 / 编辑翻译键。
 *
 * <p>{@code keyCode} 是完整键（如 {@code error.2001}），{@code namespace}
 * 取第一段；两者不一致时以显式 namespace 为准，便于把 {@code cms.*} 归到 cms 模块。</p>
 */
@Data
public class KeyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.i18n.key.namespace.required}")
    @Size(max = 64, message = "{valid.i18n.key.code.max}")
    private String namespace;

    @NotBlank(message = "{valid.i18n.key.code.required}")
    @Size(max = 255, message = "{valid.i18n.key.code.max}")
    private String keyCode;

    @Size(max = 2000, message = "{valid.i18n.key.default.max}")
    private String defaultValue;

    @Size(max = 64, message = "{valid.i18n.key.code.max}")
    private String moduleCode;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.i18n.key.default.max}")
    private String remark;
}
