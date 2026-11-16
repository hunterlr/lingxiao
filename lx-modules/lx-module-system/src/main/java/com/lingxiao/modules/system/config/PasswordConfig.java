package com.lingxiao.modules.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器。
 *
 * <p>只引入 spring-security-crypto，不引入完整 Spring Security：
 * 我们要的是 BCrypt，不是默认登录页和过滤器链。</p>
 *
 * <p>BCrypt 每次加盐，同一个密码两次哈希结果不同，校验必须用 matches，
 * 不能用字符串相等。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
