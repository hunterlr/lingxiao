package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 会员账号合并预览。
 *
 * <p>只做只读统计，不修改任何数据；后台确认后再调用合并接口。
 * 收藏 / 点赞按 {@code articleId} 去重，重叠部分合并时跳过；评论直接迁移，
 * 第三方绑定同一平台只保留目标账号的一条。</p>
 */
@Data
public class MemberMergePreviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long sourceMemberId;
    private String sourceUsername;
    private String sourceNickname;
    private String sourceStatus;

    private Long targetMemberId;
    private String targetUsername;
    private String targetNickname;
    private String targetStatus;

    /** 旧账号收藏数 */
    private long favoriteSource;
    /** 新账号现有收藏数 */
    private long favoriteTarget;
    /** 两边都收藏了同一篇文章的数量，合并时跳过 */
    private long favoriteOverlap;

    private long likeSource;
    private long likeTarget;
    private long likeOverlap;

    /** 旧账号未删除的评论数 */
    private long commentSource;

    private long socialSource;
    private long socialTarget;
    /** 两边都绑定了的平台数量，合并时跳过旧账号的 */
    private long socialOverlap;

    /** 旧账号已绑定平台编码，仅展示 */
    private List<String> socialSourceProviders = new ArrayList<>();
    /** 新账号已绑定平台编码，仅展示 */
    private List<String> socialTargetProviders = new ArrayList<>();
}
