package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 覆盖率聚合行（Mapper 内部使用）。
 */
@Data
public class CoverageRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private String namespace;

    private Long totalKeys;

    private Long translated;
}
