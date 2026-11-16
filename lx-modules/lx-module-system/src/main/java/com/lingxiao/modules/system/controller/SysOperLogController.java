package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.entity.SysOperLog;
import com.lingxiao.modules.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 操作日志接口。
 *
 * <p>路径用 {@code /system/oper-log} 而不是 {@code /monitor/oper-log}：
 * 日志数据属于系统模块，页面放在“系统监控”菜单下只是入口分组。
 * 权限码沿用菜单里已种好的 {@code platform:monitor:operlog:*}。</p>
 */
@RestController
@RequestMapping("/api/admin/system/oper-log")
@RequiredArgsConstructor
public class SysOperLogController {

    private final SysOperLogService operLogService;

    @RequiresPermission("platform:monitor:operlog:list")
    @GetMapping("/list")
    public R<PageResult<SysOperLog>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) Integer businessType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(operLogService.page(pageNum, pageSize, title, operName,
                businessType, status, beginTime, endTime));
    }

    @RequiresPermission("platform:monitor:operlog:list")
    @GetMapping("/{operId}")
    public R<SysOperLog> detail(@PathVariable Long operId) {
        return R.ok(operLogService.detail(operId));
    }

    /** 清空全部操作日志；这是危险操作，单独一个权限码。 */
    @RequiresPermission("platform:monitor:operlog:clean")
    @Log(title = "操作日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public R<Integer> clean() {
        return R.ok(operLogService.clean());
    }
}
