package com.lingxiao.modules.i18n.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.i18n.dto.PackPublishRequest;
import com.lingxiao.modules.i18n.dto.PackRollbackRequest;
import com.lingxiao.modules.i18n.dto.PackVO;
import com.lingxiao.modules.i18n.service.I18nPackService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 后台多语言 — 语言包发布 / 回滚。
 *
 * <p>发布成功后本实例缓存立即清空；多实例最迟 5 秒读到新译文。</p>
 */
@RestController
@RequestMapping("/api/admin/i18n/pack")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.i18n.enabled", havingValue = "true")
public class I18nPackController {

    private final I18nPackService packService;

    @RequiresPermission("platform:i18n:pack:list")
    @GetMapping("/list")
    public R<List<PackVO>> list(@RequestParam(required = false) String languageCode,
                                @RequestParam(required = false) String namespace) {
        return R.ok(packService.list(languageCode, namespace));
    }

    @RequiresPermission("platform:i18n:pack:list")
    @GetMapping("/current")
    public R<List<PackVO>> current(@RequestParam(required = false) String languageCode,
                                   @RequestParam(required = false) String namespace) {
        return R.ok(packService.current(languageCode, namespace));
    }

    @RequiresPermission("platform:i18n:pack:publish")
    @Log(title = "语言包发布", businessType = BusinessType.UPDATE)
    @PostMapping("/publish")
    public R<List<PackVO>> publish(@Valid @RequestBody PackPublishRequest request) {
        return R.ok(packService.publish(request, LoginHelper.getUserId()));
    }

    @RequiresPermission("platform:i18n:pack:rollback")
    @Log(title = "语言包回滚", businessType = BusinessType.UPDATE)
    @PostMapping("/rollback")
    public R<PackVO> rollback(@Valid @RequestBody PackRollbackRequest request) {
        return R.ok(packService.rollback(request, LoginHelper.getUserId()));
    }
}
