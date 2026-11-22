package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员第三方账号绑定（mem_user_social）。
 *
 * <p>与后台的 {@code sys_user_social} 分开：会员域与平台域不共表、不共 Token。
 * 只存 member_id，不建跨模块外键；同一服务商的 open_id 只能绑定一个会员。</p>
 */
@Data
@TableName("mem_user_social")
public class MemUserSocial {

    @TableId(value = "social_id", type = IdType.ASSIGN_ID)
    private Long socialId;

    private Long tenantId;

    private Long memberId;

    /** 服务商编码，与 mem_oauth_provider.provider 一致 */
    private String provider;

    private String openId;

    /** 微信 / QQ 等平台的跨应用统一标识 */
    private String unionId;

    /** 首次授权时的昵称快照，仅用于兜底展示 */
    private String nickname;

    /** 首次授权时的头像快照 */
    private String avatar;

    /** 最近一次用户信息 JSON，仅排障用，接口不下发 */
    private String rawData;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
