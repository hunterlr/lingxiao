package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 导入结果。
 *
 * <p>导入只写草稿，不直接发布；{@code errors} 最多保留前 50 条，
 * 避免一个坏文件把响应撑爆。</p>
 */
@Data
public class ImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int total;

    private int success;

    private int skipped;

    private int failed;

    private List<String> errors = new ArrayList<>();
}
