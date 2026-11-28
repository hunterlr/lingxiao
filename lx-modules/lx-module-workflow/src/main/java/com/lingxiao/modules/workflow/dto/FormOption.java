package com.lingxiao.modules.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 选项（select / radio）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormOption implements Serializable {

    private static final long serialVersionUID = 1L;

    private String label;

    private String value;
}