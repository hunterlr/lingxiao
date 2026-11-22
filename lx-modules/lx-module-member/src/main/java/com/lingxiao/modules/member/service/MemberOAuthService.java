package com.lingxiao.modules.member.service;

import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberLoginResponse;
import com.lingxiao.modules.member.dto.MemberOAuthAuthorizeVO;
import com.lingxiao.modules.member.dto.MemberOAuthBindingVO;
import com.lingxiao.modules.member.dto.MemberOAuthTicketVO;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.oauth.MemberOAuthProvider;
import com.lingxiao.modules.member.oauth.MemberOAuthProviderConfig;
import com.lingxiao.modules.member.oauth.MemberOAuthProviderRegistry;
import com.lingxiao.modules.member.oauth.MemberOAuthProviderSpec;
import com.lingxiao.modules.member.oauth.MemberOAuthState;
import com.lingxiao.modules.member.oauth.MemberOAuthStateStore;
import com.lingxiao.modules.member.oauth.MemberOAuthTicket;
import com.lingxiao.modules.member.oauth.OAuthException;
import com.lingxiao.modules.member.oauth.OAuthJson;
import com.lingxiao.modules.member.oauth.OAuthUserInfo;
import com.lingxiao.modules.member.security.MemberLoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * 会员第三方登录流程编排。
 *
 * <p>流程：</p>
 * <ol>
 *   <li>门户取授权地址 → 后端生成 state 并保存，返回平台授权 URL；</li>
 *   <li>平台回调后端 → 校验并作废 state，换用户信息，登录 / 建号 / 绑定；</li>
 *   <li>成功后 302 到门户 {@code /oauth/callback?ticket=...}；</li>
 *   <li>门户 POST {@code /api/app/auth/oauth/exchange} 用一次性 ticket 换 Token。</li>
 * </ol>
 *
 * <p>Token 从不出现在 URL 或第三方平台的回调参数里；ticket 只存会员 ID，
 * 且两分钟过期、只能使用一次。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberOAuthService {

    private static final int PKCE_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberOAuthConfigService configService;
    private final MemberOAuthAccountService accountService;
    private final MemberOAuthProviderRegistry registry;
    private final MemberOAuthStateStore stateStore;
    private final MemberLevelService levelService;
    private final MemberTokenIssuer tokenIssuer;
    private final OAuthJson json;

    /** 门户点击按钮时调用：返回平台授权地址。 */
    public MemberOAuthAuthorizeVO authorizeUrl(String provider, String mode, String redirect) {
        MemberOAuthProviderConfig config = configService.requireEnabled(provider);
        MemberOAuthProvider adapter = registry.get(provider);
        MemberOAuthProviderSpec spec = registry.spec(provider);
        if (adapter == null || spec == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.oauthProvider");
        }
        if (!spec.isBrowser()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthMiniOnly");
        }

        MemberOAuthState state = new MemberOAuthState();
        state.setProvider(provider);
        state.setRedirect(safeRedirect(redirect));
        if (MemberOAuthState.MODE_BIND.equals(mode)) {
            Long memberId = MemberLoginHelper.getMemberId();
            if (memberId == null) {
                throw new ServiceException(ErrorCode.UNAUTHORIZED);
            }
            state.setMode(MemberOAuthState.MODE_BIND);
            state.setMemberId(memberId);
        } else {
            state.setMode(MemberOAuthState.MODE_LOGIN);
        }

        String challenge = null;
        if (spec.isPkce()) {
            String verifier = randomVerifier();
            state.setCodeVerifier(verifier);
            challenge = challenge(verifier);
        }

        String stateKey = stateStore.createState(state);
        MemberOAuthAuthorizeVO vo = new MemberOAuthAuthorizeVO();
        vo.setProvider(provider);
        vo.setAuthorizeUrl(adapter.authorizeUrl(config, stateKey, challenge));
        return vo;
    }

    /**
     * 小程序等无网页授权的平台：客户端把 {@code wx.login()} 的 code 直接交给后端。
     *
     * <p>仅允许注册表里 {@code browser=false} 的平台，避免普通 OAuth 平台绕过
     * state 校验。绑定模式下会员 ID 只从服务端会话读取。</p>
     */
    public MemberOAuthTicketVO miniLogin(String provider, String code, String mode, String redirect) {
        MemberOAuthProviderSpec spec = registry.spec(provider);
        if (spec == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.oauthProvider");
        }
        if (spec.isBrowser()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthMiniOnly");
        }
        if (!StringUtils.hasText(code)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthCodeRequired");
        }
        MemberOAuthProviderConfig config = configService.requireEnabled(provider);
        MemberOAuthProvider adapter = registry.get(provider);
        if (adapter == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.oauthProvider");
        }
        // 绑定模式先校验登录态，再做外部请求：未登录时不浪费一次平台调用
        Long bindMemberId = MemberOAuthState.MODE_BIND.equals(mode) ? requireMemberId() : null;

        OAuthUserInfo info = adapter.exchange(config, code, null);
        MemberOAuthTicketVO vo = new MemberOAuthTicketVO();
        vo.setProvider(provider);
        vo.setRedirect(safeRedirect(redirect));
        if (bindMemberId != null) {
            accountService.bind(bindMemberId, info);
            vo.setBound(true);
            log.info("会员通过小程序绑定第三方账号: provider={}, memberId={}", provider, bindMemberId);
            return vo;
        }

        MemUser user = accountService.loginOrRegister(info, configService.isAutoRegister());
        vo.setTicket(stateStore.createTicket(provider, user.getMemberId()));
        log.info("小程序第三方登录成功: provider={}, memberId={}", provider, user.getMemberId());
        return vo;
    }

    /**
     * 平台回调。任何异常都转成门户可识别的错误码，不让浏览器停在 500。
     *
     * @return 302 目标地址
     */
    public String handleCallback(String provider, String code, String stateKey) {
        MemberOAuthState state = stateStore.consumeState(stateKey);
        String errorTarget = state != null && state.isBind() ? "/account" : "/login";
        try {
            if (state == null) {
                throw new OAuthException("state invalid or expired");
            }
            if (!provider.equals(state.getProvider())) {
                throw new OAuthException("state provider mismatch");
            }
            MemberOAuthProviderConfig config = configService.requireEnabled(provider);
            if (!StringUtils.hasText(code)) {
                throw new OAuthException("authorization code missing");
            }
            MemberOAuthProvider adapter = registry.get(provider);
            if (adapter == null) {
                throw new OAuthException("provider adapter missing");
            }

            OAuthUserInfo info = adapter.exchange(config, code, state.getCodeVerifier());
            if (state.isBind()) {
                if (state.getMemberId() == null) {
                    throw new ServiceException(ErrorCode.UNAUTHORIZED);
                }
                accountService.bind(state.getMemberId(), info);
                return configService.portalBaseUrl()
                        + "/account?oauthBound=" + json.encode(provider);
            }

            MemUser user = accountService.loginOrRegister(info, configService.isAutoRegister());
            String ticket = stateStore.createTicket(provider, user.getMemberId());
            log.info("第三方登录成功: provider={}, memberId={}", provider, user.getMemberId());
            return configService.portalBaseUrl()
                    + "/oauth/callback?ticket=" + json.encode(ticket)
                    + "&redirect=" + json.encode(safeRedirect(state.getRedirect()));
        } catch (ServiceException e) {
            log.warn("第三方登录被拒绝: provider={}, code={}, key={}",
                    provider, e.getCode(), e.getMessageKey());
            return errorUrl(errorTarget, provider, serviceErrorCode(e));
        } catch (OAuthException e) {
            log.warn("第三方登录失败: provider={}, error={}", provider, e.getMessage());
            String errorCode = e.getMessage() != null && e.getMessage().contains("state")
                    ? "state_invalid" : "provider_error";
            return errorUrl(errorTarget, provider, errorCode);
        } catch (RuntimeException e) {
            log.error("第三方登录异常: provider={}", provider, e);
            return errorUrl(errorTarget, provider, "failed");
        }
    }

    /** 用一次性 ticket 换会员 Token。 */
    public MemberLoginResponse exchange(String ticket) {
        if (!configService.isGlobalEnabled()) {
            throw ServiceException.keyed(ErrorCode.FORBIDDEN, "error.1003.member.oauthDisabled");
        }
        MemberOAuthTicket parsed = stateStore.consumeTicket(ticket);
        if (parsed == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthTicketInvalid");
        }
        MemUser user = accountService.requireActiveMember(parsed.getMemberId());
        String levelName = levelService.nameOf(user.getLevelId());
        log.info("第三方登录会话签发: provider={}, memberId={}", parsed.getProvider(), user.getMemberId());
        return tokenIssuer.issue(user, levelName);
    }

    public List<MemberOAuthBindingVO> bindings() {
        Long memberId = requireMemberId();
        return accountService.bindings(memberId);
    }

    public void unbind(String provider) {
        Long memberId = requireMemberId();
        accountService.unbind(memberId, provider);
    }

    private Long requireMemberId() {
        Long memberId = MemberLoginHelper.getMemberId();
        if (memberId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        return memberId;
    }

    private String errorUrl(String target, String provider, String errorCode) {
        return configService.portalBaseUrl()
                + target
                + "?oauthError=" + json.encode(errorCode)
                + "&provider=" + json.encode(provider);
    }

    /** 把后端 messageKey 收敛成门户稳定的短码，前端按码翻译。 */
    private String serviceErrorCode(ServiceException e) {
        String key = e.getMessageKey();
        if ("error.1003.member.oauthDisabled".equals(key)) {
            return "disabled";
        }
        if ("error.1003.member.oauthProviderDisabled".equals(key)) {
            return "provider_disabled";
        }
        if ("error.1001.member.oauthNotConfigured".equals(key)) {
            return "not_configured";
        }
        if ("error.1001.member.oauthNotBound".equals(key)) {
            return "not_bound";
        }
        if ("error.1001.member.oauthAlreadyBound".equals(key)) {
            return "already_bound";
        }
        if ("error.1001.member.oauthBindConflict".equals(key)) {
            return "bind_conflict";
        }
        if (e.getCode() == ErrorCode.ACCOUNT_DISABLED.getCode()) {
            return "account_disabled";
        }
        if (e.getCode() == ErrorCode.NOT_FOUND.getCode()) {
            return "member_missing";
        }
        return "failed";
    }

    /** 只允许站内路径，防止被拼成开放重定向。 */
    private String safeRedirect(String redirect) {
        if (!StringUtils.hasText(redirect)) {
            return "/account";
        }
        String value = redirect.trim();
        if (value.length() > 300 || !value.startsWith("/") || value.startsWith("//")) {
            return "/account";
        }
        return value;
    }

    private String randomVerifier() {
        byte[] bytes = new byte[PKCE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String challenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new OAuthException("cannot build PKCE challenge", e);
        }
    }
}
