package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 语言包版本展示。
 */
@Data
public class PackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long packId;

    private String languageCode;

    private String namespace;

    private Integer version;

    private String checksum;

    /** 0 历史 / 1 当前 / 2 已废弃 */
    private String status;

    private Long publishedBy;

    private LocalDateTime publishedAt;
}
