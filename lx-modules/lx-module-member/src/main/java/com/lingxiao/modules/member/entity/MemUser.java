package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 前台会员（mem_user）。
 *
 * <p>与 {@code sys_user} 完全物理隔离：不共表、不共 Token、不共权限缓存。
 * 平台后台用户是“员工 / 运营”，会员是“站点注册用户”，两边账号不能互相登录。</p>
 *
 * <p><b>tenant_id：</b>当前是单租户部署，统一写 0；预留给后续 SaaS 化。
 * 唯一约束都带 tenant_id，避免多租户上线后改表。</p>
 *
 * <p><b>密码：</b>数据库存 BCrypt 哈希；{@code @JsonProperty(WRITE_ONLY)} 保证
 * 只接收、不返回，列表和详情接口不会把哈希带出去。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mem_user")
public class MemUser extends BaseEntity {

    @TableId(value = "member_id", type = IdType.ASSIGN_ID)
    private Long memberId;

    /** 租户 ID；单租户部署固定 0 */
    private Long tenantId;

    /** 登录账号，租户内唯一 */
    private String username;

    /** 显示名 */
    private String nickname;

    /** BCrypt 哈希，接口只接收、不返回 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 手机号，租户内应用层唯一；是否必填由参数 mem.register.mobileRequired 控制 */
    private String mobile;

    /** 邮箱，租户内应用层唯一；是否必填由参数 mem.register.emailRequired 控制 */
    private String email;

    /** 头像 fileId 或 http(s) 外链；不建 mem_file */
    private String avatar;

    /** 0 男 / 1 女 / 2 未知，与 sys_user_sex 字典对齐 */
    private String sex;

    /** 会员等级；注册时取默认等级 */
    private Long levelId;

    /** 积分，可消耗 */
    private Integer points;

    /** 成长值，只增不减（后续配置衰减时再调整） */
    private Integer growth;

    /** 0 正常 / 1 停用 */
    private String status;

    /** 注册渠道：web / h5 / app，当前默认 web */
    private String registerChannel;

    private String registerIp;

    private String loginIp;

    private LocalDateTime loginTime;

    /** 连续登录失败次数；成功登录 / 管理员重置密码时清零 */
    private Integer loginFailCount;

    /** 锁定截止时间；NULL 表示未锁定 */
    private LocalDateTime lockTime;

    /** 最后一次活跃时间；当前在登录与资料更新时刷新 */
    private LocalDateTime lastActiveTime;

    private String remark;

    // ==================== 非数据库字段 ====================

    /** 列表展示用等级名，不落库 */
    @TableField(exist = false)
    private String levelName;
}
