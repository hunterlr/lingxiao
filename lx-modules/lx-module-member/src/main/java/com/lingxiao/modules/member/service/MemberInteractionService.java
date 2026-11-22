package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberFavoriteVO;
import com.lingxiao.modules.member.dto.MemberInteractionVO;
import com.lingxiao.modules.member.entity.MemFavorite;
import com.lingxiao.modules.member.entity.MemLike;
import com.lingxiao.modules.member.mapper.MemFavoriteMapper;
import com.lingxiao.modules.member.mapper.MemLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会员互动服务：收藏与点赞。
 *
 * <p><b>只认会员域：</b>所有 {@code memberId} 都来自会员 Token 的 Session
 * （{@code MemberLoginHelper}），接口不接受前端传会员 ID；平台后台 Token
 * 没有会员会话，访问这里会先被会员域拦截器拒掉。</p>
 *
 * <p><b>跨模块边界：</b>只存 {@code article_id}，不查 {@code cms_article}、
 * 不建外键。文章是否可见由 CMS 门户接口负责；收藏列表由门户再用公开的
 * 文章批量摘要接口补展示字段。</p>
 *
 * <p><b>幂等：</b>重复收藏 / 点赞不报错，取消不存在的收藏 / 点赞同样成功；
 * 并发重复插入由唯一键兜底，捕获 {@link DuplicateKeyException} 视为已收藏。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberInteractionService {

    private static final long DEFAULT_TENANT_ID = 0L;

    /** 与其它会员分页接口保持一致：单页最多 50 条。 */
    private static final long MAX_PAGE_SIZE = 50;

    private final MemFavoriteMapper favoriteMapper;
    private final MemLikeMapper likeMapper;

    /**
     * 单篇文章的互动状态。
     *
     * <p>{@code memberId} 为空表示匿名访问：只返回总数，不返回个人状态。</p>
     */
    public MemberInteractionVO stats(Long articleId, Long memberId) {
        Long normalizedArticleId = requireArticleId(articleId);
        MemberInteractionVO vo = new MemberInteractionVO();
        vo.setArticleId(normalizedArticleId);
        vo.setFavoriteCount(favoriteMapper.selectCount(new LambdaQueryWrapper<MemFavorite>()
                .eq(MemFavorite::getArticleId, normalizedArticleId)));
        vo.setLikeCount(likeMapper.selectCount(new LambdaQueryWrapper<MemLike>()
                .eq(MemLike::getArticleId, normalizedArticleId)));
        vo.setFavorited(memberId != null && favoriteMapper.selectCount(new LambdaQueryWrapper<MemFavorite>()
                .eq(MemFavorite::getMemberId, memberId)
                .eq(MemFavorite::getArticleId, normalizedArticleId)) > 0);
        vo.setLiked(memberId != null && likeMapper.selectCount(new LambdaQueryWrapper<MemLike>()
                .eq(MemLike::getMemberId, memberId)
                .eq(MemLike::getArticleId, normalizedArticleId)) > 0);
        return vo;
    }

    /** 我的收藏分页，按收藏时间倒序。 */
    public PageResult<MemberFavoriteVO> pageFavorites(Long memberId, long pageNum, long pageSize) {
        Long currentMemberId = requireMemberId(memberId);
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<MemFavorite> page = new Page<>(current, size);
        Page<MemFavorite> result = favoriteMapper.selectPage(page, new LambdaQueryWrapper<MemFavorite>()
                .eq(MemFavorite::getMemberId, currentMemberId)
                .orderByDesc(MemFavorite::getCreateTime)
                .orderByDesc(MemFavorite::getFavoriteId));
        List<MemberFavoriteVO> records = result.getRecords().stream().map(item -> {
            MemberFavoriteVO vo = new MemberFavoriteVO();
            vo.setArticleId(item.getArticleId());
            vo.setCreateTime(item.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 收藏；已收藏时直接返回当前状态。 */
    @Transactional
    public MemberInteractionVO favorite(Long memberId, Long articleId) {
        Long currentMemberId = requireMemberId(memberId);
        Long currentArticleId = requireArticleId(articleId);
        if (!existsFavorite(currentMemberId, currentArticleId)) {
            MemFavorite favorite = new MemFavorite();
            favorite.setTenantId(DEFAULT_TENANT_ID);
            favorite.setMemberId(currentMemberId);
            favorite.setArticleId(currentArticleId);
            favorite.setCreateTime(LocalDateTime.now());
            try {
                favoriteMapper.insert(favorite);
            } catch (DuplicateKeyException ignored) {
                // 并发下另一个请求已插入，视为收藏成功
            }
        }
        return stats(currentArticleId, currentMemberId);
    }

    /** 取消收藏；未收藏时同样返回成功后的状态。 */
    @Transactional
    public MemberInteractionVO unfavorite(Long memberId, Long articleId) {
        Long currentMemberId = requireMemberId(memberId);
        Long currentArticleId = requireArticleId(articleId);
        favoriteMapper.delete(new LambdaQueryWrapper<MemFavorite>()
                .eq(MemFavorite::getMemberId, currentMemberId)
                .eq(MemFavorite::getArticleId, currentArticleId));
        return stats(currentArticleId, currentMemberId);
    }

    /** 点赞；已点赞时直接返回当前状态。 */
    @Transactional
    public MemberInteractionVO like(Long memberId, Long articleId) {
        Long currentMemberId = requireMemberId(memberId);
        Long currentArticleId = requireArticleId(articleId);
        if (!existsLike(currentMemberId, currentArticleId)) {
            MemLike like = new MemLike();
            like.setTenantId(DEFAULT_TENANT_ID);
            like.setMemberId(currentMemberId);
            like.setArticleId(currentArticleId);
            like.setCreateTime(LocalDateTime.now());
            try {
                likeMapper.insert(like);
            } catch (DuplicateKeyException ignored) {
                // 并发下另一个请求已插入，视为点赞成功
            }
        }
        return stats(currentArticleId, currentMemberId);
    }

    /** 取消点赞；未点赞时同样返回成功后的状态。 */
    @Transactional
    public MemberInteractionVO unlike(Long memberId, Long articleId) {
        Long currentMemberId = requireMemberId(memberId);
        Long currentArticleId = requireArticleId(articleId);
        likeMapper.delete(new LambdaQueryWrapper<MemLike>()
                .eq(MemLike::getMemberId, currentMemberId)
                .eq(MemLike::getArticleId, currentArticleId));
        return stats(currentArticleId, currentMemberId);
    }

    private boolean existsFavorite(Long memberId, Long articleId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<MemFavorite>()
                .eq(MemFavorite::getMemberId, memberId)
                .eq(MemFavorite::getArticleId, articleId)) > 0;
    }

    private boolean existsLike(Long memberId, Long articleId) {
        return likeMapper.selectCount(new LambdaQueryWrapper<MemLike>()
                .eq(MemLike::getMemberId, memberId)
                .eq(MemLike::getArticleId, articleId)) > 0;
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
}
