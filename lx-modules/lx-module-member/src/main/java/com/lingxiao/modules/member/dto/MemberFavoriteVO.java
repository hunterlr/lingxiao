package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的收藏条目。
 *
 * <p>只返回文章 ID 与收藏时间；标题 / 封面由门户再调 CMS 门户接口补齐，
 * 避免会员模块反向依赖 CMS，也避免文章改标题后收藏列表出现旧文案。</p>
 */
@Data
public class MemberFavoriteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long articleId;

    private LocalDateTime createTime;
}
