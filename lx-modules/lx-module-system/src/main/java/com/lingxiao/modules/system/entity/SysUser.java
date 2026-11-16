package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台用户（sys_user）。
 *
 * <p>只放“能登录后台的人”。会员是另一张 {@code mem_user}，v1.0 不建。</p>
 *
 * <p><b>密码字段：</b>{@code @JsonProperty(access = WRITE_ONLY)} 保证只接收、不返回；
 * 数据库里存 BCrypt 哈希，禁止明文 / MD5 / SHA1。</p>
 *
 * <p><b>transient 字段：</b>{@code roleIds / postIds / deptName} 不落库，
 * 只用于“保存时一并授权”和“列表展示部门名”，通过 {@code @TableField(exist = false)} 排除。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long userId;

    /** 所属部门；数据范围「本部门 / 本部门及以下」依赖它 */
    private Long deptId;

    /** 登录账号，平台内唯一 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名不能超过 64 个字符")
    private String username;

    /** 显示名 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String nickname;

    /** BCrypt 哈希，长度固定 60；接口只接收、不返回 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(min = 6, max = 64, message = "密码长度必须在 6~64 之间")
    private String password;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @Size(max = 32, message = "手机号不能超过 32 个字符")
    private String phone;

    /** 0 男 / 1 女 / 2 未知，字典 sys_user_sex */
    private String sex;

    /** 头像 fileId 或 URL；v1.0 先存 fileId 字符串 */
    private String avatar;

    /** 0 正常 / 1 停用，字典 sys_common_status */
    private String status;

    private String loginIp;
    private LocalDateTime loginTime;

    /** 连续登录失败次数；登录成功、管理员解锁、重置密码时清零 */
    private Integer loginFailCount;

    /** 锁定截止时间；NULL 表示未锁定。到期后下一次登录从 0 重新计数 */
    private LocalDateTime lockTime;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    // ==================== 非数据库字段 ====================

    /** 保存时一并授权的角色；null 表示“不修改” */
    @TableField(exist = false)
    private List<Long> roleIds;

    /** 保存时一并授权的岗位；null 表示“不修改” */
    @TableField(exist = false)
    private List<Long> postIds;

    /** 列表展示用部门名，不落库 */
    @TableField(exist = false)
    private String deptName;
}
