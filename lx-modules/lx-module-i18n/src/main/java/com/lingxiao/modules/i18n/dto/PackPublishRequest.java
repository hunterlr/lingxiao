package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 发布语言包。
 *
 * <p>{@code namespace} 为空时发布该语言下所有有译文的命名空间；
 * 每个命名空间各生成一个版本，互不影响回滚粒度。</p>
 */
@Data
public class PackPublishRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.i18n.language.code.required}")
    private String languageCode;

    private String namespace;
}
