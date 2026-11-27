package com.lingxiao.modules.member.service;

import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 会员登录设备管理。
 *
 * <p><b>不泄露 Token：</b>对外的会话标识是 Token 的 SHA-256 前 16 位，
 * 前端凭它踢下线；服务端每次都按同一算法重算后比对，不保存额外映射。</p>
 *
 * <p><b>当前设备不能在这里踢：</b>踢掉当前会话等价于退出登录，容易让用户困惑，
 * 统一提示走“退出登录”。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberSessionService {

    private static final int HANDLE_LENGTH = 16;

    private final MemberSessionStore sessionStore;

    /** 当前会员的所有有效会话；当前设备排在最前，其余按登录时间倒序。 */
    public List<MemberSessionVO> list(Long memberId) {
        Long currentMemberId = requireMemberId(memberId);
        String currentToken = sessionStore.currentToken();
        List<MemberSessionVO> result = new ArrayList<>();
        for (String token : sessionStore.tokens(currentMemberId)) {
            long timeout = sessionStore.timeout(token);
            if (timeout <= 0) {
                // 已过期但还没被清理的 Token，不展示
                continue;
            }
            MemberSessionStore.SessionContext context = sessionStore.context(token);
            MemberSessionVO vo = new MemberSessionVO();
            vo.setSessionId(handle(token));
            vo.setCurrentSession(currentToken != null && currentToken.equals(token));
            vo.setDevice(deviceLabel(context.getUserAgent()));
            vo.setUserAgent(context.getUserAgent());
            vo.setLoginIp(context.getLoginIp());
            vo.setLoginTime(context.getLoginTime());
            vo.setExpiresIn(timeout);
            result.add(vo);
        }
        result.sort(Comparator
                .comparing((MemberSessionVO vo) -> !Boolean.TRUE.equals(vo.getCurrentSession()))
                .thenComparing(vo -> vo.getLoginTime() == null ? null : vo.getLoginTime(),
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /** 踢掉指定会话；只能踢自己的，且不能踢当前设备。 */
    public void kick(Long memberId, String sessionId) {
        Long currentMemberId = requireMemberId(memberId);
        if (!StringUtils.hasText(sessionId)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1004.member.session");
        }
        String currentToken = sessionStore.currentToken();
        for (String token : sessionStore.tokens(currentMemberId)) {
            if (!handle(token).equals(sessionId)) {
                continue;
            }
            if (currentToken != null && currentToken.equals(token)) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.session.current");
            }
            sessionStore.logout(token);
            log.info("会员退出指定设备: memberId={}, session={}", currentMemberId, sessionId);
            return;
        }
        throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.session");
    }

    /** 退出除当前设备外的所有会话，返回被踢掉的数量。 */
    public int kickOthers(Long memberId) {
        Long currentMemberId = requireMemberId(memberId);
        String currentToken = sessionStore.currentToken();
        int kicked = 0;
        for (String token : sessionStore.tokens(currentMemberId)) {
            if (currentToken != null && currentToken.equals(token)) {
                continue;
            }
            sessionStore.logout(token);
            kicked++;
        }
        log.info("会员退出其它设备: memberId={}, kicked={}", currentMemberId, kicked);
        return kicked;
    }

    /**
     * Token → 会话句柄。
     *
     * <p>取 SHA-256 的前 16 个十六进制字符（64 bit）：足够区分同一会员的几台设备，
     * 又不能反推出 Token。会员模块没有额外依赖 crypto 包，这里用 JDK 自带的
     * {@link MessageDigest}，避免为一行摘要引入新依赖。</p>
     */
    private String handle(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            char[] digits = "0123456789abcdef".toCharArray();
            StringBuilder sb = new StringBuilder(HANDLE_LENGTH);
            for (int i = 0; i < HANDLE_LENGTH / 2; i++) {
                int value = hash[i] & 0xFF;
                sb.append(digits[value >>> 4]).append(digits[value & 0x0F]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必须提供 SHA-256；走到这里说明运行环境被裁剪过
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 从 User-Agent 归并出可读设备名。
     *
     * <p>只做展示，不做风控判断；认不出来的统一叫“未知设备”。</p>
     */
    private String deviceLabel(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "未知设备";
        }
        String ua = userAgent.toLowerCase();
        String os;
        if (ua.contains("windows")) {
            os = "Windows";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            os = "iOS";
        } else if (ua.contains("mac os") || ua.contains("macintosh")) {
            os = "macOS";
        } else if (ua.contains("linux")) {
            os = "Linux";
        } else {
            return "未知设备";
        }

        String browser;
        if (ua.contains("edg/")) {
            browser = "Edge";
        } else if (ua.contains("chrome/") || ua.contains("crios/")) {
            browser = "Chrome";
        } else if (ua.contains("firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("safari/")) {
            browser = "Safari";
        } else {
            browser = null;
        }
        return browser == null ? os : os + " · " + browser;
    }

    private Long requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        return memberId;
    }
}