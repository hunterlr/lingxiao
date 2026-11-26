package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 门户语言包响应。
 *
 * <p>整包下发，前端一次缓存；{@code version} / {@code checksum} 用于
 * {@code ETag} 与增量判断。</p>
 */
@Data
public class PortalPackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String languageCode;

    private String namespace;

    private Integer version;

    private String checksum;

    private Map<String, String> messages = new LinkedHashMap<>();
}
