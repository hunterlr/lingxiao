package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 语言包加载的扁平行（Mapper 内部使用）。
 */
@Data
public class KeyValueRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private String keyCode;

    private String valueText;
}
