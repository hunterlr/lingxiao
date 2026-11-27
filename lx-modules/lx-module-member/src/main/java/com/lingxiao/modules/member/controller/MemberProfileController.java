package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.modules.member.dto.MemberContactChangeRequest;
import com.lingxiao.modules.member.dto.MemberContactCodeRequest;
import com.lingxiao.modules.member.dto.MemberInfoVO;
import com.lingxiao.modules.member.dto.MemberPasswordChangeRequest;
import com.lingxiao.modules.member.dto.MemberPasswordSetRequest;
import com.lingxiao.modules.member.dto.MemberProfileUpdateRequest;
import com.lingxiao.modules.member.dto.MemberSessionVO;
import com.lingxiao.modules.member.security.MemberLoginHelper;
import com.lingxiao.modules.member.service.MemberAvatarService;
import com.lingxiao.modules.member.service.MemberContactService;
import com.lingxiao.modules.member.service.MemberSessionService;
import com.lingxiao.modules.member.service.MemberUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

/**
 * 会员个人中心（{@code /api/app/profile/**}）。
 *
 * <p>由会员域拦截器保护，未登录直接 401；会员只能看 / 改自己的资料，
 * 不接受 {@code memberId} 参数，避免越权改别人。</p>
 */
@RestController
@RequestMapping("/api/app/profile")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberProfileController {

    private final MemberUserService userService;
    private final MemberContactService contactService;
    private final MemberAvatarService avatarService;
    private final MemberSessionService sessionService;

    @GetMapping
    public R<MemberInfoVO> info() {
        return R.ok(userService.getInfo());
    }

    @PutMapping
    public R<MemberInfoVO> updateProfile(@Valid @RequestBody MemberProfileUpdateRequest request) {
        return R.ok(userService.updateProfile(request));
    }

    /** 改密接口不记录请求参数，避免明文密码进操作日志。 */
    @Log(title = "会员个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody MemberPasswordChangeRequest request) {
        userService.changePassword(request);
        return R.ok();
    }

    /** 第三方建号首次设置密码；已设置密码的账号只能走改密接口。 */
    @Log(title = "会员个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/password/set")
    public R<Void> setPassword(@Valid @RequestBody MemberPasswordSetRequest request) {
        userService.setPassword(request);
        return R.ok();
    }

    /** 换绑手机号 / 邮箱：验证码发送到新联系方式，不记录密码与验证码。 */
    @Log(title = "会员个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PostMapping("/contact/code")
    public R<Void> sendContactCode(@Valid @RequestBody MemberContactCodeRequest request) {
        contactService.sendCode(MemberLoginHelper.getMemberId(), request);
        return R.ok();
    }

    /** 换绑手机号 / 邮箱：校验验证码并返回最新资料。 */
    @Log(title = "会员个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/contact")
    public R<MemberInfoVO> changeContact(@Valid @RequestBody MemberContactChangeRequest request) {
        contactService.change(MemberLoginHelper.getMemberId(), request);
        return R.ok(userService.getInfo());
    }

    /** 头像上传：图片进文件中心，会员表只存 fileId，返回最新资料。 */
    @Log(title = "会员个人中心", businessType = BusinessType.UPDATE)
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<MemberInfoVO> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return R.ok(avatarService.upload(MemberLoginHelper.getMemberId(), file));
    }

    /** 登录设备列表；只返回会话句柄，不返回 Token。 */
    @GetMapping("/sessions")
    public R<List<MemberSessionVO>> sessions() {
        return R.ok(sessionService.list(MemberLoginHelper.getMemberId()));
    }

    /** 退出指定设备；当前设备不允许在这里踢，提示用“退出登录”。 */
    @Log(title = "会员个人中心", businessType = BusinessType.DELETE)
    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> kickSession(@PathVariable String sessionId) {
        sessionService.kick(MemberLoginHelper.getMemberId(), sessionId);
        return R.ok();
    }

    /** 退出除当前设备外的所有会话。 */
    @Log(title = "会员个人中心", businessType = BusinessType.DELETE)
    @DeleteMapping("/sessions/others")
    public R<Integer> kickOtherSessions() {
        return R.ok(sessionService.kickOthers(MemberLoginHelper.getMemberId()));
    }
}
