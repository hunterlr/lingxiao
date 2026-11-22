package com.lingxiao.modules.system.dto;

import lombok.Data;

/**
 * 权限码 + 所属模块。
 *
 * <p>权限码来自 {@code sys_menu.perms}，但模块关闭后这些权限必须一并失效。
 * 只查权限码字符串拿不到模块归属，所以用这个最小投影多带一列
 * {@code module_code}，由调用方按模块开关过滤。</p>
 */
@Data
public class MenuPermRow {

    /** 权限码，例如 platform:monitor:job:list */
    private String perms;

    /** 所属模块，例如 system / file / job / generator / cms */
    private String moduleCode;
}
