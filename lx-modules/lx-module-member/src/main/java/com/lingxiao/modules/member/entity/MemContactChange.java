package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员手机号 / 邮箱换绑验证码（mem_contact_change）。
 *
 * <p>只存验证码的 BCrypt 哈希，不存明文；验证码发送到新的联系方式，
 * 用来证明新值归当前会员所有。记录一次性使用，过期或使用后不再接受。</p>
 *
 * <p>{@code old_value} 只用于排查与审计，换绑成功后不会回写；
 * 会员当前联系方式仍以 {@code mem_user.mobile / email} 为准。</p>
 */
@Data
@TableName("mem_contact_change")
public class MemContactChange implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "change_id", type = IdType.ASSIGN_ID)
    private Long changeId;

    private Long memberId;

    /** mobile / email */
    private String contactType;

    /** 申请时的旧联系方式，仅审计 */
    private String oldValue;

    /** 待绑定的新手机号 / 邮箱 */
    private String newValue;

    /** 验证码 BCrypt 哈希 */
    private String codeHash;

    private LocalDateTime expireTime;

    /** 0 未使用 / 1 已使用 */
    private String used;

    private String requestIp;

    private LocalDateTime createTime;

    private LocalDateTime useTime;
}
