package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 角色（sys_role）。
 *
 * <p>平台域角色与租户域 / 会员域角色是三套独立表，不共用本表。</p>
 *
 * <p>{@code dataScope} 决定数据可见范围（1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人）。
 * v1.0 由 {@code DataScopeService} 在用户 / 部门查询与写操作上落地；2 自定义的部门集合
 * 存在 {@code sys_role_dept}，随保存接口的 {@code deptIds} 一起提交。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    @TableId(value = "role_id", type = IdType.ASSIGN_ID)
    private Long roleId;

    /** 角色标识，例如 admin / operator；权限判断用它 */
    @NotBlank(message = "{valid.role.key.required}")
    @Size(max = 64, message = "{valid.role.key.max}")
    private String roleKey;

    @NotBlank(message = "{valid.role.name.required}")
    @Size(max = 64, message = "{valid.role.name.max}")
    private String roleName;

    /** 排序，越小越靠前 */
    private Integer sort;

    /**
     * 数据范围：
     * 1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人
     */
    private String dataScope;

    /** 0 正常 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;

    // ==================== 非数据库字段 ====================

    /** 保存时一并授权的菜单；null 表示“不修改” */
    @TableField(exist = false)
    private List<Long> menuIds;

    /** 自定义数据范围勾选的部门；null 表示“不修改”，仅 dataScope=2 时生效 */
    @TableField(exist = false)
    private List<Long> deptIds;
}
