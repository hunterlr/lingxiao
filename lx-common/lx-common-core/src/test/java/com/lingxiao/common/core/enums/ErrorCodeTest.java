package com.lingxiao.common.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码契约测试。
 *
 * <p>错误码是前端分支判断的依据，只能新增不能改号。这里锁住三件事：
 * 号码唯一、文案非空、核心码段（0 / 1xxx / 2xxx / 3xxx / 9xxx）仍然存在。</p>
 */
@DisplayName("ErrorCode 错误码")
class ErrorCodeTest {

    @Test
    @DisplayName("错误码全局唯一")
    void codesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(seen.add(code.getCode()))
                    .as("错误码重复：%s", code.getCode())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("每个错误码都有非空中文文案")
    void messagesArePresent() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getMsg()).as("%s 的文案", code.name()).isNotBlank();
        }
    }

    @Test
    @DisplayName("核心错误码号码不变（前端与文档依赖）")
    void coreCodesAreStable() {
        assertThat(ErrorCode.SUCCESS.getCode()).isZero();
        assertThat(ErrorCode.FAIL.getCode()).isEqualTo(1000);
        assertThat(ErrorCode.BAD_REQUEST.getCode()).isEqualTo(1001);
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(1002);
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(1003);
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo(1004);
        assertThat(ErrorCode.LOGIN_FAILED.getCode()).isEqualTo(2001);
        assertThat(ErrorCode.ACCOUNT_LOCKED.getCode()).isEqualTo(2004);
        assertThat(ErrorCode.OLD_PASSWORD_ERROR.getCode()).isEqualTo(2005);
        assertThat(ErrorCode.FILE_TOO_LARGE.getCode()).isEqualTo(3003);
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo(9000);
    }

    @Test
    @DisplayName("错误码按段位分布：0 / 1xxx / 2xxx / 3xxx / 9xxx")
    void codeRanges() {
        Set<Integer> prefixes = Arrays.stream(ErrorCode.values())
                .map(code -> code.getCode() / 1000)
                .collect(Collectors.toSet());

        assertThat(prefixes).containsExactlyInAnyOrder(0, 1, 2, 3, 9);
    }
}
