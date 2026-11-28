package com.lingxiao.modules.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 动态表单 Schema：字段列表就是全部结构。
 *
 * <p>第二刀不做联动规则与字段权限矩阵：那要表达式求值器，收益在第三刀才出现。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormSchema implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<FormField> fields = new ArrayList<>();
}