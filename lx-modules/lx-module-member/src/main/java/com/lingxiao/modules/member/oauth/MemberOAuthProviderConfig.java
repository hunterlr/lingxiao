package com.lingxiao.modules.member.oauth;

import com.lingxiao.modules.member.entity.MemOAuthProvider;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 解析后的第三方登录运行期配置：数据库配置 + 内置默认值。
 *
 * <p>端点、scope、回调地址都可以在后台覆写；留空时回落到 Provider 描述里的默认值。
 * 回调地址留空时由会员服务按 {@code mem.oauth.portalBaseUrl} 自动拼出，避免运营
 * 忘记配置时只能在浏览器里看到平台错误页。</p>
 */
@Getter
public class MemberOAuthProviderConfig {

    private final MemOAuthProvider entity;

    private final MemberOAuthProviderSpec spec;

    private final String redirectUri;

    public MemberOAuthProviderConfig(MemOAuthProvider entity, MemberOAuthProviderSpec spec, String redirectUri) {
        this.entity = entity;
        this.spec = spec;
        this.redirectUri = redirectUri;
    }

    public String getProvider() {
        return spec.getCode();
    }

    public String getDisplayName() {
        return StringUtils.hasText(entity.getDisplayName()) ? entity.getDisplayName() : spec.getDisplayName();
    }

    public String getClientId() {
        return trim(entity.getClientId());
    }

    public String getClientSecret() {
        return trim(entity.getClientSecret());
    }

    public String getScope() {
        return StringUtils.hasText(entity.getScope()) ? entity.getScope().trim() : spec.getDefaultScope();
    }

    public String getAuthorizeUrl() {
        return pick(entity.getAuthorizeUrl(), spec.getAuthorizeUrl());
    }

    public String getTokenUrl() {
        return pick(entity.getTokenUrl(), spec.getTokenUrl());
    }

    public String getUserInfoUrl() {
        return pick(entity.getUserInfoUrl(), spec.getUserInfoUrl());
    }

    public boolean isConfigured() {
        return StringUtils.hasText(getClientId()) && StringUtils.hasText(getClientSecret());
    }

    private String pick(String override, String fallback) {
        return StringUtils.hasText(override) ? override.trim() : fallback;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
