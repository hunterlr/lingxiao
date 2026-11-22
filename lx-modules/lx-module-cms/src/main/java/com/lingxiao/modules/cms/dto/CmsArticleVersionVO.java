package com.lingxiao.modules.cms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容版本列表行。
 *
 * <p>列表不返回正文，避免历史版本把一页数据撑大；正文在版本详情里按需读取。</p>
 */
@Data
public class CmsArticleVersionVO {

    private Long versionId;

    private Integer versionNo;

    private String changeType;

    private String title;

    private String slug;

    private String status;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createTime;
}
