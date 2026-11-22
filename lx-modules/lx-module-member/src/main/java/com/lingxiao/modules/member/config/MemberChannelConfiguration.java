package com.lingxiao.modules.member.config;

import com.lingxiao.modules.member.service.DevMemberEmailSender;
import com.lingxiao.modules.member.service.DevMemberSmsSender;
import com.lingxiao.modules.member.service.MemberEmailSender;
import com.lingxiao.modules.member.service.MemberSmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会员消息通道装配。
 *
 * <p>默认 {@code lx.member.channel.mode=dev}，只装配写日志的开发实现；
 * 生产环境实现 {@link MemberSmsSender} / {@link MemberEmailSender} 后把
 * mode 改成非 dev，本配置不再提供开发 Bean，由生产方式接管。</p>
 *
 * <p>模块关闭时整个配置不装配，与其它会员 Bean 的开关语义保持一致。</p>
 */
@Configuration
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberChannelConfiguration {

    @Bean
    @ConditionalOnProperty(name = "lx.member.channel.mode", havingValue = "dev", matchIfMissing = true)
    public MemberSmsSender devMemberSmsSender() {
        return new DevMemberSmsSender();
    }

    @Bean
    @ConditionalOnProperty(name = "lx.member.channel.mode", havingValue = "dev", matchIfMissing = true)
    public MemberEmailSender devMemberEmailSender() {
        return new DevMemberEmailSender();
    }
}
