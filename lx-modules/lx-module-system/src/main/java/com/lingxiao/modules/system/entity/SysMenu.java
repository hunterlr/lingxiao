package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 菜单 / 权限（sys_menu）。
 *
 * <p>三种类型：</p>
 * <ul>
 *   <li>M 目录：侧边栏分组，不对应页面；</li>
 *   <li>C 菜单：对应一个前端页面；</li>
 *   <li>F 按钮：只提供 perms 权限码，不显示在侧边栏。</li>
 * </ul>
 *
 * <p>{@code menuKey} 是幂等导入用的稳定标识，不依赖自增 ID；
 * {@code moduleCode} 标记菜单属于哪个模块，模块关闭时不下发。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    @TableId(value = "menu_id", type = IdType.ASSIGN_ID)
    private Long menuId;

    /** 稳定键，例如 system:user:list；菜单 SQL 可重复执行 */
    @NotBlank(message = "菜单标识不能为空")
    @Size(max = 128, message = "菜单标识不能超过 128 个字符")
    private String menuKey;

    /** 上级菜单，0 表示根 */
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称不能超过 64 个字符")
    private String menuName;

    /** M 目录 / C 菜单 / F 按钮 */
    @NotBlank(message = "菜单类型不能为空")
    @Pattern(regexp = "[MCF]", message = "菜单类型只能是 M / C / F")
    private String menuType;

    /** 前端路由，例如 /system/user */
    @Size(max = 255, message = "路由地址不能超过 255 个字符")
    private String path;

    /** 前端组件，例如 system/user/index */
    @Size(max = 255, message = "组件路径不能超过 255 个字符")
    private String component;

    /** 权限码，例如 platform:system:user:list */
    @Size(max = 128, message = "权限码不能超过 128 个字符")
    private String perms;

    private String icon;
    private Integer orderNum;

    /** 0 显示 / 1 隐藏 */
    private String visible;

    /** 0 正常 / 1 停用 */
    private String status;

    /** 所属模块，system / file / cms ... */
    @Size(max = 32, message = "模块编码不能超过 32 个字符")
    private String moduleCode;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
