package com.lingxiao.modules.file.controller;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.domain.R;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.log.annotation.Log;
import com.lingxiao.common.log.enums.BusinessType;
import com.lingxiao.common.security.annotation.RequiresPermission;
import com.lingxiao.modules.file.dto.FileVO;
import com.lingxiao.modules.file.dto.RenameRequest;
import com.lingxiao.modules.file.entity.SysFileNameLog;
import com.lingxiao.modules.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

/**
 * 文件中心后台接口。
 *
 * <p>全部在 {@code /api/admin/file} 下，需要平台域登录。</p>
 *
 * <p><b>权限：</b>上传 / 改名 / 浏览分别校验 {@code platform:file:upload}、
 * {@code platform:file:rename}、{@code platform:file:list}。
 * 富文本插图的图片上传也走 `/upload`，所以给“能编辑公告但不能进文件中心”的
 * 角色授权时，别忘了单独勾上“文件上传”。</p>
 */
@RestController
@RequestMapping("/api/admin/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @RequiresPermission("platform:file:upload")
    @Log(title = "文件上传", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public R<FileVO> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam(defaultValue = "common") String bizType,
                            @RequestParam(required = false) String remark) {
        return R.ok(fileService.upload(file, bizType, remark));
    }

    /**
     * 上传个人头像。
     *
     * <p>不校验 {@code platform:file:upload}：改头像属于个人自助能力，
     * 普通角色不该为了换头像被要求进文件中心。仍受全局文件类型 / 大小限制，
     * 并额外限定图片扩展名，避免把这里当成通用上传通道。</p>
     *
     * <p>返回 {@code fileId}，由前端写回 {@code sys_user.avatar}；
     * 第三方登录回写的 {@code http(s)} 外链不走这个接口。</p>
     */
    @Log(title = "头像上传", businessType = BusinessType.INSERT)
    @PostMapping("/avatar")
    public R<FileVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean image = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp");
        if (!image) {
            throw new ServiceException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "头像只支持图片文件");
        }
        return R.ok(fileService.upload(file, "avatar", null));
    }

    @RequiresPermission("platform:file:list")
    @GetMapping("/list")
    public R<PageResult<FileVO>> list(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "20") long pageSize,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String bizType,
                                      @RequestParam(required = false) Boolean imageOnly) {
        return R.ok(fileService.list(pageNum, pageSize, keyword, bizType, imageOnly));
    }

    @RequiresPermission("platform:file:list")
    @GetMapping("/{fileId}")
    public R<FileVO> detail(@PathVariable Long fileId) {
        return R.ok(fileService.detail(fileId));
    }

    @RequiresPermission("platform:file:rename")
    @Log(title = "文件改名", businessType = BusinessType.UPDATE)
    @PutMapping("/{fileId}/rename")
    public R<FileVO> rename(@PathVariable Long fileId, @Valid @RequestBody RenameRequest request) {
        return R.ok(fileService.rename(fileId, request.getDisplayName()));
    }

    @RequiresPermission("platform:file:list")
    @GetMapping("/{fileId}/name-history")
    public R<List<SysFileNameLog>> nameHistory(@PathVariable Long fileId) {
        return R.ok(fileService.nameHistory(fileId));
    }
}
