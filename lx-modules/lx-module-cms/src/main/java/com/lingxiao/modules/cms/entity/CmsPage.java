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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CMS 单页（cms_page）。
 *
 * <p>关于我们、联系方式这类“一个 key 对应一张固定页面”的内容。
 * 单页复用 page 内置模型的扩展字段，值存 {@code cms_content_ext}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_page")
public class CmsPage extends BaseEntity {

    @TableId(value = "page_id", type = IdType.ASSIGN_ID)
    private Long pageId;

    @NotNull(message = "{valid.cms.site.required}")
    private Long siteId;

    private Long modelId;

    @NotBlank(message = "{valid.cms.page.key.required}")
    @Size(max = 100, message = "{valid.cms.page.key.max}")
    private String pageKey;

    @NotBlank(message = "{valid.cms.page.name.required}")
    @Size(max = 100, message = "{valid.cms.page.name.max}")
    private String pageName;

    @Size(max = 200, message = "{valid.cms.article.title.max}")
    private String title;

    @Size(max = 500, message = "{valid.cms.article.summary.max}")
    private String summary;

    /** fileId 或 http(s) URL */
    @Size(max = 255, message = "{valid.cms.cover.max}")
    private String cover;

    /** 已清洗的 HTML */
    private String content;

    @Size(max = 100, message = "{valid.cms.template.max}")
    private String template;

    @Size(max = 200, message = "{valid.cms.seo.title.max}")
    private String seoTitle;

    @Size(max = 255, message = "{valid.cms.seo.keywords.max}")
    private String seoKeywords;

    @Size(max = 500, message = "{valid.cms.seo.description.max}")
    private String seoDescription;

    /** 0 草稿 / 1 已发布 / 2 已下线 */
    private String status;

    private LocalDateTime publishTime;

    private Integer sort;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;

    /** 编辑时提交的扩展字段；详情接口回填，不是表字段 */
    @TableField(exist = false)
    private Map<String, Object> ext = new LinkedHashMap<>();
}
