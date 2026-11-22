package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 单篇文章的会员互动状态。
 *
 * <p>公开接口只保证 {@code favoriteCount / likeCount}；带会员 Token 时
 * 再返回 {@code favorited / liked}，匿名访问时两者固定为 {@code false}。</p>
 */
@Data
public class MemberInteractionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long articleId;

    /** 全站收藏数 */
    private Long favoriteCount = 0L;

    /** 全站点赞数 */
    private Long likeCount = 0L;

    /** 当前会员是否已收藏；匿名访问固定 false */
    private Boolean favorited = false;

    /** 当前会员是否已点赞；匿名访问固定 false */
    private Boolean liked = false;
}
