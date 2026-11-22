package com.lingxiao.modules.member.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 会员密码编码器。
 *
 * <p>不直接注入 Spring 的 {@code PasswordEncoder}：那个 Bean 属于系统模块，
 * 会员模块不依赖系统模块；这里自带一个 BCrypt 实例，哈希格式与后台一致，
 * 但两个域的密码互不读取。BCrypt 每次加盐，校验必须用 {@code matches}。</p>
 */
@Component
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberPasswordEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encodedPassword != null && delegate.matches(rawPassword, encodedPassword);
    }
}
