package com.lingxiao.modules.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.member.dto.MemberFavoriteVO;
import com.lingxiao.modules.member.dto.MemberInteractionVO;
import com.lingxiao.modules.member.entity.MemFavorite;
import com.lingxiao.modules.member.entity.MemLike;
import com.lingxiao.modules.member.mapper.MemFavoriteMapper;
import com.lingxiao.modules.member.mapper.MemLikeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员收藏 / 点赞服务测试。
 *
 * <p>重点覆盖：匿名只读总数、登录后个人状态、重复操作幂等、取消删除，
 * 以及非法文章 ID 不落库。</p>
 */
@DisplayName("MemberInteractionService 收藏与点赞")
class MemberInteractionServiceTest {

    private MemFavoriteMapper favoriteMapper;
    private MemLikeMapper likeMapper;
    private MemberInteractionService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MemFavorite.class);
        TableInfoHelper.initTableInfo(assistant, MemLike.class);
    }

    @BeforeEach
    void setUp() {
        favoriteMapper = mock(MemFavoriteMapper.class);
        likeMapper = mock(MemLikeMapper.class);
        service = new MemberInteractionService(favoriteMapper, likeMapper);
    }

    @Test
    @DisplayName("匿名只返回总数，个人状态固定 false")
    void statsForAnonymous() {
        when(favoriteMapper.selectCount(any())).thenReturn(2L);
        when(likeMapper.selectCount(any())).thenReturn(3L);

        MemberInteractionVO vo = service.stats(100L, null);

        assertThat(vo.getArticleId()).isEqualTo(100L);
        assertThat(vo.getFavoriteCount()).isEqualTo(2L);
        assertThat(vo.getLikeCount()).isEqualTo(3L);
        assertThat(vo.getFavorited()).isFalse();
        assertThat(vo.getLiked()).isFalse();
    }

    @Test
    @DisplayName("收藏不存在时插入，并返回已收藏状态")
    void favoriteInsertsWhenAbsent() {
        when(favoriteMapper.selectCount(any())).thenReturn(0L, 1L, 1L);
        when(likeMapper.selectCount(any())).thenReturn(0L, 0L);

        MemberInteractionVO vo = service.favorite(9L, 100L);

        verify(favoriteMapper).insert(any(MemFavorite.class));
        assertThat(vo.getFavoriteCount()).isEqualTo(1L);
        assertThat(vo.getFavorited()).isTrue();
        assertThat(vo.getLiked()).isFalse();
    }

    @Test
    @DisplayName("已收藏时重复收藏不新增")
    void favoriteIsIdempotent() {
        when(favoriteMapper.selectCount(any())).thenReturn(1L, 1L, 1L);
        when(likeMapper.selectCount(any())).thenReturn(0L, 0L);

        MemberInteractionVO vo = service.favorite(9L, 100L);

        verify(favoriteMapper, never()).insert(any(MemFavorite.class));
        assertThat(vo.getFavorited()).isTrue();
    }

    @Test
    @DisplayName("取消收藏直接删除关系")
    void unfavoriteDeletesRelation() {
        when(favoriteMapper.selectCount(any())).thenReturn(0L, 0L);
        when(likeMapper.selectCount(any())).thenReturn(0L, 0L);

        MemberInteractionVO vo = service.unfavorite(9L, 100L);

        verify(favoriteMapper).delete(any());
        assertThat(vo.getFavoriteCount()).isZero();
        assertThat(vo.getFavorited()).isFalse();
    }

    @Test
    @DisplayName("非法文章 ID 不落库")
    void invalidArticleIdRejected() {
        assertThatThrownBy(() -> service.like(9L, 0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文章不存在");
        verify(likeMapper, never()).insert(any(MemLike.class));
    }

    @Test
    @DisplayName("我的收藏按时间倒序分页")
    void pageFavoritesMapsRecords() {
        Page<MemFavorite> page = new Page<>(1, 10);
        page.setTotal(1);
        MemFavorite favorite = new MemFavorite();
        favorite.setArticleId(100L);
        favorite.setCreateTime(LocalDateTime.of(2026, 11, 22, 20, 0));
        page.setRecords(Collections.singletonList(favorite));
        when(favoriteMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<MemberFavoriteVO> result = service.pageFavorites(9L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getArticleId()).isEqualTo(100L);
    }
}
