package com.lingxiao.modules.i18n.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 翻译键（i18n_key）。
 *
 * <p>{@code keyCode} 存完整键，例如 {@code error.2001}；
 * {@code namespace} 取第一段，便于按命名空间加载和统计。</p>
 */
@Data
@TableName("i18n_key")
public class I18nKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "key_id", type = IdType.ASSIGN_ID)
    private Long keyId;

    private String namespace;

    private String keyCode;

    private String defaultValue;

    private String moduleCode;

    /** 0 启用 / 1 停用（废弃键保留，不物理删除） */
    private String status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
