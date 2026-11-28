package com.lingxiao.modules.i18n.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.modules.i18n.dto.PackVO;
import com.lingxiao.modules.i18n.dto.PortalLangConfigVO;
import com.lingxiao.modules.i18n.dto.PortalLanguageVO;
import com.lingxiao.modules.i18n.dto.PortalPackVO;
import com.lingxiao.modules.i18n.service.I18nPackService;
import com.lingxiao.modules.i18n.service.PortalLangConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门户公开多语言接口（{@code /api/portal/i18n/**}）。
 *
 * <p>不需要登录，只返回已发布译文；后台接口 {@code /api/admin/i18n/**} 与这里物理分离。</p>
 *
 * <p>{@code ETag} 用发布版本的 checksum，前端带上 {@code If-None-Match}
 * 可以拿到 304，省掉重复下载整包。</p>
 */
@RestController
@RequestMapping("/api/portal/i18n")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nPortalController {

    private final I18nPackService packService;
    private final PortalLangConfigService langConfigService;

    /** 门户语言配置：寻址方式、默认语言、可切换语言；门户用它生成 URL 与 hreflang。 */
    @GetMapping("/config")
    public R<PortalLangConfigVO> config() {
        return R.ok(langConfigService.config());
    }

    @GetMapping("/languages")
    public R<List<PortalLanguageVO>> languages() {
        return R.ok(packService.portalLanguages());
    }

    @GetMapping("/version")
    public R<PackVO> version(@RequestParam String lang,
                             @RequestParam(required = false) String namespace) {
        return R.ok(packService.portalVersion(lang, namespace));
    }

    @GetMapping("/{lang}/{namespace}")
    public ResponseEntity<R<PortalPackVO>> pack(@PathVariable String lang,
                                                @PathVariable String namespace,
                                                @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        PortalPackVO pack = packService.portalPack(lang, namespace);
        HttpHeaders headers = new HttpHeaders();
        // 发布缓存 5 秒，与后端缓存窗口一致
        headers.setCacheControl("public, max-age=5");
        if (pack.getChecksum() != null) {
            String etag = '"' + pack.getChecksum() + '"';
            headers.setETag(etag);
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
            }
        }
        if (pack.getVersion() != null) {
            headers.set("X-I18n-Version", String.valueOf(pack.getVersion()));
        }
        return ResponseEntity.ok().headers(headers).body(R.ok(pack));
    }
}
