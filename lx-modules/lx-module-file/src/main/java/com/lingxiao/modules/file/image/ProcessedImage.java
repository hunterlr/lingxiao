package com.lingxiao.modules.file.image;

import lombok.Data;

/**
 * 一次图片处理的结果。
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code mainBytes}：准备落盘的“主图”字节。JPEG / PNG 会重新编码以剥离 EXIF、
 *       文本块等元数据；GIF / BMP 保持原样，避免破坏动图或多帧内容；</li>
 *   <li>{@code width / height}：主图实际宽高（已按 EXIF 方向纠正）；</li>
 *   <li>{@code thumbBytes / mediumBytes}：缩略图 / 中图字节，{@code null} 表示原图
 *       本身够小或生成失败，调用方应回退到主图。</li>
 * </ul>
 *
 * <p>本对象只描述“处理结果”，不负责落盘；路径由 {@code FileService} 统一拼接，
 * 以便存储实现（local / MinIO / OSS）只认相对路径。</p>
 */
@Data
public class ProcessedImage {

    private byte[] mainBytes;

    private int width;

    private int height;

    private byte[] thumbBytes;

    /** 缩略图输出格式：jpg / png，决定衍生文件扩展名与 Content-Type */
    private String thumbFormat;

    private byte[] mediumBytes;

    /** 中图输出格式：jpg / png */
    private String mediumFormat;
}
