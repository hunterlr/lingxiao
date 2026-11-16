package com.lingxiao.framework.module;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 模块信息（只读视图）。
 */
@Data
@AllArgsConstructor
public class ModuleInfo {
    private String code;
    private String name;
    private boolean enabled;
    private String description;
}
