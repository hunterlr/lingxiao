package com.lingxiao.common.core.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文案工具测试。
 *
 * <p>核心约定：翻译是“锦上添花”，任何异常都不能影响主流程。
 * 所以这里锁的不是“能不能翻译”，而是“翻译失败时一定退回默认文案”。</p>
 */
@DisplayName("MessageUtils 文案工具")
class MessageUtilsTest {

    @AfterEach
    void reset() {
        // 静态入口，用完必须还原，避免污染其它测试类
        MessageUtils.setResolver(null);
    }

    @Test
    @DisplayName("未接入语言包：返回默认文案")
    void fallbackWhenNotInitialized() {
        assertThat(MessageUtils.isInitialized()).isFalse();
        assertThat(MessageUtils.message("error.2001", "用户名或密码错误"))
                .isEqualTo("用户名或密码错误");
    }

    @Test
    @DisplayName("接入语言包：按 key 取到译文")
    void resolveWhenInitialized() {
        MessageUtils.setResolver((key, args) -> "error.2001".equals(key) ? "Bad credentials" : null);

        assertThat(MessageUtils.isInitialized()).isTrue();
        assertThat(MessageUtils.message("error.2001", "用户名或密码错误")).isEqualTo("Bad credentials");
    }

    @Test
    @DisplayName("语言包返回 null / 空串：仍退回默认文案")
    void fallbackWhenMissingKey() {
        MessageUtils.setResolver((key, args) -> null);

        assertThat(MessageUtils.message("error.9999", "兜底")).isEqualTo("兜底");
        assertThat(MessageUtils.message("", "兜底")).isEqualTo("兜底");
        assertThat(MessageUtils.message(null, "兜底")).isEqualTo("兜底");
    }

    @Test
    @DisplayName("语言包抛异常：不向外传播，退回默认文案")
    void fallbackWhenResolverThrows() {
        MessageUtils.setResolver((key, args) -> {
            throw new IllegalStateException("语言包炸了");
        });

        assertThat(MessageUtils.message("error.2001", "用户名或密码错误"))
                .isEqualTo("用户名或密码错误");
    }

    @Test
    @DisplayName("占位参数透传给解析器")
    void passArguments() {
        MessageUtils.setResolver((key, args) ->
                args == null ? "no-args" : "args=" + args.length + ",first=" + args[0]);

        assertThat(MessageUtils.message("error.2004.locked", new Object[]{10}, "兜底"))
                .isEqualTo("args=1,first=10");
    }

    @Test
    @DisplayName("错误码转键名：error.<code>")
    void errorKey() {
        assertThat(MessageUtils.errorKey(2001)).isEqualTo("error.2001");
        assertThat(MessageUtils.errorKey(0)).isEqualTo("error.0");
    }
}
