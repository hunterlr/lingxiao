package com.lingxiao.modules.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用树节点（部门树 / 菜单树 / 下拉树）。
 *
 * <p>只保留前端渲染树需要的字段，不返回整个实体：
 * 部门树不需要 leader / phone，菜单树不需要 component，减少响应体积。</p>
 *
 * <p>{@code children} 初始化为空列表，前端可以少一层判空。</p>
 */
@Data
public class TreeSelect {

    /** 节点 ID（部门 ID 或菜单 ID） */
    private Long id;

    /** 展示文本（部门名或菜单名） */
    private String label;

    /** 父节点 ID，根为 0 */
    private Long parentId;

    /** 菜单类型 M/C/F；部门树固定为 D */
    private String type;

    /** 权限码（仅菜单节点有值，供前端展示） */
    private String perms;

    /** 排序值，前端可再排 */
    private Integer orderNum;

    private List<TreeSelect> children = new ArrayList<>();

    public TreeSelect() {
    }

    public TreeSelect(Long id, String label, Long parentId, String type) {
        this.id = id;
        this.label = label;
        this.parentId = parentId;
        this.type = type;
    }
}
