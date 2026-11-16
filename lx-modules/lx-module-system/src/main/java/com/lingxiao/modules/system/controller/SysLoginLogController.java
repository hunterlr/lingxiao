package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.entity.SysLoginLog;
import com.lingxiao.modules.system.service.SysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 登录日志接口。
 *
 * <p>只提供列表与清空：登录日志是审计数据，不提供修改和新增
 * （新增只发生在登录流程内部）。清空需要独立的按钮权限码。</p>
 */
@RestController
@RequestMapping("/api/admin/system/login-log")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService loginLogService;

    @RequiresPermission("platform:monitor:loginlog:list")
    @GetMapping("/list")
    public R<PageResult<SysLoginLog>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(loginLogService.page(pageNum, pageSize, username, ip, status, beginTime, endTime));
    }

    /** 清空全部登录日志；危险操作，单独权限码。 */
    @RequiresPermission("platform:monitor:loginlog:clean")
    @Log(title = "登录日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public R<Integer> clean() {
        return R.ok(loginLogService.clean());
    }
}
