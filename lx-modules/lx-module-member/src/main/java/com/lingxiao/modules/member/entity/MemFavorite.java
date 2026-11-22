package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员收藏（mem_favorite）。
 *
 * <p>只存 {@code memberId + articleId}，不建跨模块外键，也不复制文章标题；
 * 门户展示“我的收藏”时再通过 CMS 门户接口补文章摘要。取消收藏直接删行，
 * 让同一会员可以重新收藏。</p>
 */
@Data
@TableName("mem_favorite")
public class MemFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "favorite_id", type = IdType.ASSIGN_ID)
    private Long favoriteId;

    /** 单租户部署固定 0 */
    private Long tenantId;

    private Long memberId;

    private Long articleId;

    private LocalDateTime createTime;
}
