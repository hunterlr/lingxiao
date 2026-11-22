package com.lingxiao.modules.member.service;

import com.lingxiao.modules.member.entity.MemUser;

/**
 * 找回密码验证码下发通道。
 *
 * <p>v1.2 第一刀只实现日志通道：开发 / 演示环境在后台日志里看 6 位验证码。
 * 接入短信或邮件时新增实现并替换 Bean 即可，业务 Service 不关心通道。</p>
 */
public interface MemberResetCodeSender {

    void send(MemUser user, String code, int expireMinutes);
}
