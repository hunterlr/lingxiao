package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员找回密码令牌（mem_password_reset）。
 *
 * <p>只存验证码的 BCrypt 哈希，不存明文；验证码通过短信 / 邮件通道下发，
 * v1.2 第一刀没有接真实通道，开发环境由 {@code MemberResetCodeSender} 写日志。
 * 记录一次性使用，过期或使用后不再接受。</p>
 */
@Data
@TableName("mem_password_reset")
public class MemPasswordReset implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "reset_id", type = IdType.ASSIGN_ID)
    private Long resetId;

    private Long memberId;

    /** 申请时使用的账号（用户名 / 手机 / 邮箱），仅用于排查 */
    private String account;

    /** 验证码 BCrypt 哈希 */
    private String codeHash;

    private LocalDateTime expireTime;

    /** 0 未使用 / 1 已使用 */
    private String used;

    private LocalDateTime createTime;

    private LocalDateTime useTime;
}
