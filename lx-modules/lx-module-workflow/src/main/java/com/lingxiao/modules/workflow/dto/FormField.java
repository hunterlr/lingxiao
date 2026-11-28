package com.lingxiao.modules.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 动态表单字段。
 *
 * <p>字段 {@code key} 直接作为流程变量名：条件分支的表达式、会签的
 * {@code flowable:collection} 都按它取值，所以发布后不要随意改名。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormField {

    /** 变量名，如 amount / approvers */
    private String key;

    private String label;

    /** input / textarea / number / select / radio / date / datetime / switch / user */
    private String type;

    private Boolean required;

    private Integer maxLength;

    private BigDecimal min;

    private BigDecimal max;

    /** number 的小数位，仅用于展示与前端步进 */
    private Integer precision;

    /** 正则，input / textarea 生效 */
    private String pattern;

    /** user 字段是否多选；多选写 List&lt;String&gt;，单选写 String */
    private Boolean multiple;

    private List<FormOption> options;
}