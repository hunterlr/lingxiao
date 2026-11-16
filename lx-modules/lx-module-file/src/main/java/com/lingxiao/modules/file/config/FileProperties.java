package com.lingxiao.modules.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件模块配置。
 *
 * <p>YAML：</p>
 * <pre>
 * lx:
 *   file:
 *     storage: local
 *     local-path: ./data/upload
 *     max-size-mb: 10
 *     allowed-extensions: jpg,jpeg,png,gif,webp,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,txt
 *     thumb-size: 160
 *     medium-size: 800
 *     max-pixels: 20000000
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lx.file")
public class FileProperties {

    /** 存储类型，v1.0 只支持 local */
    private String storage = "local";

    /** 本地存储根目录，相对路径按启动目录解析 */
    private String localPath = "./data/upload";

    /** 单文件大小上限（MB） */
    private long maxSizeMb = 10;

    /** 允许的扩展名，逗号分隔；SVG 默认不放行（XSS 风险） */
    private String allowedExtensions = "jpg,jpeg,png,gif,webp,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,txt";

    /** 缩略图最长边（px）：文件网格用，约 160 够清晰又不浪费流量 */
    private int thumbSize = 160;

    /** 中图最长边（px）：详情预览 / 富文本插图用，避免直接加载原图 */
    private int mediumSize = 800;

    /**
     * 参与图片处理的最大像素数（宽 × 高）。
     *
     * <p>超过阈值只入库宽高、不生成变体，防止“10MB 的压缩炸弹”在解码时把堆打满。
     * 默认 2000 万像素，约等于 5000×4000 的手机原图。</p>
     */
    private long maxPixels = 20_000_000L;
}
