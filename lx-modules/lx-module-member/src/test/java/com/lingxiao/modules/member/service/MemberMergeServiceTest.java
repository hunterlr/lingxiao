package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员账号合并测试。
 *
 * <p>重点锁定：收藏 / 点赞按文章去重、评论直接改归属、同一平台绑定只保留目标账号、
 * 旧账号停用并踢会话、审计计数正确。</p>
 */
@DisplayName("MemberMergeService 账号合并")
class MemberMergeServiceTest {

    private MemUserMapper userMapper;
    private MemFavoriteMapper favoriteMapper;
    private MemLikeMapper likeMapper;
    private MemCommentMapper commentMapper;
    private MemUserSocialMapper socialMapper;
    private MemMergeLogMapper mergeLogMapper;
    private MemberTokenIssuer tokenIssuer;
    private MemberMergeService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemUser.class);
        TableInfoHelper.initTableInfo(assistant, MemFavorite.class);
        TableInfoHelper.initTableInfo(assistant, MemLike.class);
        TableInfoHelper.initTableInfo(assistant, MemComment.class);
        TableInfoHelper.initTableInfo(assistant, MemUserSocial.class);
        TableInfoHelper.initTableInfo(assistant, MemMergeLog.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(MemUserMapper.class);
        favoriteMapper = mock(MemFavoriteMapper.class);
        likeMapper = mock(MemLikeMapper.class);
        commentMapper = mock(MemCommentMapper.class);
        socialMapper = mock(MemUserSocialMapper.class);
        mergeLogMapper = mock(MemMergeLogMapper.class);
        tokenIssuer = mock(MemberTokenIssuer.class);
        service = new MemberMergeService(userMapper, favoriteMapper, likeMapper, commentMapper,
                socialMapper, mergeLogMapper, tokenIssuer);
    }

    private static MemUser user(Long id, String username, String status) {
        MemUser user = new MemUser();
        user.setMemberId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setStatus(status);
        return user;
    }

    private static MemFavorite favorite(Long memberId, Long articleId) {
        MemFavorite row = new MemFavorite();
        row.setTenantId(0L);
        row.setMemberId(memberId);
        row.setArticleId(articleId);
        return row;
    }

    private static MemLike like(Long memberId, Long articleId) {
        MemLike row = new MemLike();
        row.setTenantId(0L);
        row.setMemberId(memberId);
        row.setArticleId(articleId);
        return row;
    }

    private static MemUserSocial social(Long memberId, String provider) {
        MemUserSocial row = new MemUserSocial();
        row.setSocialId(100L + memberId);
        row.setMemberId(memberId);
        row.setProvider(provider);
        return row;
    }

    @Test
    @DisplayName("预览只统计，不修改数据")
    void previewReportsOverlaps() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "old", "0"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "keep", "0"));
        when(favoriteMapper.selectList(any()))
                .thenReturn(Arrays.asList(favorite(1L, 10L), favorite(1L, 20L)))
                .thenReturn(Collections.singletonList(favorite(2L, 20L)));
        when(likeMapper.selectList(any()))
                .thenReturn(Collections.singletonList(like(1L, 30L)))
                .thenReturn(Collections.emptyList());
        when(commentMapper.selectCount(any())).thenReturn(4L);
        when(socialMapper.selectList(any()))
                .thenReturn(Arrays.asList(social(1L, "github"), social(1L, "gitee")))
                .thenReturn(Collections.singletonList(social(2L, "github")));

        MemberMergePreviewVO vo = service.preview(1L, 2L);

        assertThat(vo.getFavoriteSource()).isEqualTo(2);
        assertThat(vo.getFavoriteTarget()).isEqualTo(1);
        assertThat(vo.getFavoriteOverlap()).isEqualTo(1);
        assertThat(vo.getLikeSource()).isEqualTo(1);
        assertThat(vo.getLikeOverlap()).isZero();
        assertThat(vo.getCommentSource()).isEqualTo(4);
        assertThat(vo.getSocialOverlap()).isEqualTo(1);
        assertThat(vo.getSocialSourceProviders()).containsExactly("github", "gitee");
        verify(favoriteMapper, times(0)).insert(any());
        verify(mergeLogMapper, times(0)).insert(any());
    }

    @Test
    @DisplayName("执行合并且按文章 / 平台去重，停用旧账号并写审计")
    void mergeMigratesAndDisablesSource() {
        when(userMapper.selectList(any()))
                .thenReturn(Arrays.asList(user(1L, "old", "0"), user(2L, "keep", "0")));
        when(favoriteMapper.selectList(any()))
                .thenReturn(Arrays.asList(favorite(1L, 10L), favorite(1L, 20L)))
                .thenReturn(Collections.singletonList(favorite(2L, 20L)));
        when(likeMapper.selectList(any()))
                .thenReturn(Arrays.asList(like(1L, 30L), like(1L, 40L)))
                .thenReturn(Collections.singletonList(like(2L, 40L)));
        when(commentMapper.update(any(), any())).thenReturn(3);
        when(socialMapper.selectList(any()))
                .thenReturn(Arrays.asList(social(1L, "github"), social(1L, "gitee")))
                .thenReturn(Collections.singletonList(social(2L, "github")));

        MemberMergeRequest request = new MemberMergeRequest();
        request.setSourceMemberId(1L);
        request.setTargetMemberId(2L);
        request.setRemark("历史重复账号");

        MemberMergeResultVO result = service.merge(request);

        assertThat(result.getFavoriteMoved()).isEqualTo(1);
        assertThat(result.getFavoriteSkipped()).isEqualTo(1);
        assertThat(result.getLikeMoved()).isEqualTo(1);
        assertThat(result.getLikeSkipped()).isEqualTo(1);
        assertThat(result.getCommentMoved()).isEqualTo(3);
        assertThat(result.getSocialMoved()).isEqualTo(1);
        assertThat(result.getSocialSkipped()).isEqualTo(1);

        // 只插入目标账号缺失的那一条收藏 / 点赞
        verify(favoriteMapper, times(1)).insert(any());
        verify(likeMapper, times(1)).insert(any());
        // 旧账号的绑定与收藏、点赞清空
        verify(favoriteMapper).delete(any());
        verify(likeMapper).delete(any());
        // 旧账号停用并踢会话
        verify(userMapper).update(any(), any());
        verify(tokenIssuer).logoutAll(1L);

        ArgumentCaptor<MemMergeLog> captor = ArgumentCaptor.forClass(MemMergeLog.class);
        verify(mergeLogMapper).insert(captor.capture());
        MemMergeLog audit = captor.getValue();
        assertThat(audit.getSourceMemberId()).isEqualTo(1L);
        assertThat(audit.getTargetMemberId()).isEqualTo(2L);
        assertThat(audit.getFavoriteMoved()).isEqualTo(1);
        assertThat(audit.getCommentMoved()).isEqualTo(3);
        assertThat(audit.getRemark()).isEqualTo("历史重复账号");
    }

    @Test
    @DisplayName("不能合并到自身，目标账号必须启用")
    void mergeRejectsInvalidTarget() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "old", "0"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "keep", "1"));

        assertThatThrownBy(() -> service.preview(1L, 1L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());

        assertThatThrownBy(() -> service.preview(1L, 2L))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());
    }
}
