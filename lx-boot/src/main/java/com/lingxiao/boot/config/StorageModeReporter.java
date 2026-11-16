package com.lingxiao.boot.config;

import com.lingxiao.modules.system.service.captcha.CaptchaStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时打印存储模式。
 *
 * <p>部署后最常见的问题是“以为开了 Redis，其实还是内存”，
 * 或者反过来“多个实例各存各的”。把结论打进启动日志，比事后翻配置可靠。</p>
 *
 * <p>本类不用 Lombok：lx-boot 是纯装配模块，不为一个日志类引入注解处理器。</p>
 */
@Component
public class StorageModeReporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StorageModeReporter.class);

    @Value("${lx.redis.enabled:false}")
    private boolean redisEnabled;

    private final CaptchaStore captchaStore;

    public StorageModeReporter(CaptchaStore captchaStore) {
        this.captchaStore = captchaStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("存储模式 | Redis 开关={} | Sa-Token 会话={} | 验证码={}",
                redisEnabled,
                redisEnabled ? "Redis" : "内存",
                captchaStore.mode());
    }
}
