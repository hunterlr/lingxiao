package com.lingxiao.modules.system.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.system.entity.SysPost;
import com.lingxiao.modules.system.service.SysPostService;
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
import java.util.List;

/**
 * 岗位管理接口。
 */
@RestController
@RequestMapping("/api/admin/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService postService;

    @RequiresPermission("platform:system:post:list")
    @GetMapping("/list")
    public R<PageResult<SysPost>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "20") long pageSize,
                                       @RequestParam(required = false) String postCode,
                                       @RequestParam(required = false) String postName,
                                       @RequestParam(required = false) String status) {
        return R.ok(postService.page(pageNum, pageSize, postCode, postName, status));
    }

    @RequiresPermission("platform:system:post:list")
    @GetMapping("/options")
    public R<List<SysPost>> options() {
        return R.ok(postService.options());
    }

    @RequiresPermission("platform:system:post:list")
    @GetMapping("/{postId}")
    public R<SysPost> detail(@PathVariable Long postId) {
        return R.ok(postService.detail(postId));
    }

    @RequiresPermission("platform:system:post:add")
    @Log(title = "岗位管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody SysPost post) {
        return R.ok(postService.create(post));
    }

    @RequiresPermission("platform:system:post:edit")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{postId}")
    public R<Void> edit(@PathVariable Long postId, @Valid @RequestBody SysPost post) {
        postService.update(postId, post);
        return R.ok();
    }

    @RequiresPermission("platform:system:post:remove")
    @Log(title = "岗位管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{postId}")
    public R<Void> remove(@PathVariable Long postId) {
        postService.delete(postId);
        return R.ok();
    }
}
