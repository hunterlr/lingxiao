package com.lingxiao.modules.cms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台生成的草稿预览票据。
 *
 * <p>门户用 {@code ticket} 打开预览链接，票据一次性、限时有效。</p>
 */
@Data
public class CmsPreviewTicketVO {

    private String ticket;

    private LocalDateTime expireTime;
}
