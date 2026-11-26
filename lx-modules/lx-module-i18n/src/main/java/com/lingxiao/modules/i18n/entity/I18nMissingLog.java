package com.lingxiao.modules.i18n.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 缺失翻译记录（i18n_missing_log）。
 *
 * <p>同一语言 + 命名空间 + 键只保留一行，命中次数累加；
 * 请求线程只写内存，定时任务批量落库。</p>
 */
@Data
@TableName("i18n_missing_log")
public class I18nMissingLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id", type = IdType.ASSIGN_ID)
    private Long logId;

    private Long tenantId;

    private String languageCode;

    private String namespace;

    private String keyCode;

    private Long hitCount;

    private LocalDateTime firstSeen;

    private LocalDateTime lastSeen;

    private String sampleLocale;
}
