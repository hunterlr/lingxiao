package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.modules.member.dto.MemberCommentCreateRequest;
import com.lingxiao.modules.member.dto.MemberCommentVO;
import com.lingxiao.modules.member.security.MemberLoginHelper;
import com.lingxiao.modules.member.service.MemberCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 会员评论接口（{@code /api/app/**}）。
 *
 * <p><b>公开读：</b>{@code GET /api/app/public/comments/articles/{articleId}}
 * 匿名可读，只返回已审核通过的评论。</p>
 *
 * <p><b>写操作：</b>{@code POST /api/app/comments/articles/{articleId}}
 * 必须登录，会员 ID 只从会员 Session 读取。提交后进入待审核状态。</p>
 *
 * <p>{@code mem.comment.enabled=false} 时，本控制器的公开读与写接口都由
 * {@code MemberCommentEnabledInterceptor} 返回 1003。</p>
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberCommentController {

    private final MemberCommentService commentService;

    /** 文章已通过审核的评论分页；匿名可读。 */
    @GetMapping("/public/comments/articles/{articleId}")
    public R<PageResult<MemberCommentVO>> list(@PathVariable Long articleId,
                                               @RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(commentService.pageApproved(articleId, pageNum, pageSize));
    }

    /** 发表评论；默认待审核，审核通过后才出现在公开列表。 */
    @PostMapping("/comments/articles/{articleId}")
    public R<MemberCommentVO> create(@PathVariable Long articleId,
                                     @Valid @RequestBody MemberCommentCreateRequest request) {
        return R.ok(commentService.create(MemberLoginHelper.getMemberId(), articleId, request.getContent()));
    }
}
