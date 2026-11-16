package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.dto.KickoutRequest;
import com.lingxiao.modules.system.dto.KickoutResultVO;
import com.lingxiao.modules.system.dto.OnlineUserVO;
import com.lingxiao.modules.system.service.OnlineUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 在线用户接口。
 *
 * <p>路径 {@code /api/admin/monitor/online} 与菜单路径一致，权限码沿用
 * {@code platform:monitor:online:*}；列表与强退分开授权。</p>
 *
 * <p>强退接口的两点刻意设计：</p>
 * <ul>
 *   <li>用 {@code POST + 请求体}，不走 URL 参数，避免 Token 被访问日志、代理日志记录；</li>
 *   <li>{@code @Log(saveRequestData = false)}，操作日志只记“谁在什么时候踢了人”，
 *       不记会话标识本身。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/monitor/online")
@RequiredArgsConstructor
public class MonitorOnlineController {

    private final OnlineUserService onlineUserService;

    @RequiresPermission("platform:monitor:online:list")
    @GetMapping("/list")
    public R<PageResult<OnlineUserVO>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip) {
        return R.ok(onlineUserService.page(pageNum, pageSize, username, ip));
    }

    /** 强制下线：支持单个 / 批量。 */
    @RequiresPermission("platform:monitor:online:kickout")
    @Log(title = "在线用户", businessType = BusinessType.FORCE_LOGOUT, saveRequestData = false)
    @PostMapping("/kickout")
    public R<KickoutResultVO> kickout(@Valid @RequestBody KickoutRequest request) {
        return R.ok(onlineUserService.kickout(request.getTokenIds()));
    }
}
