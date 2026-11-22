package com.lingxiao.modules.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * CMS 站点（cms_site）。
 *
 * <p>一个站点对应一个门户品牌：域名、Logo、SEO 默认值。v1.1 允许建多个，
 * 但门户默认按 {@code siteKey} 或域名取一个；租户化以后站点会挂到 tenant 下。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_site")
public class CmsSite extends BaseEntity {

    @TableId(value = "site_id", type = IdType.ASSIGN_ID)
    private Long siteId;

    /** 稳定键：脚本、门户、种子数据都按它找站点，改名不影响引用 */
    @NotBlank(message = "{valid.cms.site.key.required}")
    @Size(max = 64, message = "{valid.cms.site.key.max}")
    private String siteKey;

    @NotBlank(message = "{valid.cms.site.name.required}")
    @Size(max = 100, message = "{valid.cms.site.name.max}")
    private String siteName;

    @Size(max = 255, message = "{valid.cms.site.domain.max}")
    private String domain;

    /** fileId 或 http(s) URL */
    @Size(max = 255, message = "{valid.cms.logo.max}")
    private String logo;

    @Size(max = 200, message = "{valid.cms.seo.title.max}")
    private String seoTitle;

    @Size(max = 255, message = "{valid.cms.seo.keywords.max}")
    private String seoKeywords;

    @Size(max = 500, message = "{valid.cms.seo.description.max}")
    private String seoDescription;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
