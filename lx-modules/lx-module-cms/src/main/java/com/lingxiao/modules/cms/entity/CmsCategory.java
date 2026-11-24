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
import java.util.ArrayList;
import java.util.List;

/**
 * CMS 栏目（cms_category）。
 *
 * <p>树结构用 {@code parentId} 表达，同时由服务层维护
 * {@code cms_category_closure}：取某栏目整棵子树时不用递归查库。</p>
 *
 * <p>{@code children} 不是表字段，只在树接口里回填。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_category")
public class CmsCategory extends BaseEntity {

    @TableId(value = "category_id", type = IdType.ASSIGN_ID)
    private Long categoryId;

    @NotNull(message = "{valid.cms.site.required}")
    private Long siteId;

    /** 0 表示根栏目 */
    private Long parentId;

    /** 绑定的内容模型；空值回退站点内置文章模型 */
    private Long modelId;

    /** 站点内稳定键：门户路径 / 内容导入按它定位，改名不影响引用 */
    @NotBlank(message = "{valid.cms.category.key.required}")
    @Size(max = 64, message = "{valid.cms.category.key.max}")
    private String categoryKey;

    @NotBlank(message = "{valid.cms.category.name.required}")
    @Size(max = 100, message = "{valid.cms.category.name.max}")
    private String categoryName;

    @Size(max = 100, message = "{valid.cms.slug.max}")
    private String slug;

    @Size(max = 255, message = "{valid.cms.path.max}")
    private String path;

    @Size(max = 100, message = "{valid.cms.template.max}")
    private String template;

    @Size(max = 64, message = "{valid.cms.icon.max}")
    private String icon;

    /** fileId 或 http(s) URL */
    @Size(max = 255, message = "{valid.cms.cover.max}")
    private String cover;

    @Size(max = 200, message = "{valid.cms.seo.title.max}")
    private String seoTitle;

    @Size(max = 255, message = "{valid.cms.seo.keywords.max}")
    private String seoKeywords;

    @Size(max = 500, message = "{valid.cms.seo.description.max}")
    private String seoDescription;

    private Integer sort;

    /** 0 显示 / 1 隐藏 */
    private String visible;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;

    @TableField(exist = false)
    private List<CmsCategory> children = new ArrayList<>();
}
