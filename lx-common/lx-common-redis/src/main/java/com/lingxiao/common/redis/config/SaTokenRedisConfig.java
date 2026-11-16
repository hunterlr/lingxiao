package com.lingxiao.common.redis.config;

import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Sa-Token 会话落 Redis（可选）。
 *
 * <p><b>为什么不用官方自动装配：</b>{@code sa-token-redis-jackson} 在
 * {@code META-INF/spring.factories} 里把 {@link SaTokenDaoRedisJackson} 注册成
 * 自动装配类，只要依赖在 classpath 上就会无条件生效。那样
 * {@code lx.redis.enabled=false} 也会去连 Redis，部署环境没装 Redis 就起不来。</p>
 *
 * <p>所以 {@code application.yml} 用
 * {@code spring.autoconfigure.exclude: cn.dev33.satoken.dao.SaTokenDaoRedisJackson}
 * 排掉自动装配，再由本类按 {@code lx.redis.enabled} 显式注册。</p>
 *
 * <p>注意：{@link SaTokenDaoRedisJackson#init(RedisConnectionFactory)} 是
 * {@code @Autowired} 方法，手动 new 之后必须显式调用，否则模板没有连接工厂。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "lx.redis.enabled", havingValue = "true")
public class SaTokenRedisConfig {

    @Bean
    public SaTokenDaoRedisJackson saTokenDao(RedisConnectionFactory connectionFactory) {
        // 启动即探测：Redis 不可用时直接启动失败，而不是等第一个用户登录才 500
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "lx.redis.enabled=true 但 Redis 不可用，请检查 spring.redis.* 或改回 false", e);
        }
        SaTokenDaoRedisJackson dao = new SaTokenDaoRedisJackson();
        dao.init(connectionFactory);
        log.info("Sa-Token 会话存储：Redis（lx.redis.enabled=true）");
        return dao;
    }
}
