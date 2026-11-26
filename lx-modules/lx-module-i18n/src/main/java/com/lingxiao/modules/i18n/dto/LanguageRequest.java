package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 新增 / 编辑语言。
 *
 * <p>{@code languageCode} 是 BCP 47 标签（zh-CN / en-US），保存后作为
 * {@code X-Lang} 与译文表的语言标识；{@code locale} 是 Java Locale 形式
 * （zh_CN / en_US），用于资源包兜底匹配。</p>
 */
@Data
public class LanguageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.i18n.language.code.required}")
    @Size(max = 16, message = "{valid.i18n.language.code.max}")
    @Pattern(regexp = "^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$", message = "{i18n.language.code.invalid}")
    private String languageCode;

    @NotBlank(message = "{valid.i18n.language.name.required}")
    @Size(max = 64, message = "{valid.i18n.language.name.max}")
    private String languageName;

    @Size(max = 64, message = "{valid.i18n.language.name.max}")
    private String nativeName;

    @Size(max = 32, message = "{valid.i18n.language.code.max}")
    private String locale;

    /** ltr / rtl，默认 ltr */
    @Pattern(regexp = "^(ltr|rtl)$", message = "{i18n.language.code.invalid}")
    private String direction;

    /** 1=默认语言；默认语言由服务端保证唯一 */
    private Integer isDefault;

    /** 0 启用 / 1 停用 */
    private String status;

    private Integer sort;
}
