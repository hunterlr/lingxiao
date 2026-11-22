package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.member.dto.MemberOAuthProviderUpdateRequest;
import com.lingxiao.modules.member.dto.MemberOAuthProviderVO;
import com.lingxiao.modules.member.service.MemberOAuthConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 后台第三方登录配置（{@code /api/admin/member/oauth}）。
 *
 * <p>由平台域拦截器保护，会员 Token 访问会因域不同直接 401；这里只配置
 * 门户登录入口，不会创建任何后台账号。</p>
 */
@RestController
@RequestMapping("/api/admin/member/oauth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberOAuthAdminController {

    private final MemberOAuthConfigService configService;

    @RequiresPermission("platform:member:oauth:list")
    @GetMapping
    public R<List<MemberOAuthProviderVO>> list() {
        return R.ok(configService.listForAdmin());
    }

    @RequiresPermission("platform:member:oauth:edit")
    @Log(title = "第三方登录配置", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/{provider}")
    public R<MemberOAuthProviderVO> update(@PathVariable String provider,
                                           @Valid @RequestBody MemberOAuthProviderUpdateRequest request) {
        return R.ok(configService.update(provider, request));
    }
}
