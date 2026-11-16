package com.lingxiao.modules.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lingxiao.modules.generator.support.TextUtils;
import lombok.Data;

import java.io.Serializable;

/**
 * 代码生成-字段配置（gen_table_column）。
 *
 * <p>没有审计字段：字段配置永远跟随表配置一起创建 / 一起删除，
 * 单独记录“谁改了某个字段的查询方式”价值不大，反而让更新接口多一堆赋值。</p>
 *
 * <p>{@code columnName / isPk / isIncrement} 是数据库事实，导入后不允许修改；
 * 可改的是展示与生成策略（注释、Java 字段名、表单控件、列表 / 查询开关等），
 * 具体白名单见 {@code GenTableService#updateColumns}。</p>
 */
@Data
@TableName("gen_table_column")
public class GenTableColumn implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "column_id", type = IdType.ASSIGN_ID)
    private Long columnId;

    private Long tableId;

    private String columnName;

    private String columnComment;

    /** 数据库类型，来自 JDBC TYPE_NAME，例如 bigint / varchar */
    private String columnType;

    /** Java 类型，例如 Long / String / LocalDateTime */
    private String javaType;

    /** Java 字段名，例如 userId / createTime */
    private String javaField;

    private String isPk;

    private String isIncrement;

    private String isRequired;

    private String isInsert;

    private String isEdit;

    private String isList;

    private String isQuery;

    private String queryType;

    private String htmlType;

    private String dictType;

    private Integer sort;

    /**
     * 首字母大写的字段名，供模板拼 getter / lambda 引用。
     *
     * <p>不是数据库列，{@code @JsonIgnore} 防止它出现在接口响应里。</p>
     */
    @JsonIgnore
    public String getJavaFieldCap() {
        if (javaField == null || javaField.isEmpty()) {
            return javaField;
        }
        return Character.toUpperCase(javaField.charAt(0)) + javaField.substring(1);
    }

    /** 可直接放进 JS 单引号字符串的标签；模板里只用于校验提示。 */
    @JsonIgnore
    public String getJsLabel() {
        return TextUtils.jsLabel(columnComment, javaField);
    }

    /** 放进 Vue 文本 / 属性时使用，做 HTML 转义。 */
    @JsonIgnore
    public String getHtmlLabel() {
        return TextUtils.html(TextUtils.cleanComment(columnComment, javaField));
    }

    /**
     * Controller 查询参数上的 {@code @DateTimeFormat} 前缀。
     *
     * <p>时间类型不能按 ISO 默认格式绑定时，前端传的是 {@code yyyy-MM-dd HH:mm:ss}，
     * 必须在参数上声明 pattern；非时间类型返回空串，模板统一写成
     * {@code ${col.paramAnnotation}@RequestParam(...)}。</p>
     */
    @JsonIgnore
    public String getParamAnnotation() {
        if ("LocalDateTime".equals(javaType)) {
            return "@DateTimeFormat(pattern = \"yyyy-MM-dd HH:mm:ss\") ";
        }
        if ("LocalDate".equals(javaType)) {
            return "@DateTimeFormat(pattern = \"yyyy-MM-dd\") ";
        }
        if ("LocalTime".equals(javaType)) {
            return "@DateTimeFormat(pattern = \"HH:mm:ss\") ";
        }
        return "";
    }
}
