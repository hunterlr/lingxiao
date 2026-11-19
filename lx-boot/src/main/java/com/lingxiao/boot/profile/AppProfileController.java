package com.lingxiao.boot.profile;

import com.lingxiao.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用名片接口：登录页 / 侧边栏读取品牌名与版本。
 *
 * <p>路径 {@code /api/admin/app/profile} 在 {@code SaTokenConfigure} 中放行，
 * 未登录也能访问；返回内容全都是界面上本来就展示的信息。</p>
 *
 * <p>放在启动模块而不是业务模块：它是“部署信息”，不属于任何业务域，
 * 模块被关闭（文件 / 任务 / 生成器）时品牌接口仍然要可用。</p>
 */
@RestController
@RequestMapping("/api/admin/app")
public class AppProfileController {

    private final AppProperties appProperties;

    public AppProfileController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping("/profile")
    public R<AppProfile> profile() {
        return R.ok(AppProfile.of(appProperties));
    }
}
