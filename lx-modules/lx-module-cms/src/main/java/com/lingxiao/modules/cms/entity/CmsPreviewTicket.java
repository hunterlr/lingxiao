package com.lingxiao.modules.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * CMS 草稿预览票据（cms_preview_ticket）。
 *
 * <p>后台为草稿生成一次性票据，门户凭票据读取草稿。票据绑定站点与目标，
 * 有有效期且只能使用一次，避免草稿链接被长期传播。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_preview_ticket")
public class CmsPreviewTicket extends BaseEntity {

    @TableId(value = "ticket_id", type = IdType.ASSIGN_ID)
    private Long ticketId;

    private String ticket;

    private Long siteId;

    /** article / page */
    private String targetType;

    private Long targetId;

    /** 预览时覆盖的模板编码；空值沿用目标自身模板 */
    private String template;

    private LocalDateTime expireTime;

    /** 0 未使用 / 1 已使用 */
    private String used;
}
