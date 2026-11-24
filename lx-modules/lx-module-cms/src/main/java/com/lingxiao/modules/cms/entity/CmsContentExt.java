package com.lingxiao.modules.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CMS 内容扩展字段（cms_content_ext）。
 *
 * <p>文章与单页共用一张扩展表，用 {@code owner_type + owner_id} 区分；
 * 唯一键保证一条内容只有一份扩展 JSON。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_content_ext")
public class CmsContentExt extends BaseEntity {

    @TableId(value = "ext_id", type = IdType.ASSIGN_ID)
    private Long extId;

    private Long siteId;

    /** article / page */
    private String ownerType;

    private Long ownerId;

    private Long modelId;

    /** 扩展字段 JSON 字符串 */
    private String extJson;
}
