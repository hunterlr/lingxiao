package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.modules.member.dto.MemberLoginResponse;
import com.lingxiao.modules.member.dto.MemberOAuthAuthorizeVO;
import com.lingxiao.modules.member.dto.MemberOAuthBindingVO;
import com.lingxiao.modules.member.dto.MemberOAuthBindTicketRequest;
import com.lingxiao.modules.member.dto.MemberOAuthExchangeRequest;
import com.lingxiao.modules.member.dto.MemberOAuthMiniLoginRequest;
import com.lingxiao.modules.member.dto.MemberOAuthProvidersVO;
import com.lingxiao.modules.member.dto.MemberOAuthTicketVO;
import com.lingxiao.modules.member.service.MemberOAuthConfigService;
import com.lingxiao.modules.member.service.MemberOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;

/**
 * 会员第三方登录接口（{@code /api/app/auth/oauth/**}）。
 *
 * <p><b>公开：</b>入口列表、授权地址、平台回调、ticket 换 Token。
 * 平台回调必须匿名可访问，否则浏览器从微信 / GitHub 跳回来会被 401。</p>
 *
 * <p><b>需登录：</b>绑定列表与解绑。绑定发起用 {@code authorize-url?mode=bind}，
 * 由 Service 自己校验登录态，因此该接口保持匿名可访问。</p>
 *
 * <p>Token 只通过 {@code POST /exchange} 的响应体下发，不出现在回调 URL。</p>
 */
@RestController
@RequestMapping("/api/app/auth/oauth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberOAuthController {

    private final MemberOAuthService oauthService;
    private final MemberOAuthConfigService configService;

    /** 门户登录 / 注册页：总开关关闭时返回 enabled=false 与空列表。 */
    @GetMapping("/providers")
    public R<MemberOAuthProvidersVO> providers() {
        return R.ok(configService.publicProviders());
    }

    /** 取平台授权地址；{@code mode=bind} 时需要携带会员 Token。 */
    @GetMapping("/authorize-url")
    public R<MemberOAuthAuthorizeVO> authorizeUrl(@RequestParam String provider,
                                                  @RequestParam(required = false, defaultValue = "login") String mode,
                                                  @RequestParam(required = false) String redirect) {
        return R.ok(oauthService.authorizeUrl(provider, mode, redirect));
    }

    /**
     * 平台回调。
     *
     * <p>成功时 302 到门户 {@code /oauth/callback}，失败时 302 回登录 / 账户页并带
     * 稳定错误码。这里必须返回 {@link ResponseEntity}，不能返回 {@code R}，
     * 否则浏览器会停在 JSON 上。</p>
     */
    @GetMapping("/callback/{provider}")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String target = oauthService.handleCallback(provider, code, state);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(target));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /** 一次性 ticket 换会员 Token。 */
    @PostMapping("/exchange")
    public R<MemberLoginResponse> exchange(@Valid @RequestBody MemberOAuthExchangeRequest request) {
        return R.ok(oauthService.exchange(request.getTicket()));
    }

    /**
     * 小程序登录：客户端用 {@code wx.login()} 的 code 直接换 ticket / 绑定。
     * 普通 OAuth 平台不能走这个入口，必须经过 state 校验。
     */
    @PostMapping("/mini-login")
    public R<MemberOAuthTicketVO> miniLogin(@Valid @RequestBody MemberOAuthMiniLoginRequest request) {
        return R.ok(oauthService.miniLogin(
                request.getProvider(), request.getCode(), request.getMode(), request.getRedirect()));
    }

    /**
     * 完成“命中已有会员”后的绑定。
     *
     * <p>必须带会员 Token（StpMember 拦截器保护 {@code /api/app/**} 下的认证接口），
     * ticket 两分钟有效且只能用一次；当前会员必须等于回调时命中的会员。</p>
     */
    @PostMapping("/bind-ticket")
    public R<Void> bindTicket(@Valid @RequestBody MemberOAuthBindTicketRequest request) {
        oauthService.bindWithTicket(request.getTicket());
        return R.ok();
    }

    /** 当前会员已绑定的第三方账号。 */
    @GetMapping("/bindings")
    public R<List<MemberOAuthBindingVO>> bindings() {
        return R.ok(oauthService.bindings());
    }

    /** 解绑；解绑后仍可用其它方式登录。 */
    @DeleteMapping("/bindings/{provider}")
    public R<Void> unbind(@PathVariable String provider) {
        oauthService.unbind(provider);
        return R.ok();
    }
}
