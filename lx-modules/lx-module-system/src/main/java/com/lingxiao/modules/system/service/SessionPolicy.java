package com.lingxiao.modules.system.service;

import com.lingxiao.common.core.config.ConfigProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 会话策略：同一账号是否允许多端同时在线。
 *
 * <p>参数 {@code sys.account.multiDeviceLogin}：</p>
 * <ul>
 *   <li>{@code false}（默认）：单会话，新登录时注销该账号的全部旧 Token；</li>
 *   <li>{@code true}：允许多端同时在线，在线用户列表按 Token 一行展示。</li>
 * </ul>
 *
 * <p><b>为什么在登录时主动踢，而不是改 {@code sa-token.is-concurrent}：</b>
 * 前者是运行期参数，改完下一次登录就生效；后者是启动期配置，
 * 改完必须重启，而且没法按参数设置页动态切换。</p>
 *
 * <p>参数值非法（手工改库、导入旧数据）时按单会话处理：
 * 失败收紧，宁可多踢一次旧会话，也不意外放开多端。</p>
 */
@Component
@RequiredArgsConstructor
public class SessionPolicy {

    /** 参数 key：与 V16 迁移落库的 sys_config 保持一致 */
    public static final String CONFIG_KEY = "sys.account.multiDeviceLogin";

    private final ConfigProvider configProvider;

    /** 是否允许多端登录；默认 false（新登录踢旧会话）。 */
    public boolean isMultiDeviceLoginEnabled() {
        String value = configProvider.getValue(CONFIG_KEY);
        return value != null && "true".equals(value.trim().toLowerCase(Locale.ROOT));
    }
}
