package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.web.util.ServletUtils;
import com.lingxiao.modules.member.dto.MemberCommentAdminVO;
import com.lingxiao.modules.member.dto.MemberCommentVO;
import com.lingxiao.modules.member.entity.MemComment;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemCommentMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 会员评论服务。
 *
 * <p><b>先审后发：</b>新评论一律 {@code status=0 待审核}，后台通过后才进入
 * 门户公开列表；拒绝保留记录与备注，便于追溯。</p>
 *
 * <p><b>跨模块边界：</b>只存 {@code article_id}，不查 {@code cms_article}、
 * 不建外键。文章是否可见由 CMS 门户接口负责，评论接口只校验 ID 为正数。</p>
 *
 * <p><b>运行期开关：</b>{@code mem.comment.enabled} 由
 * {@code MemberCommentEnabledInterceptor} 在接口层拦截；本服务不重复判断，
 * 后台审核接口不受该开关影响，关闭后仍可清理历史评论。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberCommentService {

    public static final String STATUS_PENDING = "0";
    public static final String STATUS_APPROVED = "1";
    public static final String STATUS_REJECTED = "2";

    private static final long DEFAULT_TENANT_ID = 0L;
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final long MAX_PAGE_SIZE = 50;

    private final MemCommentMapper commentMapper;
    private final MemUserMapper userMapper;

    /** 门户公开列表：只返回已通过评论，按时间倒序。 */
    public PageResult<MemberCommentVO> pageApproved(Long articleId, long pageNum, long pageSize) {
        Long currentArticleId = requireArticleId(articleId);
        Page<MemComment> page = new Page<>(Math.max(pageNum, 1), normalizePageSize(pageSize));
        Page<MemComment> result = commentMapper.selectPage(page, new LambdaQueryWrapper<MemComment>()
                .eq(MemComment::getArticleId, currentArticleId)
                .eq(MemComment::getStatus, STATUS_APPROVED)
                .orderByDesc(MemComment::getCreateTime)
                .orderByDesc(MemComment::getCommentId));
        Map<Long, MemUser> members = memberMap(result.getRecords());
        List<MemberCommentVO> records = result.getRecords().stream()
                .map(item -> toMemberVO(item, members.get(item.getMemberId())))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 会员发表评论：默认待审核，返回刚写入的记录供前端提示。 */
    @Transactional
    public MemberCommentVO create(Long memberId, Long articleId, String content) {
        Long currentMemberId = requireMemberId(memberId);
        Long currentArticleId = requireArticleId(articleId);
        String text = normalizeContent(content);

        MemComment comment = new MemComment();
        comment.setTenantId(DEFAULT_TENANT_ID);
        comment.setArticleId(currentArticleId);
        comment.setMemberId(currentMemberId);
        comment.setContent(text);
        comment.setStatus(STATUS_PENDING);
        comment.setCreateIp(ServletUtils.getClientIp());
        commentMapper.insert(comment);

        MemUser user = userMapper.selectById(currentMemberId);
        log.info("会员发表评论: commentId={}, memberId={}, articleId={}",
                comment.getCommentId(), currentMemberId, currentArticleId);
        return toMemberVO(comment, user);
    }

    /** 后台评论列表：支持内容 / 会员关键字、状态与文章筛选。 */
    public PageResult<MemberCommentAdminVO> pageAdmin(long pageNum, long pageSize,
                                                     String keyword, String status, Long articleId) {
        List<Long> memberIds = StringUtils.hasText(keyword)
                ? userMapper.selectList(new LambdaQueryWrapper<MemUser>()
                        .select(MemUser::getMemberId)
                        .and(w -> w.like(MemUser::getUsername, keyword.trim())
                                .or().like(MemUser::getNickname, keyword.trim())))
                        .stream()
                        .map(MemUser::getMemberId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        LambdaQueryWrapper<MemComment> wrapper = new LambdaQueryWrapper<MemComment>()
                .and(StringUtils.hasText(keyword), w -> {
                    w.like(MemComment::getContent, keyword.trim());
                    if (!memberIds.isEmpty()) {
                        w.or().in(MemComment::getMemberId, memberIds);
                    }
                })
                .eq(StringUtils.hasText(status), MemComment::getStatus, status)
                .eq(articleId != null && articleId > 0, MemComment::getArticleId, articleId)
                .orderByAsc(MemComment::getStatus)
                .orderByDesc(MemComment::getCreateTime)
                .orderByDesc(MemComment::getCommentId);

        Page<MemComment> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        Page<MemComment> result = commentMapper.selectPage(page, wrapper);
        Map<Long, MemUser> members = memberMap(result.getRecords());
        List<MemberCommentAdminVO> records = result.getRecords().stream()
                .map(item -> toAdminVO(item, members.get(item.getMemberId())))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 后台审核：只接受已通过 / 已拒绝；重复审核覆盖上一次结果。 */
    @Transactional
    public void audit(Long commentId, String status, String remark, Long operatorId) {
        if (!STATUS_APPROVED.equals(status) && !STATUS_REJECTED.equals(status)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "审核状态只能是已通过或已拒绝");
        }
        MemComment existing = mustGet(commentId);
        commentMapper.update(null, new LambdaUpdateWrapper<MemComment>()
                .eq(MemComment::getCommentId, existing.getCommentId())
                .set(MemComment::getStatus, status)
                .set(MemComment::getAuditBy, operatorId)
                .set(MemComment::getAuditTime, LocalDateTime.now())
                .set(MemComment::getAuditRemark, trimToNull(remark)));
        log.info("后台审核评论: commentId={}, status={}, operator={}",
                existing.getCommentId(), status, operatorId);
    }

    /** 后台逻辑删除；已删除或不存在时返回 1004。 */
    @Transactional
    public void delete(Long commentId) {
        MemComment existing = mustGet(commentId);
        commentMapper.deleteById(existing.getCommentId());
        log.info("后台删除评论: commentId={}", existing.getCommentId());
    }

    private MemComment mustGet(Long commentId) {
        MemComment comment = commentId == null ? null : commentMapper.selectById(commentId);
        if (comment == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.comment");
        }
        return comment;
    }

    private Map<Long, MemUser> memberMap(List<MemComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> memberIds = comments.stream()
                .map(MemComment::getMemberId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(memberIds).stream()
                .collect(Collectors.toMap(MemUser::getMemberId, item -> item, (a, b) -> a));
    }

    private MemberCommentVO toMemberVO(MemComment comment, MemUser user) {
        MemberCommentVO vo = new MemberCommentVO();
        vo.setCommentId(comment.getCommentId());
        vo.setArticleId(comment.getArticleId());
        vo.setContent(comment.getContent());
        vo.setCreateTime(comment.getCreateTime());
        vo.setNickname(user == null ? "会员" : user.getNickname());
        vo.setAvatar(user == null ? null : user.getAvatar());
        return vo;
    }

    private MemberCommentAdminVO toAdminVO(MemComment comment, MemUser user) {
        MemberCommentAdminVO vo = new MemberCommentAdminVO();
        vo.setCommentId(comment.getCommentId());
        vo.setArticleId(comment.getArticleId());
        vo.setMemberId(comment.getMemberId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setNickname(user == null ? "已注销会员" : user.getNickname());
        vo.setAvatar(user == null ? null : user.getAvatar());
        vo.setContent(comment.getContent());
        vo.setStatus(comment.getStatus());
        vo.setCreateTime(comment.getCreateTime());
        vo.setAuditTime(comment.getAuditTime());
        vo.setAuditRemark(comment.getAuditRemark());
        return vo;
    }

    private String normalizeContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.comment.content.required");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.comment.content.max");
        }
        return text;
    }

    private Long requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        return memberId;
    }

    private Long requireArticleId(Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "文章不存在");
        }
        return articleId;
    }

    private long normalizePageSize(long pageSize) {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
