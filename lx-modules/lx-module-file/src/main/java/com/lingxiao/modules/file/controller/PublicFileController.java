package com.lingxiao.modules.file.controller;

import com.lingxiao.modules.file.entity.SysFile;
import com.lingxiao.modules.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 文件公开访问接口。
 *
 * <p>路径 {@code /api/file/{fileId}} 不在 {@code /api/admin/**} 下，因此不要求后台登录。
 * 适用于公告配图、CMS 文章图等公开内容；私有附件后续走签名 URL 或鉴权下载。</p>
 *
 * <p><b>尺寸参数：</b>{@code ?size=thumb|medium} 返回上传时生成的缩略图 / 中图；
 * 变体不存在（老文件、小图、非图片）时自动回退原图，不返回 404。</p>
 *
 * <p>安全：不暴露真实存储路径，只能通过 fileId 访问；响应头禁用 MIME 嗅探。</p>
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class PublicFileController {

    private final FileService fileService;

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> get(@PathVariable Long fileId,
                                        @RequestParam(required = false) String size) {
        SysFile file = fileService.getEntity(fileId);
        String path = fileService.resolvePath(file, size);
        Resource resource = fileService.loadPath(path);

        MediaType mediaType = MediaTypeFactory.getMediaType(path)
                .orElseGet(() -> MediaTypeFactory.getMediaType(file.getOriginalName())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM));
        ContentDisposition disposition = ContentDisposition.builder("inline")
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        boolean variant = "thumb".equalsIgnoreCase(size) || "medium".equalsIgnoreCase(size);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=" + (variant ? 86400 : 3600))
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
