package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberMessageAdminVO;
import com.lingxiao.modules.member.dto.MemberMessageVO;
import com.lingxiao.modules.member.entity.MemMessage;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.mapper.MemMessageMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 会员站内消息服务。
 *
 * <p><b>两种来源：</b>评论审核结果由 {@link #notifyCommentAudited} 自动写；
 * 其余通知由后台“会员管理 → 消息管理”手动发送。两类消息共用一张表，
 * 用 {@code msgType} 区分。</p>
 *
 * <p><b>跨模块边界：</b>只存 {@code bizType + bizId}，不查 CMS 表、不建外键，
 * 也不复制文章标题；门户要展示文章标题时用公开接口补。</p>
 *
 * <p><b>开关：</b>门户接口由 {@code MemberMessageEnabledInterceptor} 拦截；
 * 本服务在自动通知前也判断一次 {@code mem.message.enabled}，关闭后不再产生新消息，
 * 但历史消息仍可读（后台不受影响）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberMessageService {

    public static final String TYPE_COMMENT_APPROVED = "comment_approved";
    public static final String TYPE_COMMENT_REJECTED = "comment_rejected";
    public static final String TYPE_SYSTEM = "system";

    public static final String READ_NO = "0";
    public static final String READ_YES = "1";

    /** 消息中心开关参数键，与拦截器共用。 */
    public static final String SWITCH_KEY = "mem.message.enabled";

    private static final String BIZ_ARTICLE = "article";
    private static final long DEFAULT_TENANT_ID = 0L;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_SEND_MEMBERS = 200;
    /** 评论摘要进消息时截断，避免一条站内信塞满正文。 */
    private static final int COMMENT_SNIPPET_LENGTH = 60;
    private static final long MAX_PAGE_SIZE = 50;
    private static final long MAX_ADMIN_PAGE_SIZE = 100;

    private final MemMessageMapper messageMapper;
    private final MemUserMapper userMapper;
    private final ConfigProvider configProvider;

    /** 我的消息分页，按时间倒序。 */
    public PageResult<MemberMessageVO> pageMine(Long memberId, long pageNum, long pageSize) {
        Long currentMemberId = requireMemberId(memberId);
        Page<MemMessage> page = new Page<>(Math.max(pageNum, 1),
                Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE));
        Page<MemMessage> result = messageMapper.selectPage(page, new LambdaQueryWrapper<MemMessage>()
                .eq(MemMessage::getMemberId, currentMemberId)
                .orderByDesc(MemMessage::getCreateTime)
                .orderByDesc(MemMessage::getMsgId));
        List<MemberMessageVO> records = result.getRecords().stream()
                .map(this::toMemberVO)
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 未读数；门户页头角标用。 */
    public long unreadCount(Long memberId) {
        Long currentMemberId = requireMemberId(memberId);
        return messageMapper.selectCount(new LambdaQueryWrapper<MemMessage>()
                .eq(MemMessage::getMemberId, currentMemberId)
                .eq(MemMessage::getReadFlag, READ_NO));
    }

    /** 标记单条已读；只能标记自己的消息，已读时直接返回成功（幂等）。 */
    @Transactional
    public void markRead(Long memberId, Long msgId) {
        Long currentMemberId = requireMemberId(memberId);
        MemMessage existing = mustGetOwn(currentMemberId, msgId);
        if (READ_YES.equals(existing.getReadFlag())) {
            return;
        }
        messageMapper.update(null, new LambdaUpdateWrapper<MemMessage>()
                .eq(MemMessage::getMsgId, existing.getMsgId())
                .eq(MemMessage::getReadFlag, READ_NO)
                .set(MemMessage::getReadFlag, READ_YES)
                .set(MemMessage::getReadTime, LocalDateTime.now()));
    }

    /** 全部标记已读；没有未读时同样成功。 */
    @Transactional
    public void markAllRead(Long memberId) {
        Long currentMemberId = requireMemberId(memberId);
        messageMapper.update(null, new LambdaUpdateWrapper<MemMessage>()
                .eq(MemMessage::getMemberId, currentMemberId)
                .eq(MemMessage::getReadFlag, READ_NO)
                .set(MemMessage::getReadFlag, READ_YES)
                .set(MemMessage::getReadTime, LocalDateTime.now()));
    }

    /** 后台消息列表：支持标题 / 正文 / 会员关键字与已读状态筛选。 */
    public PageResult<MemberMessageAdminVO> pageAdmin(long pageNum, long pageSize,
                                                     String keyword, String readFlag) {
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

        LambdaQueryWrapper<MemMessage> wrapper = new LambdaQueryWrapper<MemMessage>()
                .and(StringUtils.hasText(keyword), w -> {
                    w.like(MemMessage::getTitle, keyword.trim())
                            .or().like(MemMessage::getContent, keyword.trim());
                    if (!memberIds.isEmpty()) {
                        w.or().in(MemMessage::getMemberId, memberIds);
                    }
                })
                .eq(StringUtils.hasText(readFlag), MemMessage::getReadFlag, readFlag)
                .orderByDesc(MemMessage::getCreateTime)
                .orderByDesc(MemMessage::getMsgId);

        Page<MemMessage> page = new Page<>(Math.max(pageNum, 1),
                Math.min(Math.max(pageSize, 1), MAX_ADMIN_PAGE_SIZE));
        Page<MemMessage> result = messageMapper.selectPage(page, wrapper);
        Map<Long, MemUser> members = memberMap(result.getRecords());
        List<MemberMessageAdminVO> records = result.getRecords().stream()
                .map(item -> toAdminVO(item, members.get(item.getMemberId())))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 后台手动发送；逐个会员写一条，返回实际写入条数。 */
    @Transactional
    public int send(List<Long> memberIds, String title, String content, Long operatorId) {
        List<Long> targets = normalizeMemberIds(memberIds);
        String normalizedTitle = normalizeTitle(title);
        String normalizedContent = normalizeContent(content);

        List<MemUser> users = userMapper.selectBatchIds(targets);
        Map<Long, MemUser> existing = users.stream()
                .collect(Collectors.toMap(MemUser::getMemberId, item -> item, (a, b) -> a));
        if (existing.size() != targets.size()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.message.member.missing");
        }

        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        for (Long memberId : targets) {
            MemMessage message = new MemMessage();
            message.setTenantId(DEFAULT_TENANT_ID);
            message.setMemberId(memberId);
            message.setMsgType(TYPE_SYSTEM);
            message.setTitle(normalizedTitle);
            message.setContent(normalizedContent);
            message.setReadFlag(READ_NO);
            message.setCreateBy(operatorId);
            message.setCreateTime(now);
            messageMapper.insert(message);
            created++;
        }
        log.info("后台发送站内消息: members={}, title={}, operator={}", created, normalizedTitle, operatorId);
        return created;
    }

    /** 后台逻辑删除；已删除或不存在时返回 1004。 */
    @Transactional
    public void delete(Long msgId) {
        MemMessage existing = msgId == null ? null : messageMapper.selectById(msgId);
        if (existing == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.message");
        }
        messageMapper.deleteById(existing.getMsgId());
        log.info("后台删除站内消息: msgId={}", existing.getMsgId());
    }

    /**
     * 评论审核结果通知。
     *
     * <p>只在状态真正变化时发：重复点“通过”不会重复通知；开关关闭时直接跳过，
     * 避免把消息写进一个已经关掉的功能里。</p>
     *
     * <p>审核备注不写进消息：后台审核界面对运营的承诺是“备注不会展示给会员”，
     * 站内信属于会员可见内容，因此只给结果与评论摘要。</p>
     */
    @Transactional
    public void notifyCommentAudited(Long memberId, Long articleId, String status,
                                     String commentContent, String previousStatus) {
        if (memberId == null || Objects.equals(status, previousStatus)) {
            return;
        }
        if (!configProvider.getBooleanValue(SWITCH_KEY, true)) {
            return;
        }
        boolean approved = "1".equals(status);
        if (!approved && !"2".equals(status)) {
            return;
        }
        String snippet = snippet(commentContent);
        String title = approved ? "评论已通过审核" : "评论未通过审核";
        StringBuilder content = new StringBuilder();
        if (approved) {
            content.append("你的评论已通过审核，现在可以在文章页看到。");
        } else {
            content.append("你的评论未通过审核，可修改后重新提交。");
        }
        if (StringUtils.hasText(snippet)) {
            content.append("\n评论内容：").append(snippet);
        }

        MemMessage message = new MemMessage();
        message.setTenantId(DEFAULT_TENANT_ID);
        message.setMemberId(memberId);
        message.setMsgType(approved ? TYPE_COMMENT_APPROVED : TYPE_COMMENT_REJECTED);
        message.setTitle(title);
        message.setContent(truncate(content.toString(), MAX_CONTENT_LENGTH));
        message.setBizType(BIZ_ARTICLE);
        message.setBizId(articleId);
        message.setReadFlag(READ_NO);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        log.info("评论审核通知: memberId={}, articleId={}, status={}", memberId, articleId, status);
    }

    private MemMessage mustGetOwn(Long memberId, Long msgId) {
        MemMessage message = msgId == null ? null : messageMapper.selectById(msgId);
        if (message == null || !memberId.equals(message.getMemberId())) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member.message");
        }
        return message;
    }

    private Map<Long, MemUser> memberMap(List<MemMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> memberIds = messages.stream()
                .map(MemMessage::getMemberId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(memberIds).stream()
                .collect(Collectors.toMap(MemUser::getMemberId, item -> item, (a, b) -> a));
    }

    private MemberMessageVO toMemberVO(MemMessage message) {
        MemberMessageVO vo = new MemberMessageVO();
        vo.setMsgId(message.getMsgId());
        vo.setMsgType(message.getMsgType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setBizType(message.getBizType());
        vo.setBizId(message.getBizId());
        vo.setReadFlag(message.getReadFlag());
        vo.setReadTime(message.getReadTime());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private MemberMessageAdminVO toAdminVO(MemMessage message, MemUser user) {
        MemberMessageAdminVO vo = new MemberMessageAdminVO();
        vo.setMsgId(message.getMsgId());
        vo.setMsgType(message.getMsgType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setBizType(message.getBizType());
        vo.setBizId(message.getBizId());
        vo.setReadFlag(message.getReadFlag());
        vo.setReadTime(message.getReadTime());
        vo.setCreateTime(message.getCreateTime());
        vo.setMemberId(message.getMemberId());
        vo.setUsername(user == null ? null : user.getUsername());
        vo.setNickname(user == null ? "已注销会员" : user.getNickname());
        return vo;
    }

    private List<Long> normalizeMemberIds(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.message.member.required");
        }
        LinkedHashSet<Long> unique = memberIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.message.member.required");
        }
        if (unique.size() > MAX_SEND_MEMBERS) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.message.member.max");
        }
        return unique.stream().collect(Collectors.toList());
    }

    private String normalizeTitle(String title) {
        String text = title == null ? "" : title.trim();
        if (text.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.message.title.required");
        }
        if (text.length() > MAX_TITLE_LENGTH) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.message.title.max");
        }
        return text;
    }

    private String normalizeContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.message.content.required");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "valid.message.content.max");
        }
        return text;
    }

    private String snippet(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= COMMENT_SNIPPET_LENGTH
                ? trimmed
                : trimmed.substring(0, COMMENT_SNIPPET_LENGTH) + "…";
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private Long requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        return memberId;
    }
}