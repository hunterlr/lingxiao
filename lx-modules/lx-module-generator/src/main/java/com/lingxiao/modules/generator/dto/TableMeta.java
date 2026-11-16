package com.lingxiao.modules.generator.dto;

import com.lingxiao.modules.generator.entity.GenTableColumn;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 从数据库读到的表结构快照。
 *
 * <p>只描述事实，不做命名推导：类名 / 模块名 / 业务名由
 * {@code GenTableService} 决定，便于以后调整规则时不碰元数据读取代码。</p>
 */
@Data
@AllArgsConstructor
public class TableMeta {

    private String tableName;

    private String tableComment;

    private List<GenTableColumn> columns;

    /** 主键列数；v1.0 只接受 1 */
    private int primaryKeyCount;
}
