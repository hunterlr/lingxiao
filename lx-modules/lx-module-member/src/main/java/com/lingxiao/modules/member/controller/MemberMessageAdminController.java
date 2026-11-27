package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.member.dto.MemberMessageAdminVO;
import com.lingxiao.modules.member.dto.MemberMessageSendRequest;
import com.lingxiao.modules.member.service.MemberMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 后台消息管理（{@code /api/admin/member/message/**}）。
 *
 * <p>只接受平台后台 Token；权限码 {@code platform:member:message:*}。
 * 消息开关关闭时后台仍可查看历史与清理，但门户不再展示入口。</p>
 */
@RestController
@RequestMapping("/api/admin/member/message")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberMessageAdminController {

    private final MemberMessageService messageService;

    @RequiresPermission("platform:member:message:list")
    @GetMapping("/list")
    public R<PageResult<MemberMessageAdminVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "20") long pageSize,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String readFlag) {
        return R.ok(messageService.pageAdmin(pageNum, pageSize, keyword, readFlag));
    }

    @RequiresPermission("platform:member:message:send")
    @Log(title = "消息管理", businessType = BusinessType.INSERT)
    @PostMapping("/send")
    public R<Integer> send(@Valid @RequestBody MemberMessageSendRequest request) {
        return R.ok(messageService.send(request.getMemberIds(), request.getTitle(),
                request.getContent(), LoginHelper.getUserId()));
    }

    @RequiresPermission("platform:member:message:remove")
    @Log(title = "消息管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{msgId}")
    public R<Void> remove(@PathVariable Long msgId) {
        messageService.delete(msgId);
        return R.ok();
    }
}