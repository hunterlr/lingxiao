package com.lingxiao.modules.generator.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表名 / 列名 → Java 命名测试。
 *
 * <p>生成物能不能编译，一半取决于这里：字段名撞 Java 关键字、名字里有连字符、
 * 前后空格没处理，都会让生成的 Entity 直接报错。</p>
 */
@DisplayName("NamingUtils 命名转换")
class NamingUtilsTest {

    @Test
    @DisplayName("下划线表名：sys_user → SysUser / sysUser")
    void snakeCase() {
        assertThat(NamingUtils.toPascalCase("sys_user")).isEqualTo("SysUser");
        assertThat(NamingUtils.toCamelCase("sys_user")).isEqualTo("sysUser");
    }

    @Test
    @DisplayName("多段 / 连续下划线 / 连字符都能折叠")
    void messySeparators() {
        assertThat(NamingUtils.toCamelCase("create_time")).isEqualTo("createTime");
        assertThat(NamingUtils.toCamelCase("  SYS__USER  ")).isEqualTo("sysUser");
        assertThat(NamingUtils.toCamelCase("create-time")).isEqualTo("createTime");
        assertThat(NamingUtils.toCamelCase("a_b_c")).isEqualTo("aBC");
    }

    @Test
    @DisplayName("空值与空白返回空串，由调用方给业务错误")
    void blankInput() {
        assertThat(NamingUtils.toCamelCase(null)).isEmpty();
        assertThat(NamingUtils.toCamelCase("")).isEmpty();
        assertThat(NamingUtils.toCamelCase("   ")).isEmpty();
        assertThat(NamingUtils.toPascalCase(null)).isEmpty();
    }

    @Test
    @DisplayName("Java 关键字追加 Field，避免生成非法字段名")
    void javaKeywords() {
        assertThat(NamingUtils.safeJavaField("class")).isEqualTo("classField");
        assertThat(NamingUtils.safeJavaField("record")).isEqualTo("recordField");
        assertThat(NamingUtils.safeJavaField("var")).isEqualTo("varField");
        assertThat(NamingUtils.safeJavaField("user")).isEqualTo("user");
        assertThat(NamingUtils.safeJavaField(null)).isNull();
        assertThat(NamingUtils.safeJavaField("")).isEmpty();
    }

    @Test
    @DisplayName("capitalize 用于 getter / setter 拼接")
    void capitalize() {
        assertThat(NamingUtils.capitalize("userName")).isEqualTo("UserName");
        assertThat(NamingUtils.capitalize("a")).isEqualTo("A");
        assertThat(NamingUtils.capitalize(null)).isNull();
        assertThat(NamingUtils.capitalize("")).isEmpty();
    }
}
