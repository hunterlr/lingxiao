package com.lingxiao.modules.i18n.service;

import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.modules.i18n.dto.PortalLangConfigVO;
import com.lingxiao.modules.i18n.entity.I18nLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门户语言配置。
 *
 * <p><b>四个参数都在「多语言 → 门户设置」：</b></p>
 * <ul>
 *   <li>{@code portal.i18n.mode}：path / query / cookie，决定语言怎么进 URL；</li>
 *   <li>{@code portal.i18n.defaultLang}：默认语言，不带前缀（除非打开下一条）；</li>
 *   <li>{@code portal.i18n.defaultLangInPath}：默认语言是否也带前缀；</li>
 *   <li>{@code portal.i18n.langs}：门户对外语言，逗号分隔。</li>
 * </ul>
 *
 * <p>参数非法或指向已停用语言时回退安全默认值（path / zh-CN / 中文不带前缀），
 * 保证门户不会因为一个参数写错而整站 500。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class PortalLangConfigService {

    public static final String MODE_PATH = "path";
    public static final String MODE_QUERY = "query";
    public static final String MODE_COOKIE = "cookie";

    private static final String KEY_MODE = "portal.i18n.mode";
    private static final String KEY_DEFAULT_LANG = "portal.i18n.defaultLang";
    private static final String KEY_DEFAULT_IN_PATH = "portal.i18n.defaultLangInPath";
    private static final String KEY_LANGS = "portal.i18n.langs";
    private static final String FALLBACK_CODE = "zh-CN";

    private final ConfigProvider configProvider;
    private final I18nLanguageService languageService;

    /** 门户每 5 秒读一次；不缓存，改完参数很快生效。 */
    public PortalLangConfigVO config() {
        List<I18nLanguage> enabled = languageService.options();
        List<PortalLangConfigVO.Language> languages = resolveLanguages(enabled);
        String defaultLang = resolveDefaultLang(configProvider.getValue(KEY_DEFAULT_LANG), languages);

        PortalLangConfigVO vo = new PortalLangConfigVO();
        vo.setMode(normalizeMode(configProvider.getValue(KEY_MODE)));
        vo.setDefaultLang(defaultLang);
        vo.setDefaultLangInPath(configProvider.getBooleanValue(KEY_DEFAULT_IN_PATH, false));
        for (PortalLangConfigVO.Language language : languages) {
            language.setDefault(language.getCode().equalsIgnoreCase(defaultLang));
        }
        vo.setLanguages(languages);
        return vo;
    }

    /** 语言代码 → URL 短码：`en-US` → `en`，其它按小写并保留地区段。 */
    public static String urlCode(String languageCode) {
        if (!StringUtils.hasText(languageCode)) {
            return "";
        }
        String value = languageCode.trim();
        String[] parts = value.split("-");
        if (parts.length == 1) {
            return parts[0].toLowerCase(Locale.ROOT);
        }
        // 英文去掉地区，其它语言保留（zh-CN → zh-cn），便于阅读与 SEO
        if ("en".equalsIgnoreCase(parts[0])) {
            return "en";
        }
        return parts[0].toLowerCase(Locale.ROOT) + "-" + parts[1].toLowerCase(Locale.ROOT);
    }

    /** URL 短码 → 语言代码；认不出返回 null。 */
    public static String languageCodeOfUrl(String urlCode, List<PortalLangConfigVO.Language> languages) {
        if (!StringUtils.hasText(urlCode) || languages == null) {
            return null;
        }
        for (PortalLangConfigVO.Language language : languages) {
            if (language.getUrlCode().equalsIgnoreCase(urlCode.trim())) {
                return language.getCode();
            }
        }
        return null;
    }

    private List<PortalLangConfigVO.Language> resolveLanguages(List<I18nLanguage> enabled) {
        Set<String> allowed = parseCodes(configProvider.getValue(KEY_LANGS));
        List<I18nLanguage> source = enabled == null ? new ArrayList<>() : enabled;
        List<I18nLanguage> picked = source.stream()
                .filter(item -> allowed.isEmpty() || allowed.contains(item.getLanguageCode()))
                .collect(Collectors.toList());
        if (picked.isEmpty()) {
            picked = source;
        }
        List<PortalLangConfigVO.Language> result = new ArrayList<>();
        for (I18nLanguage item : picked) {
            PortalLangConfigVO.Language language = new PortalLangConfigVO.Language();
            language.setCode(item.getLanguageCode());
            language.setUrlCode(urlCode(item.getLanguageCode()));
            language.setName(item.getLanguageName());
            language.setNativeName(StringUtils.hasText(item.getNativeName())
                    ? item.getNativeName() : item.getLanguageName());
            language.setDirection(StringUtils.hasText(item.getDirection()) ? item.getDirection() : "ltr");
            result.add(language);
        }
        if (result.isEmpty()) {
            PortalLangConfigVO.Language fallback = new PortalLangConfigVO.Language();
            fallback.setCode(FALLBACK_CODE);
            fallback.setUrlCode(urlCode(FALLBACK_CODE));
            fallback.setName("简体中文");
            fallback.setNativeName("简体中文");
            fallback.setDirection("ltr");
            result.add(fallback);
        }
        return result;
    }

    private String resolveDefaultLang(String configured, List<PortalLangConfigVO.Language> languages) {
        if (StringUtils.hasText(configured)) {
            String value = configured.trim();
            boolean exists = languages.stream().anyMatch(item -> item.getCode().equalsIgnoreCase(value));
            if (exists) {
                return languages.stream()
                        .filter(item -> item.getCode().equalsIgnoreCase(value))
                        .map(PortalLangConfigVO.Language::getCode)
                        .findFirst().orElse(languages.get(0).getCode());
            }
            log.warn("门户默认语言配置为未启用语言，回退首个可用语言: {}", value);
        }
        return languages.stream().filter(item -> item.getUrlCode().equalsIgnoreCase("zh-cn"))
                .map(PortalLangConfigVO.Language::getCode)
                .findFirst().orElse(languages.get(0).getCode());
    }

    private String normalizeMode(String value) {
        String mode = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (MODE_QUERY.equals(mode) || MODE_COOKIE.equals(mode)) {
            return mode;
        }
        return MODE_PATH;
    }

    private Set<String> parseCodes(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}