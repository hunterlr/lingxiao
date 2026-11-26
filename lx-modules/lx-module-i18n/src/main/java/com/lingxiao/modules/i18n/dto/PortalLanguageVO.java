package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 门户可用语言。
 *
 * <p>只暴露渲染需要的字段，不带后台的创建时间 / 备注。</p>
 */
@Data
public class PortalLanguageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String languageCode;

    private String languageName;

    private String nativeName;

    private String direction;

    private boolean isDefault;

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
