package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.modules.member.dto.MemberMessageVO;
import com.lingxiao.modules.member.security.MemberLoginHelper;
import com.lingxiao.modules.member.service.MemberMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员站内消息接口（{@code /api/app/messages/**}）。
 *
 * <p>全部需要登录；会员 ID 只从会员 Session 读取，不接受前端传。
 * {@code mem.message.enabled=false} 时由 {@code MemberMessageEnabledInterceptor}
 * 统一返回 1003，避免页面一个接口能用、一个不能用。</p>
 */
@RestController
@RequestMapping("/api/app/messages")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberMessageController {

    private final MemberMessageService messageService;

    @GetMapping
    public R<PageResult<MemberMessageVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(messageService.pageMine(MemberLoginHelper.getMemberId(), pageNum, pageSize));
    }

    /** 未读数，门户页头角标用。 */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(messageService.unreadCount(MemberLoginHelper.getMemberId()));
    }

    @PutMapping("/{msgId}/read")
    public R<Void> read(@PathVariable Long msgId) {
        messageService.markRead(MemberLoginHelper.getMemberId(), msgId);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> readAll() {
        messageService.markAllRead(MemberLoginHelper.getMemberId());
        return R.ok();
    }
}