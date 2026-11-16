package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.entity.SysNotice;
import com.lingxiao.modules.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 公告管理接口。
 *
 * <p>{@code noticeContent} 是 WangEditor 5 提交的 HTML，服务端用 Jsoup 白名单清洗
 * （写入 + 读取两侧），前端可提交任意 HTML，但不能假设入库内容是干净的。</p>
 */
@RestController
@RequestMapping("/api/admin/system/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService noticeService;

    @RequiresPermission("platform:system:notice:list")
    @GetMapping("/list")
    public R<PageResult<SysNotice>> list(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "20") long pageSize,
                                         @RequestParam(required = false) String noticeTitle,
                                         @RequestParam(required = false) String noticeType,
                                         @RequestParam(required = false) String status) {
        return R.ok(noticeService.page(pageNum, pageSize, noticeTitle, noticeType, status));
    }

    @RequiresPermission("platform:system:notice:list")
    @GetMapping("/{noticeId}")
    public R<SysNotice> detail(@PathVariable Long noticeId) {
        return R.ok(noticeService.detail(noticeId));
    }

    @RequiresPermission("platform:system:notice:add")
    @Log(title = "公告管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysNotice notice) {
        return R.ok(noticeService.create(notice));
    }

    @RequiresPermission("platform:system:notice:edit")
    @Log(title = "公告管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{noticeId}")
    public R<Void> edit(@PathVariable Long noticeId, @Valid @RequestBody SysNotice notice) {
        noticeService.update(noticeId, notice);
        return R.ok();
    }

    @RequiresPermission("platform:system:notice:remove")
    @Log(title = "公告管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeId}")
    public R<Void> remove(@PathVariable Long noticeId) {
        noticeService.delete(noticeId);
        return R.ok();
    }
}
