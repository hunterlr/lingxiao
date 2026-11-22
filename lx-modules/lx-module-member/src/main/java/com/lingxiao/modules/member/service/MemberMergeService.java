package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.member.dto.MemberMergePreviewVO;
import com.lingxiao.modules.member.dto.MemberMergeRequest;
import com.lingxiao.modules.member.dto.MemberMergeResultVO;
import com.lingxiao.modules.member.entity.MemComment;
import com.lingxiao.modules.member.entity.MemFavorite;
import com.lingxiao.modules.member.entity.MemLike;
import com.lingxiao.modules.member.entity.MemMergeLog;
import com.lingxiao.modules.member.entity.MemUser;
import com.lingxiao.modules.member.entity.MemUserSocial;
import com.lingxiao.modules.member.mapper.MemCommentMapper;
import com.lingxiao.modules.member.mapper.MemFavoriteMapper;
import com.lingxiao.modules.member.mapper.MemLikeMapper;
import com.lingxiao.modules.member.mapper.MemMergeLogMapper;
import com.lingxiao.modules.member.mapper.MemUserMapper;
import com.lingxiao.modules.member.mapper.MemUserSocialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 后台会员账号合并。
 *
 * <p><b>使用场景：</b>历史遗留的同邮箱 / 同手机号重复账号。第三方登录只做
 * “验证后绑定”，不会静默合并；确实需要合并时由管理员在后台确认执行。</p>
 *
 * <p><b>合并语义：</b>把 {@code source}（旧账号）的收藏、点赞、评论与第三方绑定
 * 迁移到 {@code target}（保留账号），按文章 / 平台去重；随后停用旧账号并踢掉它的
 * 全部会话。旧账号不物理删除，保留审计与回滚依据。</p>
 *
 * <p>整个过程在一个事务里完成；迁移前对两个会员行加 {@code FOR UPDATE}，
 * 避免两个管理员同时合并同一组账号造成数据重复。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberMergeService {

    private static final String STATUS_DISABLED = "1";
    /** 与 mem_user.remark / mem_merge_log.remark 的列宽一致 */
    private static final int REMARK_MAX = 500;

    private final MemUserMapper userMapper;
    private final MemFavoriteMapper favoriteMapper;
    private final MemLikeMapper likeMapper;
    private final MemCommentMapper commentMapper;
    private final MemUserSocialMapper socialMapper;
    private final MemMergeLogMapper mergeLogMapper;
    private final MemberTokenIssuer tokenIssuer;

    /** 只读预览；不修改任何数据，后台据此二次确认。 */
    public MemberMergePreviewVO preview(Long sourceMemberId, Long targetMemberId) {
        MemUser source = mustGet(sourceMemberId);
        MemUser target = mustGet(targetMemberId);
        validate(source, target);

        MemberMergePreviewVO vo = new MemberMergePreviewVO();
        fillMember(vo, source, target);

        Set<Long> sourceFavorites = favoriteArticles(sourceMemberId);
        Set<Long> targetFavorites = favoriteArticles(targetMemberId);
        vo.setFavoriteSource(sourceFavorites.size());
        vo.setFavoriteTarget(targetFavorites.size());
        vo.setFavoriteOverlap(intersectSize(sourceFavorites, targetFavorites));

        Set<Long> sourceLikes = likeArticles(sourceMemberId);
        Set<Long> targetLikes = likeArticles(targetMemberId);
        vo.setLikeSource(sourceLikes.size());
        vo.setLikeTarget(targetLikes.size());
        vo.setLikeOverlap(intersectSize(sourceLikes, targetLikes));

        vo.setCommentSource(countComments(sourceMemberId));

        List<MemUserSocial> sourceSocial = socials(sourceMemberId);
        List<MemUserSocial> targetSocial = socials(targetMemberId);
        vo.setSocialSource(sourceSocial.size());
        vo.setSocialTarget(targetSocial.size());
        for (MemUserSocial row : sourceSocial) {
            vo.getSocialSourceProviders().add(row.getProvider());
        }
        Set<String> targetProviders = new HashSet<>();
        for (MemUserSocial row : targetSocial) {
            vo.getSocialTargetProviders().add(row.getProvider());
            targetProviders.add(row.getProvider());
        }
        long overlap = 0;
        for (MemUserSocial row : sourceSocial) {
            if (targetProviders.contains(row.getProvider())) {
                overlap++;
            }
        }
        vo.setSocialOverlap(overlap);
        return vo;
    }

    /** 执行合并；结果写入 {@code mem_merge_log}。 */
    @Transactional(rollbackFor = Exception.class)
    public MemberMergeResultVO merge(MemberMergeRequest request) {
        Long sourceMemberId = request.getSourceMemberId();
        Long targetMemberId = request.getTargetMemberId();
        if (sourceMemberId == null || targetMemberId == null) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.mergeSame");
        }
        if (sourceMemberId.equals(targetMemberId)) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.mergeSame");
        }

        // 锁定两行后再校验，避免并发合并把同一批数据迁移两次
        List<MemUser> locked = userMapper.selectList(new LambdaQueryWrapper<MemUser>()
                .in(MemUser::getMemberId, Arrays.asList(sourceMemberId, targetMemberId))
                .last("FOR UPDATE"));
        MemUser source = pick(locked, sourceMemberId);
        MemUser target = pick(locked, targetMemberId);
        if (source == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member");
        }
        if (target == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member");
        }
        validate(source, target);

        LocalDateTime now = LocalDateTime.now();
        Long operatorId = operatorId();

        MemberMergeResultVO result = new MemberMergeResultVO();
        result.setSourceMemberId(sourceMemberId);
        result.setTargetMemberId(targetMemberId);

        // 1. 收藏：按 articleId 去重后迁移
        int favoriteMoved = 0;
        int favoriteSkipped = 0;
        List<MemFavorite> sourceFavorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<MemFavorite>().eq(MemFavorite::getMemberId, sourceMemberId));
        Set<Long> targetFavoriteArticles = favoriteArticles(targetMemberId);
        for (MemFavorite row : sourceFavorites) {
            if (targetFavoriteArticles.contains(row.getArticleId())) {
                favoriteSkipped++;
                continue;
            }
            MemFavorite copy = new MemFavorite();
            copy.setTenantId(row.getTenantId());
            copy.setMemberId(targetMemberId);
            copy.setArticleId(row.getArticleId());
            copy.setCreateTime(row.getCreateTime() == null ? now : row.getCreateTime());
            favoriteMapper.insert(copy);
            targetFavoriteArticles.add(row.getArticleId());
            favoriteMoved++;
        }
        favoriteMapper.delete(new LambdaQueryWrapper<MemFavorite>()
                .eq(MemFavorite::getMemberId, sourceMemberId));
        result.setFavoriteMoved(favoriteMoved);
        result.setFavoriteSkipped(favoriteSkipped);

        // 2. 点赞：与收藏同样的去重规则
        int likeMoved = 0;
        int likeSkipped = 0;
        List<MemLike> sourceLikes = likeMapper.selectList(
                new LambdaQueryWrapper<MemLike>().eq(MemLike::getMemberId, sourceMemberId));
        Set<Long> targetLikeArticles = likeArticles(targetMemberId);
        for (MemLike row : sourceLikes) {
            if (targetLikeArticles.contains(row.getArticleId())) {
                likeSkipped++;
                continue;
            }
            MemLike copy = new MemLike();
            copy.setTenantId(row.getTenantId());
            copy.setMemberId(targetMemberId);
            copy.setArticleId(row.getArticleId());
            copy.setCreateTime(row.getCreateTime() == null ? now : row.getCreateTime());
            likeMapper.insert(copy);
            targetLikeArticles.add(row.getArticleId());
            likeMoved++;
        }
        likeMapper.delete(new LambdaQueryWrapper<MemLike>()
                .eq(MemLike::getMemberId, sourceMemberId));
        result.setLikeMoved(likeMoved);
        result.setLikeSkipped(likeSkipped);

        // 3. 评论：直接改归属；逻辑删除的评论由 MyBatis-Plus 自动排除
        int commentMoved = commentMapper.update(null, new LambdaUpdateWrapper<MemComment>()
                .eq(MemComment::getMemberId, sourceMemberId)
                .set(MemComment::getMemberId, targetMemberId)
                .set(MemComment::getUpdateTime, now));
        result.setCommentMoved(commentMoved);

        // 4. 第三方绑定：同一平台只保留目标账号的一条
        int socialMoved = 0;
        int socialSkipped = 0;
        List<MemUserSocial> sourceSocial = socials(sourceMemberId);
        Set<String> targetProviders = new HashSet<>();
        for (MemUserSocial row : socials(targetMemberId)) {
            targetProviders.add(row.getProvider());
        }
        for (MemUserSocial row : sourceSocial) {
            if (targetProviders.contains(row.getProvider())) {
                // 目标账号已绑定同一平台：删掉旧账号的，避免违反唯一键
                socialMapper.deleteById(row.getSocialId());
                socialSkipped++;
                continue;
            }
            socialMapper.update(null, new LambdaUpdateWrapper<MemUserSocial>()
                    .eq(MemUserSocial::getSocialId, row.getSocialId())
                    .set(MemUserSocial::getMemberId, targetMemberId)
                    .set(MemUserSocial::getUpdateTime, now));
            targetProviders.add(row.getProvider());
            socialMoved++;
        }
        result.setSocialMoved(socialMoved);
        result.setSocialSkipped(socialSkipped);

        // 5. 停用旧账号并踢会话；不物理删除，保留历史数据指向
        userMapper.update(null, new LambdaUpdateWrapper<MemUser>()
                .eq(MemUser::getMemberId, sourceMemberId)
                .set(MemUser::getStatus, STATUS_DISABLED)
                .set(MemUser::getRemark, mergeRemark(target, request.getRemark()))
                .set(MemUser::getUpdateBy, operatorId)
                .set(MemUser::getUpdateTime, now));
        tokenIssuer.logoutAll(sourceMemberId);

        // 6. 审计
        MemMergeLog mergeLog = new MemMergeLog();
        mergeLog.setTenantId(source.getTenantId() == null ? 0L : source.getTenantId());
        mergeLog.setSourceMemberId(sourceMemberId);
        mergeLog.setTargetMemberId(targetMemberId);
        mergeLog.setSourceUsername(source.getUsername());
        mergeLog.setTargetUsername(target.getUsername());
        mergeLog.setFavoriteMoved(favoriteMoved);
        mergeLog.setFavoriteSkipped(favoriteSkipped);
        mergeLog.setLikeMoved(likeMoved);
        mergeLog.setLikeSkipped(likeSkipped);
        mergeLog.setCommentMoved(commentMoved);
        mergeLog.setSocialMoved(socialMoved);
        mergeLog.setSocialSkipped(socialSkipped);
        mergeLog.setOperatorId(operatorId);
        mergeLog.setRemark(truncate(trimToNull(request.getRemark()), REMARK_MAX));
        mergeLog.setCreateTime(now);
        mergeLogMapper.insert(mergeLog);
        result.setMergeId(mergeLog.getMergeId());

        log.info("后台合并会员账号: source={}, target={}, operator={}, favorite={}/{}, like={}/{}, "
                        + "comment={}, social={}/{}",
                sourceMemberId, targetMemberId, operatorId,
                favoriteMoved, favoriteSkipped, likeMoved, likeSkipped,
                commentMoved, socialMoved, socialSkipped);
        return result;
    }

    private void fillMember(MemberMergePreviewVO vo, MemUser source, MemUser target) {
        vo.setSourceMemberId(source.getMemberId());
        vo.setSourceUsername(source.getUsername());
        vo.setSourceNickname(source.getNickname());
        vo.setSourceStatus(source.getStatus());
        vo.setTargetMemberId(target.getMemberId());
        vo.setTargetUsername(target.getUsername());
        vo.setTargetNickname(target.getNickname());
        vo.setTargetStatus(target.getStatus());
    }

    private void validate(MemUser source, MemUser target) {
        if (source.getMemberId().equals(target.getMemberId())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.mergeSame");
        }
        if (!CommonConstants.STATUS_NORMAL.equals(target.getStatus())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.member.mergeTargetDisabled");
        }
    }

    private MemUser mustGet(Long memberId) {
        MemUser user = memberId == null ? null : userMapper.selectById(memberId);
        if (user == null) {
            throw ServiceException.keyed(ErrorCode.NOT_FOUND, "error.1004.member");
        }
        return user;
    }

    private MemUser pick(List<MemUser> rows, Long memberId) {
        if (rows == null) {
            return null;
        }
        for (MemUser row : rows) {
            if (memberId.equals(row.getMemberId())) {
                return row;
            }
        }
        return null;
    }

    private Set<Long> favoriteArticles(Long memberId) {
        List<MemFavorite> rows = favoriteMapper.selectList(
                new LambdaQueryWrapper<MemFavorite>().eq(MemFavorite::getMemberId, memberId));
        Set<Long> result = new HashSet<>();
        for (MemFavorite row : rows) {
            result.add(row.getArticleId());
        }
        return result;
    }

    private Set<Long> likeArticles(Long memberId) {
        List<MemLike> rows = likeMapper.selectList(
                new LambdaQueryWrapper<MemLike>().eq(MemLike::getMemberId, memberId));
        Set<Long> result = new HashSet<>();
        for (MemLike row : rows) {
            result.add(row.getArticleId());
        }
        return result;
    }

    private long countComments(Long memberId) {
        Long count = commentMapper.selectCount(
                new LambdaQueryWrapper<MemComment>().eq(MemComment::getMemberId, memberId));
        return count == null ? 0L : count;
    }

    private List<MemUserSocial> socials(Long memberId) {
        return socialMapper.selectList(new LambdaQueryWrapper<MemUserSocial>()
                .eq(MemUserSocial::getMemberId, memberId)
                .orderByAsc(MemUserSocial::getCreateTime));
    }

    private long intersectSize(Set<Long> left, Set<Long> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0L;
        }
        long count = 0;
        for (Long value : left) {
            if (right.contains(value)) {
                count++;
            }
        }
        return count;
    }

    private String mergeRemark(MemUser target, String remark) {
        String merged = "已合并到会员 " + target.getUsername() + "（" + target.getMemberId() + "）";
        if (StringUtils.hasText(remark)) {
            merged = merged + "；" + remark.trim();
        }
        if (merged.length() > REMARK_MAX) {
            merged = merged.substring(0, REMARK_MAX);
        }
        return merged;
    }

    private Long operatorId() {
        try {
            return LoginHelper.getUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
