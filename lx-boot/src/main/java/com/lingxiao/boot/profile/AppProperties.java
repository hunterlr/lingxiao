package com.lingxiao.boot.profile;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用品牌与版本（部署信息，不是业务数据）。
 *
 * <p>这是全站名称 / 版本号的<b>唯一真源</b>：</p>
 * <ul>
 *   <li>{@code lx.app.name} / {@code lx.app.name-en}：中英文品牌名；</li>
 *   <li>{@code lx.app.version}：对外展示版本，构建时由 Maven 的
 *       {@code @project.version@} 填充，发布只需改根 {@code pom.xml}。</li>
 * </ul>
 *
 * <p>前端不写死品牌，启动时通过 {@code GET /api/admin/app/profile}
 * 读取一次；改名称 / 版本不需要重新构建前端。</p>
 *
 * <p>为什么不放 {@code sys_config}：品牌属于制品属性，换库 / 清库 / 导入快照
 * 都会丢；登录页在未登录时就要显示名称，也不适合依赖业务参数表。</p>
 */
@Component
@ConfigurationProperties(prefix = "lx.app")
public class AppProperties {

    /** 中文品牌名，如“凌霄后台”。 */
    private String name = "凌霄后台";

    /** 英文品牌名，如“LingXiao Admin”。 */
    private String nameEn = "LingXiao Admin";

    /** 对外展示版本，如 1.0.0。 */
    private String version = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
