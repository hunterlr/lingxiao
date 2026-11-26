package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 翻译矩阵行：一个键 + 多语言译文。
 *
 * <p>{@code values} 以 {@code languageCode -> valueText} 返回，
 * 前端按语言列渲染；某语言没有译文时不出现该 key，方便显示“未翻译”。</p>
 */
@Data
public class TranslationRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long keyId;

    private String namespace;

    private String keyCode;

    private String defaultValue;

    private String moduleCode;

    private String status;

    private String remark;

    private Map<String, String> values = new LinkedHashMap<>();
}
