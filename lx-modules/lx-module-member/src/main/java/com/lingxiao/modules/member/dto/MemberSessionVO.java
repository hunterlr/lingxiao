package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员登录设备（一次会话）。
 *
 * <p>{@code sessionId} 是 Token 的哈希句柄，用来“退出该设备”；
 * 原始 Token 不会出现在响应里。</p>
 */
@Data
public class MemberSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话句柄（Token 的 SHA-256 前 16 位），用于踢下线 */
    private String sessionId;

    /** 是否是当前请求所在设备 */
    private Boolean currentSession;

    /** 由 User-Agent 归并出的设备名，如 “Windows · Chrome” */
    private String device;

    private String userAgent;

    private String loginIp;

    private LocalDateTime loginTime;

    /** 剩余有效期（秒），到点自动失效 */
    private Long expiresIn;
}