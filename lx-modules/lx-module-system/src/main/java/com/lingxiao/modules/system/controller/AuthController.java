package com.lingxiao.modules.system.controller;

import com.lingxiao.admin.api.dto.LoginRequest;
import com.lingxiao.admin.api.dto.LoginResponse;
import com.lingxiao.admin.api.dto.MenuVO;
import com.lingxiao.admin.api.dto.PasswordChangeRequest;
import com.lingxiao.admin.api.dto.ProfileUpdateRequest;
import com.lingxiao.admin.api.dto.UserInfoResponse;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.modules.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 后台认证接口。
 *
 * <p>路径统一在 {@code /api/admin/auth} 下；登录与验证码由安全配置放行，
 * 其余接口必须带平台域 Token。</p>
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @Log(title = "退出登录", businessType = BusinessType.OTHER)
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @GetMapping("/info")
    public R<UserInfoResponse> info() {
        return R.ok(authService.getInfo());
    }

    @GetMapping("/menus")
    public R<List<MenuVO>> menus() {
        return R.ok(authService.getMenus());
    }

    /**
     * 修改本人资料。
     *
     * <p>{@code saveRequestData = false}：请求体里虽然没有密码，但邮箱 / 手机号
     * 属于个人信息，操作日志只留“谁在什么时候改了个人资料”，不存具体值。</p>
     */
    @Log(title = "个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/profile")
    public R<UserInfoResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return R.ok(authService.updateProfile(request));
    }

    /**
     * 修改本人密码。
     *
     * <p>必须记录审计，但绝不能记录请求参数——否则明文密码会写进 sys_oper_log。</p>
     */
    @Log(title = "个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(request);
        return R.ok();
    }
}
