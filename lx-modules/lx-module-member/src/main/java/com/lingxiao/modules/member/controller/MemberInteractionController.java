package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.modules.member.dto.MemberFavoriteVO;
import com.lingxiao.modules.member.dto.MemberInteractionVO;
import com.lingxiao.modules.member.security.MemberLoginHelper;
import com.lingxiao.modules.member.service.MemberInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员互动接口（{@code /api/app/**}）。
 *
 * <p><b>Token 域：</b>本控制器只接受会员 Token（{@code lx-member-token}），
 * 会员 ID 一律从会员 Session 读取，不接受前端传入；平台后台 Token
 * （{@code lx-platform-token}）访问这里会因域不同返回 401。</p>
 *
 * <p><b>公开读：</b>{@code /api/app/public/interactions/**} 只读统计，
 * 不要求登录；带会员 Token 时额外返回当前会员的收藏 / 点赞状态。
 * 写操作仍在 {@code /api/app/interactions/**} 下，必须登录。</p>
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberInteractionController {

    private final MemberInteractionService interactionService;

    /** 文章互动统计；匿名可读，登录时附带个人状态。 */
    @GetMapping("/public/interactions/articles/{articleId}")
    public R<MemberInteractionVO> stats(@PathVariable Long articleId) {
        return R.ok(interactionService.stats(articleId, MemberLoginHelper.getMemberId()));
    }

    /** 我的收藏分页。 */
    @GetMapping("/interactions/favorites")
    public R<PageResult<MemberFavoriteVO>> favorites(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(interactionService.pageFavorites(MemberLoginHelper.getMemberId(), pageNum, pageSize));
    }

    @PutMapping("/interactions/articles/{articleId}/favorite")
    public R<MemberInteractionVO> favorite(@PathVariable Long articleId) {
        return R.ok(interactionService.favorite(MemberLoginHelper.getMemberId(), articleId));
    }

    @DeleteMapping("/interactions/articles/{articleId}/favorite")
    public R<MemberInteractionVO> unfavorite(@PathVariable Long articleId) {
        return R.ok(interactionService.unfavorite(MemberLoginHelper.getMemberId(), articleId));
    }

    @PutMapping("/interactions/articles/{articleId}/like")
    public R<MemberInteractionVO> like(@PathVariable Long articleId) {
        return R.ok(interactionService.like(MemberLoginHelper.getMemberId(), articleId));
    }

    @DeleteMapping("/interactions/articles/{articleId}/like")
    public R<MemberInteractionVO> unlike(@PathVariable Long articleId) {
        return R.ok(interactionService.unlike(MemberLoginHelper.getMemberId(), articleId));
    }
}
