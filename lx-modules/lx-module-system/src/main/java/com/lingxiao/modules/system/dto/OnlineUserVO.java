package com.lingxiao.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线用户（一个 Token 一行）。
 *
 * <p><b>为什么按 Token 而不是按用户：</b>平台默认单会话
 * （{@code sys.account.multiDeviceLogin=false}），开启多端后同一账号可以有多条会话。
 * 列表按 Token 一行，既能如实反映“人在办公室和家里各登录一次”，
 * 也能在多端模式下只踢掉其中一条。</p>
 *
 * <p><b>为什么 tokenId 是脱敏值：</b>Token 是可直接冒充用户的凭证，
 * 不能出现在列表响应里。后端返回“首 4 + 末 4”的脱敏串，强退时再按
 * 脱敏串反查真实 Token；这样即使响应被日志 / 浏览器插件记录，也拿不到可用凭证。</p>
 */
@Data
public class OnlineUserVO {

    /** 脱敏后的会话标识，仅用于强退时定位 Token */
    private String tokenId;

    private Long userId;

    private String username;

    private String nickname;

    private String deptName;

    /** 该 Token 登录时的客户端 IP */
    private String loginIp;

    /** 浏览器 / 客户端标识（User-Agent 截断到 200 字符） */
    private String browser;

    /** Token 创建时间（等价于登录时间） */
    private LocalDateTime loginTime;

    /**
     * 最后活动时间。
     *
     * <p>未开启 Sa-Token 的 {@code active-timeout} 时没有独立的活跃记录，
     * 后端会回退成登录时间，前端用同一列展示，避免出现空列。</p>
     */
    private LocalDateTime lastActiveTime;

    /** 剩余有效期（秒），由 Sa-Token 实时计算 */
    private long timeoutSeconds;

    /** 是否当前正在操作的会话 */
    private boolean current;

    /** 后端判定的可强退状态；前端只负责置灰，不重复实现策略 */
    private boolean kickable;

    /** 不可强退的原因，可强退时为空 */
    private String kickableReason;
}
