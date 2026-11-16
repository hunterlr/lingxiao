package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.dto.CacheInfoVO;
import com.lingxiao.modules.system.service.CacheMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 缓存监控接口。
 *
 * <p>Redis 关闭时返回本机 Caffeine 统计并明确提示；Redis 异常时降级返回，
 * 不让监控页本身成为故障点。权限码 {@code platform:monitor:cache:list}。</p>
 */
@RestController
@RequestMapping("/api/admin/monitor/cache")
@RequiredArgsConstructor
public class MonitorCacheController {

    private final CacheMonitorService cacheMonitorService;

    @RequiresPermission("platform:monitor:cache:list")
    @GetMapping
    public R<CacheInfoVO> info() {
        return R.ok(cacheMonitorService.sample());
    }
}
