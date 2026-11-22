package com.lingxiao.modules.member.service;

import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 会员端轻量限流。
 *
 * <p>按「动作 + 维度 + 值」做固定窗口计数，例如
 * {@code member:login:id:alice}、{@code member:login:ip:1.2.3.4}。
 * 每个窗口从第一次请求开始计时，窗口内超过阈值返回 {@link ErrorCode#TOO_MANY_REQUESTS}。</p>
 *
 * <p><b>为什么放内存：</b>限流只用于挡住短时间内的脚本重放，不是账号安全状态的唯一防线；
 * 登录失败仍然落 {@code mem_user.login_fail_count} / {@code lock_time}，重启不丢。
 * 单机部署下内存计数足够，且不依赖 Redis；多实例部署时各节点独立计数，
 * 后续可把本类替换为 Redis / 网关限流，业务层调用方式不变。</p>
 *
 * <p>过期窗口会在计数过程中按批次清理，避免按账号 / IP 无限增长。</p>
 */
@Service
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberRateLimitService {

    private static final int CLEANUP_INTERVAL = 1024;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();
    private final LongSupplier clock;

    public MemberRateLimitService() {
        this(System::currentTimeMillis);
    }

    /** 单测用：注入可控时钟，验证窗口过期后重新放行。 */
    MemberRateLimitService(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * 记一次请求并检查是否超限。
     *
     * @param key           限流键，调用方负责拼动作与维度
     * @param limit         窗口内允许的最大请求数；小于等于 0 表示关闭
     * @param windowMinutes 窗口长度（分钟）；小于等于 0 表示关闭
     * @throws ServiceException 超限时抛出 1006
     */
    public void check(String key, int limit, int windowMinutes) {
        if (limit <= 0 || windowMinutes <= 0) {
            return;
        }
        long now = clock.getAsLong();
        long windowMillis = TimeUnit.MINUTES.toMillis(windowMinutes);
        Window current = windows.compute(key, (k, old) -> {
            if (old == null || old.isExpired(now)) {
                return new Window(now, 1, windowMillis);
            }
            return new Window(old.start, old.count + 1, old.windowMillis);
        });

        if ((checks.incrementAndGet() & (CLEANUP_INTERVAL - 1)) == 0) {
            cleanup(now);
        }
        if (current.count > limit) {
            long remainingMillis = current.start + current.windowMillis - now;
            long remainingMinutes = Math.max(1, (remainingMillis + 59_999L) / 60_000L);
            throw new ServiceException(ErrorCode.TOO_MANY_REQUESTS,
                    "error.1006.rateLimit", remainingMinutes);
        }
    }

    private void cleanup(long now) {
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private static final class Window {

        private final long start;
        private final int count;
        private final long windowMillis;

        private Window(long start, int count, long windowMillis) {
            this.start = start;
            this.count = count;
            this.windowMillis = windowMillis;
        }

        private boolean isExpired(long now) {
            return now - start >= windowMillis;
        }
    }
}
