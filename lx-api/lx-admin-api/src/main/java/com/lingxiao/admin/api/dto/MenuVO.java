package com.lingxiao.admin.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点。
 *
 * <p>只返回目录（M）和菜单（C），按钮（F）只用于权限判断，不进入侧边栏。</p>
 */
@Data
public class MenuVO {

    private Long id;
    private Long parentId;
    private String name;

    /** M 目录 / C 菜单 / F 按钮 */
    private String type;

    /** 前端路由地址 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 权限码，按钮级权限用 */
    private String perms;

    private String icon;
    private Integer orderNum;

    /** 0 显示 / 1 隐藏 */
    private String visible;

    private List<MenuVO> children = new ArrayList<>();
}
