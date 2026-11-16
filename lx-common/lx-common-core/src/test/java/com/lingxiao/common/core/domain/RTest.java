package com.lingxiao.common.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lingxiao.common.core.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应体测试。
 *
 * <p>前端所有分支都依赖 {@code code == 0}，这里的用例等价于“接口契约测试”：
 * 成功码、失败码、msg 兜底、data 透传、序列化不带 success 字段。</p>
 */
@DisplayName("R 统一响应体")
class RTest {

    @Test
    @DisplayName("成功：code=0、msg=操作成功、data 为空")
    void okWithoutData() {
        R<Void> r = R.ok();

        assertThat(r.getCode()).isZero();
        assertThat(r.getMsg()).isEqualTo(ErrorCode.SUCCESS.getMsg());
        assertThat(r.getData()).isNull();
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("成功：data 原样透传，不被包装或改写")
    void okWithData() {
        R<String> r = R.ok("lingxiao");

        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualTo("lingxiao");
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("失败：只给 msg 时使用 FAIL(1000)")
    void failWithMessage() {
        R<Void> r = R.fail("业务失败");

        assertThat(r.getCode()).isEqualTo(ErrorCode.FAIL.getCode());
        assertThat(r.getMsg()).isEqualTo("业务失败");
        assertThat(r.getData()).isNull();
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("失败：按错误码取标准文案")
    void failWithErrorCode() {
        R<Void> r = R.fail(ErrorCode.FORBIDDEN);

        assertThat(r.getCode()).isEqualTo(1003);
        assertThat(r.getMsg()).isEqualTo("没有权限执行该操作");
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("失败：code 与 msg 可自定义，用于动态提示")
    void failWithCustomCode() {
        R<Void> r = R.fail(2004, "账号已锁定，剩余 3 分钟");

        assertThat(r.getCode()).isEqualTo(2004);
        assertThat(r.getMsg()).isEqualTo("账号已锁定，剩余 3 分钟");
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("isSuccess 必须被 @JsonIgnore 标记，不能出现在响应 JSON 里")
    void isSuccessIsHiddenFromJson() throws NoSuchMethodException {
        Method method = R.class.getMethod("isSuccess");

        assertThat(method.isAnnotationPresent(JsonIgnore.class)).isTrue();
    }
}
