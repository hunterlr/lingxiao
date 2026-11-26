package com.lingxiao.modules.i18n.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台语言定义（i18n_language）。
 *
 * <p>字段严格按 docs/15-i18n.md；不继承 BaseEntity，避免给 i18n 表
 * 额外塞 del_flag / version 等文档里没有的列。</p>
 */
@Data
@TableName("i18n_language")
public class I18nLanguage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "language_id", type = IdType.ASSIGN_ID)
    private Long languageId;

    /** BCP 47，例如 zh-CN / en-US */
    private String languageCode;

    private String languageName;

    private String nativeName;

    /** Java Locale 形式，例如 zh_CN / en_US */
    private String locale;

    /** ltr / rtl */
    private String direction;

    /** 1=平台默认语言 */
    private Integer isDefault;

    /** 0 启用 / 1 停用 */
    private String status;

    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
