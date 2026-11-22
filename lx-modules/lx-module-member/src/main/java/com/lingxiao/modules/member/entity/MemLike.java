package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员点赞（mem_like）。
 *
 * <p>与收藏同样只存 {@code memberId + articleId}，取消点赞直接删行；
 * 点赞总数按 {@code articleId} 统计，门户公开接口可匿名读取。</p>
 */
@Data
@TableName("mem_like")
public class MemLike implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "like_id", type = IdType.ASSIGN_ID)
    private Long likeId;

    /** 单租户部署固定 0 */
    private Long tenantId;

    private Long memberId;

    private Long articleId;

    private LocalDateTime createTime;
}
