package com.lingxiao.modules.demo.controller;

import com.lingxiao.common.core.domain.R;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可选模块示例接口。
 *
 * <p>{@code @ConditionalOnProperty} 保证关闭时整个 Controller Bean 不装配，
 * 因此接口返回 404，而不是“接口在但抛异常”。</p>
 */
@RestController
@RequestMapping("/api/admin/demo")
@ConditionalOnProperty(name = "lx.module.modules.demo.enabled", havingValue = "true")
public class DemoController {

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("pong");
    }
}
