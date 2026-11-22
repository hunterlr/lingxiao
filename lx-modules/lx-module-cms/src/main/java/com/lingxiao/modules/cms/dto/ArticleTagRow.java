package com.lingxiao.modules.cms.dto;

import lombok.Data;

/**
 * 文章-标签关联查询行。
 *
 * <p>门户列表需要一次取回一页文章的标签，避免逐篇查询造成 N+1；
 * 这个 DTO 只承载两列，不对外暴露。</p>
 */
@Data
public class ArticleTagRow {

    private Long articleId;

    private Long tagId;
}
