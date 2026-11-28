package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 门户语言配置（公开接口下发）。
 *
 * <p>门户只靠这一份配置决定 URL 形态、默认语言与可切换语言；
 * 后台改参数后 5 秒内生效，不需要重启或重新构建前端。</p>
 */
@Data
public class PortalLangConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** path / query / cookie */
    private String mode;

    /** 默认语言代码，如 zh-CN */
    private String defaultLang;

    /** 默认语言是否也带 URL 前缀 */
    private boolean defaultLangInPath;

    /** 门户对外语言，按排序；至少一项 */
    private List<Language> languages = new ArrayList<>();

    /** 一种语言在 URL 里的写法与展示名。 */
    @Data
    public static class Language implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 完整 locale，如 zh-CN */
        private String code;

        /** URL 短码，如 zh-cn / en */
        private String urlCode;

        private String name;

        private String nativeName;

        /** ltr / rtl */
        private String direction;

        private boolean isDefault;
    }
}