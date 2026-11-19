package com.lingxiao.boot.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 应用名片：配置缺失 / 空白时的兜底规则。
 *
 * <p>这些规则不是“锦上添花”：部署时只配中文名、或环境变量把版本置空，
 * 都应该得到一个可渲染的名片，而不是 null 或空标题。</p>
 */
class AppProfileTest {

    @Test
    void trimsValues() {
        AppProperties properties = new AppProperties();
        properties.setName("  凌霄后台  ");
        properties.setNameEn("  LingXiao Admin ");
        properties.setVersion(" 1.0.0 ");

        AppProfile profile = AppProfile.of(properties);

        assertEquals("凌霄后台", profile.getName());
        assertEquals("LingXiao Admin", profile.getNameEn());
        assertEquals("1.0.0", profile.getVersion());
    }

    @Test
    void missingEnglishNameFallsBackToChinese() {
        AppProperties properties = new AppProperties();
        properties.setName("凌霄后台");
        properties.setNameEn("   ");

        AppProfile profile = AppProfile.of(properties);

        assertEquals("凌霄后台", profile.getName());
        assertEquals("凌霄后台", profile.getNameEn());
    }

    @Test
    void missingChineseNameFallsBackToEnglish() {
        AppProperties properties = new AppProperties();
        properties.setName(null);
        properties.setNameEn("LingXiao Admin");

        AppProfile profile = AppProfile.of(properties);

        assertEquals("LingXiao Admin", profile.getName());
        assertEquals("LingXiao Admin", profile.getNameEn());
    }

    @Test
    void nullVersionBecomesEmptyString() {
        AppProperties properties = new AppProperties();
        properties.setVersion(null);

        assertEquals("", AppProfile.of(properties).getVersion());
    }
}
