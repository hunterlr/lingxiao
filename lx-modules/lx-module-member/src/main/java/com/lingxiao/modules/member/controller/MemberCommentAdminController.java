package com.lingxiao.modules.member.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.member.dto.MemberCommentAdminVO;
import com.lingxiao.modules.member.dto.MemberCommentAuditRequest;
import com.lingxiao.modules.member.service.MemberCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 后台评论审核（{@code /api/admin/member/comment/**}）。
 *
 * <p>只接受平台后台 Token；权限码 {@code platform:member:comment:*}。
 * 评论运行期开关关闭时，后台审核接口仍然可用，便于清理历史评论。</p>
 */
@RestController
@RequestMapping("/api/admin/member/comment")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberCommentAdminController {

    private final MemberCommentService commentService;

    @RequiresPermission("platform:member:comment:list")
    @GetMapping("/list")
    public R<PageResult<MemberCommentAdminVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "20") long pageSize,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long articleId) {
        return R.ok(commentService.pageAdmin(pageNum, pageSize, keyword, status, articleId));
    }

    @RequiresPermission("platform:member:comment:audit")
    @Log(title = "评论管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{commentId}/audit")
    public R<Void> audit(@PathVariable Long commentId,
                         @Valid @RequestBody MemberCommentAuditRequest request) {
        commentService.audit(commentId, request.getStatus(), request.getRemark(), LoginHelper.getUserId());
        return R.ok();
    }

    @RequiresPermission("platform:member:comment:remove")
    @Log(title = "评论管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{commentId}")
    public R<Void> remove(@PathVariable Long commentId) {
        commentService.delete(commentId);
        return R.ok();
    }
}
