package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 批量保存译文（草稿）。
 *
 * <p>一次最多 500 条，前端按页提交；保存后仍是草稿，需要在语言包页面发布。</p>
 */
@Data
public class ValueBatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "{valid.i18n.key.code.required}")
    @Size(max = 500, message = "{valid.i18n.value.text.max}")
    @Valid
    private List<ValueItem> items;

    @Data
    public static class ValueItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotNull(message = "{valid.i18n.key.code.required}")
        private Long keyId;

        @NotBlank(message = "{valid.i18n.language.code.required}")
        private String languageCode;

        @Size(max = 2000, message = "{valid.i18n.value.text.max}")
        private String valueText;
    }
}
