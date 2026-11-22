package com.lingxiao.modules.member.service;

import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会员限流测试。
 *
 * <p>锁定固定窗口的边界：窗口内前 N 次放行，第 N+1 次拒绝；窗口过期后重新放行；
 * 不同 Key 互不影响。时钟由测试注入，不等待真实分钟。</p>
 */
@DisplayName("MemberRateLimitService 会员限流")
class MemberRateLimitServiceTest {

    @Test
    @DisplayName("窗口内超过阈值返回 1006，并给出重试分钟")
    void rejectsAfterLimit() {
        long[] now = {1_000L};
        MemberRateLimitService service = new MemberRateLimitService(() -> now[0]);

        service.check("login:alice", 3, 10);
        service.check("login:alice", 3, 10);
        service.check("login:alice", 3, 10);

        assertThatThrownBy(() -> service.check("login:alice", 3, 10))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> {
                    ServiceException ex = (ServiceException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode());
                    assertThat(ex.getMessageKey()).isEqualTo("error.1006.rateLimit");
                });
    }

    @Test
    @DisplayName("不同 Key 独立计数：一个账号超限不影响另一个")
    void keysAreIndependent() {
        MemberRateLimitService service = new MemberRateLimitService(() -> 1_000L);

        service.check("login:alice", 1, 10);
        assertThatThrownBy(() -> service.check("login:alice", 1, 10))
                .isInstanceOf(ServiceException.class);
        assertThatCode(() -> service.check("login:bob", 1, 10)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("窗口过期后重新计数，不会永久封禁")
    void windowResets() {
        long[] now = {1_000L};
        MemberRateLimitService service = new MemberRateLimitService(() -> now[0]);

        service.check("register:alice", 1, 10);
        assertThatThrownBy(() -> service.check("register:alice", 1, 10))
                .isInstanceOf(ServiceException.class);

        now[0] += 10 * 60_000L;
        assertThatCode(() -> service.check("register:alice", 1, 10)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("limit / window 非正数时关闭限流")
    void nonPositiveDisables() {
        MemberRateLimitService service = new MemberRateLimitService(() -> 1_000L);

        assertThatCode(() -> {
            service.check("any", 0, 10);
            service.check("any", 1, 0);
        }).doesNotThrowAnyException();
    }
}
