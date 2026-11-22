package com.lingxiao.modules.cms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站文章行。
 *
 * <p>只给后台回收站列表用：标题、归属、删除人与删除时间足够定位内容，
 * 不返回正文，避免一页回收站把 longtext 全拖出来。站点 / 栏目名在
 * CMS 自己表内 JOIN 取，不跨模块查 sys_user。</p>
 */
@Data
public class CmsArticleRecycleVO {

    private Long articleId;
    private Long siteId;
    private String siteName;
    private Long categoryId;
    private String categoryName;
    private String title;
    private String slug;

    /** 删除前状态：0 草稿 / 1 已发布 / 2 已下线 */
    private String status;

    private Long deleteBy;
    private String deleteByName;
    private LocalDateTime deleteTime;
}
