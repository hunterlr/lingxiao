package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.dto.ServerInfoVO;
import com.lingxiao.modules.system.service.ServerMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务监控接口。
 *
 * <p>只读、无参数；每次请求现采一次，不做缓存。数据来自 JDK MXBean，
 * 不依赖任何外部监控组件，因此部署到没有 Prometheus 的环境也能用。</p>
 */
@RestController
@RequestMapping("/api/admin/monitor/server")
@RequiredArgsConstructor
public class MonitorServerController {

    private final ServerMonitorService serverMonitorService;

    @RequiresPermission("platform:monitor:server:list")
    @GetMapping
    public R<ServerInfoVO> info() {
        return R.ok(serverMonitorService.sample());
    }
}
