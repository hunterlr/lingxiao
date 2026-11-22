package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.member.dto.MemberChannelTestRequest;
import com.lingxiao.modules.member.dto.MemberChannelTestResult;
import com.lingxiao.modules.member.dto.MemberChannelUpdateRequest;
import com.lingxiao.modules.member.entity.MemChannelConfig;
import com.lingxiao.modules.member.service.MemberChannelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 后台会员消息通道配置（{@code /api/admin/member/channel}）。
 *
 * <p>单独一个页面而不是混在通用参数设置里：通道有启停、dev / prod 模式、
 * 服务商与密钥等字段，放在参数列表里既拥挤又容易误改。短信 / 邮件各一行，
 * 运行期修改立即生效，不需要重启。</p>
 *
 * <p>平台域拦截器保护，会员 Token 访问会因域不同直接 401。</p>
 */
@RestController
@RequestMapping("/api/admin/member/channel")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberChannelAdminController {

    private final MemberChannelConfigService channelConfigService;

    @RequiresPermission("platform:member:channel:query")
    @GetMapping
    public R<List<MemChannelConfig>> list() {
        return R.ok(channelConfigService.list());
    }

    @RequiresPermission("platform:member:channel:edit")
    @Log(title = "会员渠道配置", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/{configId}")
    public R<Void> update(@PathVariable Long configId,
                          @Valid @RequestBody MemberChannelUpdateRequest request) {
        channelConfigService.update(configId, request);
        return R.ok();
    }

    @RequiresPermission("platform:member:channel:test")
    @Log(title = "会员渠道配置", businessType = BusinessType.OTHER, saveRequestData = false)
    @PostMapping("/{configId}/test")
    public R<MemberChannelTestResult> test(@PathVariable Long configId,
                                           @Valid @RequestBody MemberChannelTestRequest request) {
        return R.ok(channelConfigService.test(configId, request.getTarget()));
    }
}
