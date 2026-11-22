package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberOAuthProviderItemVO;
import com.lingxiao.modules.member.dto.MemberOAuthProviderUpdateRequest;
import com.lingxiao.modules.member.dto.MemberOAuthProviderVO;
import com.lingxiao.modules.member.dto.MemberOAuthProvidersVO;
import com.lingxiao.modules.member.entity.MemOAuthProvider;
import com.lingxiao.modules.member.mapper.MemOAuthProviderMapper;
import com.lingxiao.modules.member.oauth.MemberOAuthProviderConfig;
import com.lingxiao.modules.member.oauth.MemberOAuthProviderRegistry;
import com.lingxiao.modules.member.oauth.MemberOAuthProviderSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员第三方登录配置。
 *
 * <p>“代码里有哪些平台”由 {@link MemberOAuthProviderRegistry} 决定，
 * “现在启用哪些平台”由 {@code mem_oauth_provider} 决定。两者分开后，
 * 新增平台只需要在注册表加一条描述，不需要改表结构。</p>
 *
 * <p>总开关 {@code mem.oauth.enabled} 放在会员设置页；单平台开关放在
 * 「会员管理 → 第三方登录」页。平台没有同时配置 ClientId / Secret 时，
 * 即使开关是启用的也不会出现在门户，避免用户点进去看到平台错误页。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberOAuthConfigService {

    public static final String CONFIG_ENABLED = "mem.oauth.enabled";
    public static final String CONFIG_AUTO_REGISTER = "mem.oauth.autoRegister";
    public static final String CONFIG_PORTAL_BASE_URL = "mem.oauth.portalBaseUrl";

    private static final String STATUS_ENABLED = "0";
    private static final String STATUS_DISABLED = "1";
    private static final String DEFAULT_PORTAL_BASE_URL = "http://127.0.0.1:3000";
    private static final String CALLBACK_PATH = "/api/app/auth/oauth/callback/";

    private final MemOAuthProviderMapper providerMapper;
    private final MemberOAuthProviderRegistry registry;
    private final ConfigProvider configProvider;
    private final ObjectMapper objectMapper;

    public boolean isGlobalEnabled() {
        return configProvider.getBooleanValue(CONFIG_ENABLED, false);
    }

    public boolean isAutoRegister() {
        return configProvider.getBooleanValue(CONFIG_AUTO_REGISTER, true);
    }

    public String portalBaseUrl() {
        String value = configProvider.getValue(CONFIG_PORTAL_BASE_URL);
        String base = StringUtils.hasText(value) ? value.trim() : DEFAULT_PORTAL_BASE_URL;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /** 服务商后台要填的回调地址；后台未覆写时按门户地址自动生成。 */
    public String callbackUrl(String provider) {
        return portalBaseUrl() + CALLBACK_PATH + provider;
    }

    /** 门户入口：只返回“已启用且已配置”的平台。 */
    public MemberOAuthProvidersVO publicProviders() {
        MemberOAuthProvidersVO result = new MemberOAuthProvidersVO();
        result.setEnabled(isGlobalEnabled());
        result.setAutoRegister(isAutoRegister());
        if (!result.isEnabled()) {
            return result;
        }
        Map<String, MemOAuthProvider> rows = rowMap();
        List<MemberOAuthProviderItemVO> items = new ArrayList<>();
        for (MemberOAuthProviderSpec spec : registry.specs()) {
            if (!spec.isBrowser()) {
                // 小程序等平台没有网页授权入口，不能出现在门户按钮里
                continue;
            }
            MemOAuthProvider row = rows.get(spec.getCode());
            if (row == null || !STATUS_ENABLED.equals(row.getStatus())) {
                continue;
            }
            MemberOAuthProviderConfig config = buildConfig(row, spec);
            if (!config.isConfigured()) {
                continue;
            }
            MemberOAuthProviderItemVO item = new MemberOAuthProviderItemVO();
            item.setProvider(spec.getCode());
            item.setDisplayName(StringUtils.hasText(row.getDisplayName())
                    ? row.getDisplayName() : spec.getDisplayName());
            item.setGroup(spec.getGroup());
            items.add(item);
        }
        result.setProviders(items);
        return result;
    }

    /** 后台列表：注册表里的全部平台，缺配置行时给出默认值。 */
    public List<MemberOAuthProviderVO> listForAdmin() {
        Map<String, MemOAuthProvider> rows = rowMap();
        List<MemberOAuthProviderVO> result = new ArrayList<>();
        int fallbackSort = 100;
        for (MemberOAuthProviderSpec spec : registry.specs()) {
            MemOAuthProvider row = rows.get(spec.getCode());
            if (row == null) {
                row = new MemOAuthProvider();
                row.setTenantId(0L);
                row.setProvider(spec.getCode());
                row.setDisplayName(spec.getDisplayName());
                row.setStatus(STATUS_DISABLED);
                row.setSort(fallbackSort);
            }
            fallbackSort += 10;
            result.add(toVO(row, spec));
        }
        result.sort((left, right) -> {
            int leftSort = left.getSort() == null ? 0 : left.getSort();
            int rightSort = right.getSort() == null ? 0 : right.getSort();
            if (leftSort != rightSort) {
                return Integer.compare(leftSort, rightSort);
            }
            return left.getProvider().compareTo(right.getProvider());
        });
        return result;
    }

    public MemberOAuthProviderVO update(String provider, MemberOAuthProviderUpdateRequest request) {
        MemberOAuthProviderSpec spec = registry.spec(provider);
        if (spec == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.oauthProvider");
        }
        MemOAuthProvider row = providerMapper.selectById(provider);
        boolean exists = row != null;
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new MemOAuthProvider();
            row.setTenantId(0L);
            row.setProvider(provider);
            row.setDisplayName(spec.getDisplayName());
            row.setStatus(STATUS_DISABLED);
            row.setSort(100);
            row.setCreateTime(now);
        }

        if (StringUtils.hasText(request.getDisplayName())) {
            row.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getStatus() != null) {
            if (!STATUS_ENABLED.equals(request.getStatus()) && !STATUS_DISABLED.equals(request.getStatus())) {
                throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.member.oauth.status");
            }
            row.setStatus(request.getStatus());
        }
        if (request.getClientId() != null) {
            row.setClientId(trimToNull(request.getClientId()));
        }
        if (Boolean.TRUE.equals(request.getClearSecret())) {
            row.setClientSecret(null);
        } else if (StringUtils.hasText(request.getClientSecret())) {
            row.setClientSecret(request.getClientSecret().trim());
        }
        if (request.getRedirectUri() != null) {
            row.setRedirectUri(trimToNull(request.getRedirectUri()));
        }
        if (request.getScope() != null) {
            row.setScope(trimToNull(request.getScope()));
        }
        if (request.getAuthorizeUrl() != null) {
            row.setAuthorizeUrl(trimToNull(request.getAuthorizeUrl()));
        }
        if (request.getTokenUrl() != null) {
            row.setTokenUrl(trimToNull(request.getTokenUrl()));
        }
        if (request.getUserInfoUrl() != null) {
            row.setUserInfoUrl(trimToNull(request.getUserInfoUrl()));
        }
        if (request.getExtraJson() != null) {
            String extraJson = trimToNull(request.getExtraJson());
            if (extraJson != null) {
                try {
                    objectMapper.readTree(extraJson);
                } catch (Exception e) {
                    throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthExtraJson");
                }
            }
            row.setExtraJson(extraJson);
        }
        if (request.getSort() != null) {
            row.setSort(request.getSort());
        }
        if (request.getRemark() != null) {
            row.setRemark(trimToNull(request.getRemark()));
        }
        row.setUpdateTime(now);

        if (row.getCreateTime() == null) {
            row.setCreateTime(now);
        }
        if (!exists) {
            providerMapper.insert(row);
        } else {
            providerMapper.updateById(row);
            // MyBatis-Plus updateById 默认忽略 null，清空密钥必须显式 set
            if (Boolean.TRUE.equals(request.getClearSecret())) {
                providerMapper.update(null, new LambdaUpdateWrapper<MemOAuthProvider>()
                        .eq(MemOAuthProvider::getProvider, provider)
                        .set(MemOAuthProvider::getClientSecret, null));
                row.setClientSecret(null);
            }
        }
        log.info("后台修改第三方登录配置: provider={}, status={}, configured={}",
                provider, row.getStatus(), hasText(row.getClientId()) && hasText(row.getClientSecret()));
        return toVO(row, spec);
    }

    /**
     * 取一个可用的平台配置；全局关闭、平台停用、未配置密钥都会抛业务异常。
     *
     * <p>错误码故意分开：门户按 code 区分“功能关闭”和“平台未配置”，
     * 不能把配置问题伪装成用户操作失败。</p>
     */
    public MemberOAuthProviderConfig requireEnabled(String provider) {
        if (!isGlobalEnabled()) {
            throw ServiceException.keyed(ErrorCode.FORBIDDEN, "error.1003.member.oauthDisabled");
        }
        MemberOAuthProviderSpec spec = registry.spec(provider);
        if (spec == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.oauthProvider");
        }
        MemOAuthProvider row = providerMapper.selectById(provider);
        if (row == null || !STATUS_ENABLED.equals(row.getStatus())) {
            throw ServiceException.keyed(ErrorCode.FORBIDDEN,
                    "error.1003.member.oauthProviderDisabled", spec.getDisplayName());
        }
        MemberOAuthProviderConfig config = buildConfig(row, spec);
        if (!config.isConfigured()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.oauthNotConfigured");
        }
        return config;
    }

    /** 已完成绑定的平台即使关闭入口，也要能显示名称，便于会员自助解绑。 */
    public String displayName(String provider) {
        MemberOAuthProviderSpec spec = registry.spec(provider);
        if (spec != null) {
            MemOAuthProvider row = providerMapper.selectById(provider);
            if (row != null && StringUtils.hasText(row.getDisplayName())) {
                return row.getDisplayName();
            }
            return spec.getDisplayName();
        }
        return provider;
    }

    private MemberOAuthProviderConfig buildConfig(MemOAuthProvider row, MemberOAuthProviderSpec spec) {
        String redirect = StringUtils.hasText(row.getRedirectUri())
                ? row.getRedirectUri().trim()
                : callbackUrl(spec.getCode());
        return new MemberOAuthProviderConfig(row, spec, redirect);
    }

    private Map<String, MemOAuthProvider> rowMap() {
        List<MemOAuthProvider> rows = providerMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, MemOAuthProvider> result = new LinkedHashMap<>();
        for (MemOAuthProvider row : rows) {
            result.put(row.getProvider(), row);
        }
        return result;
    }

    private MemberOAuthProviderVO toVO(MemOAuthProvider row, MemberOAuthProviderSpec spec) {
        MemberOAuthProviderVO vo = new MemberOAuthProviderVO();
        vo.setProvider(spec.getCode());
        vo.setDisplayName(StringUtils.hasText(row.getDisplayName())
                ? row.getDisplayName() : spec.getDisplayName());
        vo.setGroup(spec.getGroup());
        vo.setBrowser(spec.isBrowser());
        vo.setStatus(StringUtils.hasText(row.getStatus()) ? row.getStatus() : STATUS_DISABLED);
        vo.setClientId(row.getClientId());
        vo.setHasSecret(hasText(row.getClientSecret()));
        vo.setConfigured(hasText(row.getClientId()) && hasText(row.getClientSecret()));
        vo.setRedirectUri(row.getRedirectUri());
        vo.setScope(row.getScope());
        vo.setAuthorizeUrl(row.getAuthorizeUrl());
        vo.setTokenUrl(row.getTokenUrl());
        vo.setUserInfoUrl(row.getUserInfoUrl());
        vo.setExtraJson(row.getExtraJson());
        vo.setSort(row.getSort());
        vo.setRemark(row.getRemark());
        vo.setSuggestedRedirectUri(callbackUrl(spec.getCode()));
        return vo;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
