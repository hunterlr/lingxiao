package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 某语言某命名空间的覆盖率。
 */
@Data
public class CoverageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String languageCode;

    private String namespace;

    private long totalKeys;

    private long translated;

    /** 百分比，保留两位小数；无键时按 0 处理 */
    private double coverage;
}
