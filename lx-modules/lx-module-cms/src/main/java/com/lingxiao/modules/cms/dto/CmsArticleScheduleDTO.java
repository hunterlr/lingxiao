package com.lingxiao.modules.cms.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 定时发布请求。
 *
 * <p>只允许把草稿 / 已下线文章排期；时间格式由全局 Jackson 负责
 * （{@code yyyy-MM-dd HH:mm:ss}），服务层还会再校验必须晚于当前时间。</p>
 */
@Data
public class CmsArticleScheduleDTO {

    @NotNull(message = "{valid.cms.article.schedule.required}")
    private LocalDateTime scheduleTime;
}
