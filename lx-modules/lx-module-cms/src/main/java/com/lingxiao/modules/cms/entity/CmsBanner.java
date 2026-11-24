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
import java.time.LocalDateTime;

/**
 * CMS 轮播 / 广告位（cms_banner）。
 *
 * <p>同一站点可有多组轮播（banner_group=home / sidebar 等）。
 * 门户只出启用且在起止时间内的记录，按 sort 升序。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_banner")
public class CmsBanner extends BaseEntity {

    @TableId(value = "banner_id", type = IdType.ASSIGN_ID)
    private Long bannerId;

    @NotNull(message = "{valid.cms.site.required}")
    private Long siteId;

    @NotBlank(message = "{valid.cms.banner.group.required}")
    @Size(max = 32, message = "{valid.cms.banner.group.max}")
    private String bannerGroup;

    @Size(max = 200, message = "{valid.cms.banner.title.max}")
    private String title;

    /** fileId 或 http(s) URL */
    @Size(max = 255, message = "{valid.cms.banner.image.max}")
    private String image;

    @Size(max = 500, message = "{valid.cms.banner.link.max}")
    private String linkUrl;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer sort;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
