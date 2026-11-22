package com.lingxiao.modules.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CMS 文章内容版本（cms_article_version）。
 *
 * <p>不可变快照：每次创建 / 编辑 / 发布 / 回滚后写入一行，只增不改。
 * 版本只覆盖内容字段，排序 / 备注 / 作者等元数据不进入快照。</p>
 *
 * <p>{@code contentHash} 用于相邻版本去重；{@code tagIds} 是逗号分隔的
 * 标签 ID 快照，回滚时只恢复仍然存在且属于同一站点的标签。</p>
 */
@Data
@TableName("cms_article_version")
public class CmsArticleVersion {

    @TableId(value = "version_id", type = IdType.ASSIGN_ID)
    private Long versionId;

    private Long articleId;

    private Long siteId;

    /** 文章内自增，从 1 开始 */
    private Integer versionNo;

    /** create / edit / publish / rollback */
    private String changeType;

    private Long categoryId;

    private String title;

    private String slug;

    private String summary;

    private String cover;

    private String content;

    private String source;

    private String sourceUrl;

    /** 逗号分隔的标签 ID 快照 */
    private String tagIds;

    /** 快照时的文章状态 */
    private String status;

    /** SHA-256，用于相邻版本去重 */
    private String contentHash;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createTime;

    /** 版本详情回填的标签 ID；不是表字段 */
    @TableField(exist = false)
    private List<Long> tagIdList = new ArrayList<>();
}
