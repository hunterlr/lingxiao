package com.lingxiao.modules.generator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 可导入的数据库表。
 *
 * <p>{@code imported} 是给前端置灰用的：已经导入过的表仍显示，
 * 但复选框不可选，避免用户点了“导入”才发现重复。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DbTableVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tableName;

    private String tableComment;

    private boolean imported;
}
