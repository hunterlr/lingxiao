package com.lingxiao.modules.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 代码生成-表配置（gen_table）。
 *
 * <p><b>为什么不继承 BaseEntity：</b>生成器配置是工具数据，删除即物理删除。
 * 如果带 {@code del_flag}，同一张表被删后再导入会撞唯一索引 {@code table_name}，
 * 还得写“恢复或覆盖”的分支，纯属给自己找麻烦。</p>
 *
 * <p>字段含义见 {@code V12__generator_schema.sql}；{@code columns} 不落库，
 * 详情接口一次性带回来，避免前端再发一次请求。</p>
 */
@Data
@TableName("gen_table")
public class GenTable implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "table_id", type = IdType.ASSIGN_ID)
    private Long tableId;

    private String tableName;

    private String tableComment;

    /** 实体类名，例如 SysUser */
    private String className;

    /** 模块名，决定包名 com.lingxiao.modules.{moduleName} 与接口前缀 */
    private String moduleName;

    /** 业务名，camelCase，用于变量名与权限码 */
    private String businessName;

    /** 功能名，用于菜单标题 */
    private String functionName;

    private String functionAuthor;

    private String genType;

    /** 前端路由，例如 /demo/order */
    private String genPath;

    private String templateGroup;

    private String status;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    /** 字段配置；仅详情接口填充 */
    @TableField(exist = false)
    private List<GenTableColumn> columns;
}
