package com.lingxiao.modules.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * CMS 标签（cms_tag）。
 *
 * <p>{@code tagKey} 是站点内稳定键，门户 URL / 内容导入按它定位；
 * 标签名可以改，key 不建议改。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_tag")
public class CmsTag extends BaseEntity {

    @TableId(value = "tag_id", type = IdType.ASSIGN_ID)
    private Long tagId;

    @NotNull(message = "{valid.cms.site.required}")
    private Long siteId;

    @NotBlank(message = "{valid.cms.tag.name.required}")
    @Size(max = 64, message = "{valid.cms.tag.name.max}")
    private String tagName;

    @NotBlank(message = "{valid.cms.tag.key.required}")
    @Size(max = 64, message = "{valid.cms.tag.key.max}")
    private String tagKey;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
