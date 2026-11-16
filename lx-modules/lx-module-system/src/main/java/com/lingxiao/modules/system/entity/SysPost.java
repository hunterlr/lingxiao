package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 岗位（sys_post）。
 *
 * <p>岗位是“职务标签”，与部门正交：一个用户可以同时有多个岗位，
 * 通过 {@code sys_user_post} 关联。权限不挂在岗位上（挂在角色上）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class SysPost extends BaseEntity {

    @TableId(value = "post_id", type = IdType.ASSIGN_ID)
    private Long postId;

    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 64, message = "岗位编码不能超过 64 个字符")
    private String postCode;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 64, message = "岗位名称不能超过 64 个字符")
    private String postName;

    private Integer sort;

    /** 0 正常 / 1 停用 */
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
