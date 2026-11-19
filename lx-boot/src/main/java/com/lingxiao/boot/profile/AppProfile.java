package com.lingxiao.boot.profile;

/**
 * 公开的应用名片：名称 + 版本。
 *
 * <p>只包含登录页 / 侧边栏需要展示的信息，不含任何环境或安全配置，
 * 因此可以由未登录请求读取。</p>
 */
public class AppProfile {

    private final String name;

    private final String nameEn;

    private final String version;

    public AppProfile(String name, String nameEn, String version) {
        this.name = name;
        this.nameEn = nameEn;
        this.version = version;
    }

    /**
     * 从配置构造名片。
     *
     * <p>中文名与英文名互为兜底：只配了一个时，另一种语言也显示它，
     * 避免切换语言后标题变空。</p>
     */
    public static AppProfile of(AppProperties properties) {
        String name = trimToEmpty(properties.getName());
        String nameEn = trimToEmpty(properties.getNameEn());
        if (name.isEmpty()) {
            name = nameEn;
        }
        if (nameEn.isEmpty()) {
            nameEn = name;
        }
        return new AppProfile(name, nameEn, trimToEmpty(properties.getVersion()));
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getVersion() {
        return version;
    }
}
