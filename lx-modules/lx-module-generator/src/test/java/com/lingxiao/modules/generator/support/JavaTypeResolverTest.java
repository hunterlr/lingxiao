package com.lingxiao.modules.generator.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库类型 → Java 类型映射测试。
 *
 * <p>映射不到的必须回退 {@code String}，不能返回 null：
 * 生成物里出现 {@code private null xxx;} 比类型不准更难排查。</p>
 */
@DisplayName("JavaTypeResolver 类型映射")
class JavaTypeResolverTest {

    @Test
    @DisplayName("整数：BIGINT / INT(11) UNSIGNED / TINYINT")
    void integers() {
        assertThat(JavaTypeResolver.resolve("BIGINT")).isEqualTo("Long");
        assertThat(JavaTypeResolver.resolve("INT(11) UNSIGNED")).isEqualTo("Integer");
        assertThat(JavaTypeResolver.resolve("tinyint(1)")).isEqualTo("Integer");
        assertThat(JavaTypeResolver.resolve("SMALLINT")).isEqualTo("Integer");
        assertThat(JavaTypeResolver.resolve("SERIAL")).isEqualTo("Integer");
    }

    @Test
    @DisplayName("小数：DECIMAL / NUMBER 统一 BigDecimal，避免金额精度丢失")
    void decimals() {
        assertThat(JavaTypeResolver.resolve("DECIMAL(10,2)")).isEqualTo("BigDecimal");
        assertThat(JavaTypeResolver.resolve("NUMERIC(18,4)")).isEqualTo("BigDecimal");
        assertThat(JavaTypeResolver.resolve("NUMBER(20)")).isEqualTo("BigDecimal");
        assertThat(JavaTypeResolver.resolve("DOUBLE PRECISION")).isEqualTo("Double");
        assertThat(JavaTypeResolver.resolve("FLOAT")).isEqualTo("Float");
    }

    @Test
    @DisplayName("布尔与时间：DATE / DATETIME / 带时区 TIMESTAMP")
    void booleanAndTime() {
        assertThat(JavaTypeResolver.resolve("BIT")).isEqualTo("Boolean");
        assertThat(JavaTypeResolver.resolve("BOOLEAN")).isEqualTo("Boolean");
        assertThat(JavaTypeResolver.resolve("DATE")).isEqualTo("LocalDate");
        assertThat(JavaTypeResolver.resolve("DATETIME")).isEqualTo("LocalDateTime");
        assertThat(JavaTypeResolver.resolve("TIMESTAMP(3) WITH TIME ZONE")).isEqualTo("LocalDateTime");
        assertThat(JavaTypeResolver.resolve("TIME")).isEqualTo("LocalTime");
    }

    @Test
    @DisplayName("二进制：VARBINARY / BLOB / BYTEA")
    void binary() {
        assertThat(JavaTypeResolver.resolve("VARBINARY(255)")).isEqualTo("byte[]");
        assertThat(JavaTypeResolver.resolve("BLOB")).isEqualTo("byte[]");
        assertThat(JavaTypeResolver.resolve("BYTEA")).isEqualTo("byte[]");
    }

    @Test
    @DisplayName("未知类型、空值、带空格输入都回退 String")
    void fallback() {
        assertThat(JavaTypeResolver.resolve("NVARCHAR2(64)")).isEqualTo("String");
        assertThat(JavaTypeResolver.resolve("JSON")).isEqualTo("String");
        assertThat(JavaTypeResolver.resolve("  varchar(64)  ")).isEqualTo("String");
        assertThat(JavaTypeResolver.resolve(null)).isEqualTo("String");
    }
}
