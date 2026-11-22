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
 * <p>开发实现始终注册，作为 {@code mode=dev} 以及生产实现缺失时的兜底；
 * 具体使用哪一组由后台「会员管理 → 渠道配置」中的 {@code mode} 运行期决定，
 * 不再依赖 yml 开关。生产环境实现 {@link MemberSmsSender} /
 * {@link MemberEmailSender} 并注册为 Bean 后，后台即可切换到 {@code prod}。</p>
 *
 * <p>模块关闭时整个配置不装配，与其它会员 Bean 的开关语义保持一致。</p>
 */
@Configuration
@ConditionalOnProperty(name = "lx.module.modules.member.enabled", havingValue = "true")
public class MemberChannelConfiguration {

    @Bean
    public MemberSmsSender devMemberSmsSender() {
        return new DevMemberSmsSender();
    }

    @Bean
    public MemberEmailSender devMemberEmailSender() {
        return new DevMemberEmailSender();
    }
}
