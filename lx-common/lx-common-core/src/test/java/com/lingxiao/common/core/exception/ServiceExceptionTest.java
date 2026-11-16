package com.lingxiao.common.core.exception;

import com.lingxiao.common.core.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 业务异常测试。
 *
 * <p>约定：异常只用于可预期错误，必须携带稳定错误码；
 * 全局异常处理器依赖 {@code getCode()} 决定 HTTP 状态与响应体。</p>
 */
@DisplayName("ServiceException 业务异常")
class ServiceExceptionTest {

    @Test
    @DisplayName("只传消息：错误码回退 1000")
    void messageOnly() {
        ServiceException e = new ServiceException("保存失败");

        assertThat(e.getCode()).isEqualTo(ErrorCode.FAIL.getCode());
        assertThat(e.getMessage()).isEqualTo("保存失败");
    }

    @Test
    @DisplayName("传错误码：code 与 msg 都来自枚举")
    void errorCode() {
        ServiceException e = new ServiceException(ErrorCode.NOT_FOUND);

        assertThat(e.getCode()).isEqualTo(1004);
        assertThat(e.getMessage()).isEqualTo("资源不存在");
    }

    @Test
    @DisplayName("错误码 + 自定义消息：code 取枚举，消息可覆盖")
    void errorCodeWithMessage() {
        ServiceException e = new ServiceException(ErrorCode.BAD_REQUEST, "密码至少 8 位");

        assertThat(e.getCode()).isEqualTo(1001);
        assertThat(e.getMessage()).isEqualTo("密码至少 8 位");
    }

    @Test
    @DisplayName("是 RuntimeException，可被全局异常处理器捕获")
    void isRuntimeException() {
        assertThatThrownBy(() -> {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }).isInstanceOf(RuntimeException.class)
                .isInstanceOf(ServiceException.class);
    }
}
