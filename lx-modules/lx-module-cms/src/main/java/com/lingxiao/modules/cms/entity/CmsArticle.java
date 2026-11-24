package com.lingxiao.modules.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CMS 文章（cms_article）。
 *
 * <p>正文是富文本 HTML，服务端写入 / 读取两侧都走
 * {@code com.lingxiao.common.web.util.HtmlSanitizer}。</p>
 *
 * <p>状态：0 草稿 / 1 已发布 / 2 已下线 / 3 定时待发布。发布、下线、定时都走独立接口，
 * 普通编辑不改状态与发布时间，避免“改个错别字把下线文章又发出去”。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_article")
public class CmsArticle extends BaseEntity {

    @TableId(value = "article_id", type = IdType.ASSIGN_ID)
    private Long articleId;

    @NotNull(message = "{valid.cms.site.required}")
    private Long siteId;

    @NotNull(message = "{valid.cms.category.required}")
    private Long categoryId;

    @NotBlank(message = "{valid.cms.article.title.required}")
    @Size(max = 200, message = "{valid.cms.article.title.max}")
    private String title;

    @Size(max = 200, message = "{valid.cms.slug.max}")
    private String slug;

    @Size(max = 500, message = "{valid.cms.article.summary.max}")
    private String summary;

    /** fileId 或 http(s) URL */
    @Size(max = 255, message = "{valid.cms.cover.max}")
    private String cover;

    private String content;

    /** sys_user.user_id，可为空（外部作者） */
    private Long authorId;

    @Size(max = 64, message = "{valid.cms.article.author.max}")
    private String authorName;

    @Size(max = 100, message = "{valid.cms.article.source.max}")
    private String source;

    @Size(max = 255, message = "{valid.cms.source.url.max}")
    private String sourceUrl;

    /** 0 草稿 / 1 已发布 / 2 已下线 / 3 定时待发布 */
    private String status;

    private LocalDateTime publishTime;

    /** 计划发布时间；status=3 时有效，到点发布后清空 */
    private LocalDateTime scheduleTime;

    private Long viewCount;

    private Integer sort;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;

    /** 回收站审计：删除人 ID / 昵称快照 / 删除时间；正常查询不返回 */
    @TableField(select = false)
    private Long deleteBy;

    @TableField(select = false)
    private String deleteByName;

    @TableField(select = false)
    private LocalDateTime deleteTime;

    /** 编辑时提交的标签；详情接口也会回填，不是表字段 */
    @TableField(exist = false)
    private List<Long> tagIds = new ArrayList<>();

    /** 模型扩展字段（图集 images 等）；详情接口回填，不是表字段 */
    @TableField(exist = false)
    private Map<String, Object> ext = new LinkedHashMap<>();
}
