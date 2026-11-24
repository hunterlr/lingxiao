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
 * CMS 区块 / 碎片（cms_block）。
 *
 * <p>首页推荐位、可复用内容块。一个 block_key 对应一段内容，
 * 门户按 key 读取；正文同样经过服务端清洗。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_block")
public class CmsBlock extends BaseEntity {

    @TableId(value = "block_id", type = IdType.ASSIGN_ID)
    private Long blockId;

    @NotNull(message = "{valid.cms.site.required}")
    private Long siteId;

    @NotBlank(message = "{valid.cms.block.key.required}")
    @Size(max = 64, message = "{valid.cms.block.key.max}")
    private String blockKey;

    @NotBlank(message = "{valid.cms.block.name.required}")
    @Size(max = 100, message = "{valid.cms.block.name.max}")
    private String blockName;

    /** html / image */
    @NotBlank(message = "{valid.cms.block.type}")
    private String blockType;

    private String content;

    /** fileId 或 http(s) URL */
    @Size(max = 255, message = "{valid.cms.cover.max}")
    private String cover;

    @Size(max = 500, message = "{valid.cms.banner.link.max}")
    private String linkUrl;

    private Integer sort;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
