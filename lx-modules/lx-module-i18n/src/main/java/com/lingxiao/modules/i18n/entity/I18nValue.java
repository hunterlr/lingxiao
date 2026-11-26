package com.lingxiao.modules.i18n.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 翻译值（i18n_value）。
 *
 * <p>{@code valueText} 是工作副本，后台保存草稿即改它；{@code publishedText}
 * 是最近一次发布的值，只有它会被 MessageSource / 门户读取，所以“草稿不影响线上”。</p>
 *
 * <p>{@code reviewStatus=0} 表示工作副本有未发布修改，{@code 1} 表示已发布；
 * 发布时把工作副本写入 {@code publishedText} 并留下 i18n_pack 快照。</p>
 */
@Data
@TableName("i18n_value")
public class I18nValue implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "value_id", type = IdType.ASSIGN_ID)
    private Long valueId;

    private Long keyId;

    /** 0=平台；租户覆盖预留 */
    private Long tenantId;

    private String languageCode;

    private String valueText;

    /** 最近一次发布的译文；草稿修改不动它 */
    private String publishedText;

    /** 0 有未发布修改 / 1 已发布 */
    private String reviewStatus;

    private Integer version;

    private Long translatedBy;

    private LocalDateTime translatedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
