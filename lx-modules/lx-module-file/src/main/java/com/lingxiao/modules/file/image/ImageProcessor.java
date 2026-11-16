package com.lingxiao.modules.file.image;

import com.lingxiao.modules.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Locale;

/**
 * 图片基础处理（v1.0 W3 门禁）。
 *
 * <p>只做三件对后台最有用的事，不做在线裁剪 / 水印编辑器：</p>
 * <ol>
 *   <li><b>读宽高</b>：用 {@link ImageReader} 只读文件头，不整图解码，避免大图占内存；</li>
 *   <li><b>剥离元数据</b>：JPEG / PNG 重新编码，EXIF（含拍摄地点、设备、GPS）不会跟着文件走；
 *       同时按 EXIF Orientation 纠正方向，手机竖拍照片不会横着显示；</li>
 *   <li><b>生成缩略图 / 中图</b>：网格用 thumb（默认 160px），详情预览用 medium（默认 800px），
 *       列表页不再直接加载几 MB 的原图。</li>
 * </ol>
 *
 * <p><b>安全阀：</b>先读宽高，像素数超过 {@code lx.file.max-pixels}（默认 2000 万）时
 * 只入库宽高、不生成变体，防止“10MB 压缩炸弹”在解码阶段把堆打满。</p>
 *
 * <p><b>格式边界：</b>只处理 ImageIO 能读的 jpg / jpeg / png / gif / bmp。
 * webp 在 JDK 8 没有内置解码器，保持原样入库（不生成变体），前端仍可通过
 * {@code ?size=thumb} 回退到原图。任何一步失败都记录日志并回退原图，<b>不影响上传成功</b>。</p>
 *
 * <p>衍生图只写磁盘，不写 {@code sys_file}：它们不是独立业务文件，不参与秒传、改名与引用计数。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageProcessor {

    private final FileProperties properties;

    /**
     * 处理图片。
     *
     * @param bytes 上传文件字节
     * @param ext   扩展名（大小写不敏感）
     * @return 处理结果；不是受支持图片、文件头损坏或无法读取时返回 {@code null}
     */
    public ProcessedImage process(byte[] bytes, String ext) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String format = normalizeFormat(ext);
        if (format == null) {
            return null;
        }
        int[] size = readSize(bytes);
        if (size == null) {
            log.debug("无法识别图片尺寸，按普通文件处理: ext={}", ext);
            return null;
        }

        ProcessedImage result = new ProcessedImage();
        result.setMainBytes(bytes);
        result.setWidth(size[0]);
        result.setHeight(size[1]);

        long pixels = (long) size[0] * size[1];
        if (pixels > properties.getMaxPixels()) {
            log.warn("图片 {}x{} 超过 max-pixels={}，只入库宽高，不生成变体",
                    size[0], size[1], properties.getMaxPixels());
            return result;
        }

        byte[] main = stripMetadata(bytes, format);
        if (main != null) {
            int[] normalized = readSize(main);
            if (normalized != null) {
                result.setMainBytes(main);
                result.setWidth(normalized[0]);
                result.setHeight(normalized[1]);
            }
        }

        // JPEG 用 jpg 输出体积小；PNG / GIF / BMP 用 png 输出，保留透明通道
        String variantFormat = "jpg".equals(format) ? "jpg" : "png";
        int maxSide = Math.max(result.getWidth(), result.getHeight());
        if (maxSide > properties.getThumbSize()) {
            byte[] thumb = resize(result.getMainBytes(), properties.getThumbSize(), variantFormat);
            if (thumb != null) {
                result.setThumbBytes(thumb);
                result.setThumbFormat(variantFormat);
            }
        }
        if (maxSide > properties.getMediumSize()) {
            byte[] medium = resize(result.getMainBytes(), properties.getMediumSize(), variantFormat);
            if (medium != null) {
                result.setMediumBytes(medium);
                result.setMediumFormat(variantFormat);
            }
        }
        return result;
    }

    /**
     * 重新编码 JPEG / PNG，剥离 EXIF 与文本元数据，并按 EXIF Orientation 纠正方向。
     *
     * <p>GIF / BMP 不重编码：GIF 会丢动画、BMP 本来就没有 EXIF，保持原样更安全。</p>
     *
     * @return 新字节；失败返回 {@code null}，调用方继续用原图
     */
    private byte[] stripMetadata(byte[] bytes, String format) {
        if (!"jpg".equals(format) && !"png".equals(format)) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(bytes.length / 2, 1024));
            Thumbnails.of(new ByteArrayInputStream(bytes))
                    .scale(1.0)
                    .rotate(exifRotation(bytes))
                    .outputFormat(format)
                    .outputQuality(0.9)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("图片元数据剥离失败，保留原图: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 JPEG 的 APP1（EXIF）里读 Orientation，换算成顺时针旋转角度。
     *
     * <p>为什么不用 Thumbnailator 的 {@code useExifOrientation(true)}：0.4.8 在
     * InputStream 源上不会读取 EXIF 元数据，实测方向不会生效。这里自己解析 APP1，
     * 只支持最常见的 3 / 6 / 8（180°、90°、270°）；镜像类方向（2/4/5/7）
     * 保持原样，避免引入翻转逻辑。</p>
     */
    private double exifRotation(byte[] bytes) {
        int orientation = readExifOrientation(bytes);
        if (orientation == 3) {
            return 180.0;
        }
        if (orientation == 6) {
            return 90.0;
        }
        if (orientation == 8) {
            return 270.0;
        }
        return 0.0;
    }

    /**
     * 解析 JPEG 的 EXIF Orientation；不是 JPEG、没有 EXIF 或解析失败都返回 1（正常方向）。
     */
    private int readExifOrientation(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return 1;
        }
        if ((bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int pos = 2;
        while (pos + 4 <= bytes.length) {
            if ((bytes[pos] & 0xFF) != 0xFF) {
                pos++;
                continue;
            }
            int marker = bytes[pos + 1] & 0xFF;
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            // 无长度字段的标记：SOI、TEM、RSTn
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            // 图像数据开始或结束，后面不会再有 APP1
            if (marker == 0xDA || marker == 0xD9) {
                break;
            }
            int len = ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
            if (len < 2 || pos + 2 + len > bytes.length) {
                break;
            }
            if (marker == 0xE1 && len >= 8
                    && bytes[pos + 4] == 'E' && bytes[pos + 5] == 'x'
                    && bytes[pos + 6] == 'i' && bytes[pos + 7] == 'f'
                    && bytes[pos + 8] == 0 && bytes[pos + 9] == 0) {
                return parseTiffOrientation(bytes, pos + 10, pos + 2 + len);
            }
            pos += 2 + len;
        }
        return 1;
    }

    /**
     * 解析 EXIF 里的 TIFF 结构，找到 IFD0 的 Orientation 标签（0x0112）。
     */
    private int parseTiffOrientation(byte[] bytes, int tiff, int end) {
        try {
            if (tiff + 8 > end) {
                return 1;
            }
            boolean little;
            if (bytes[tiff] == 'I' && bytes[tiff + 1] == 'I') {
                little = true;
            } else if (bytes[tiff] == 'M' && bytes[tiff + 1] == 'M') {
                little = false;
            } else {
                return 1;
            }
            long ifd = tiff + readUint32(bytes, tiff + 4, little);
            if (ifd + 2 > end) {
                return 1;
            }
            int count = readUint16(bytes, (int) ifd, little);
            for (int i = 0; i < count; i++) {
                long entry = ifd + 2 + (long) i * 12;
                if (entry + 12 > end) {
                    return 1;
                }
                if (readUint16(bytes, (int) entry, little) == 0x0112) {
                    int type = readUint16(bytes, (int) entry + 2, little);
                    long number = readUint32(bytes, (int) entry + 4, little);
                    if (type == 3 && number >= 1) {
                        return readUint16(bytes, (int) entry + 8, little);
                    }
                    return 1;
                }
            }
        } catch (RuntimeException e) {
            log.debug("解析 EXIF 方向失败: {}", e.getMessage());
        }
        return 1;
    }

    private int readUint16(byte[] bytes, int offset, boolean little) {
        int first = bytes[offset] & 0xFF;
        int second = bytes[offset + 1] & 0xFF;
        return little ? (second << 8) | first : (first << 8) | second;
    }

    private long readUint32(byte[] bytes, int offset, boolean little) {
        long first = bytes[offset] & 0xFFL;
        long second = bytes[offset + 1] & 0xFFL;
        long third = bytes[offset + 2] & 0xFFL;
        long fourth = bytes[offset + 3] & 0xFFL;
        if (little) {
            return (fourth << 24) | (third << 16) | (second << 8) | first;
        }
        return (first << 24) | (second << 16) | (third << 8) | fourth;
    }

    /**
     * 生成最长边不超过 {@code maxSide} 的变体；小图不会被放大。
     */
    private byte[] resize(byte[] bytes, int maxSide, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(bytes.length / 8, 1024));
            Thumbnails.of(new ByteArrayInputStream(bytes))
                    .size(maxSide, maxSide)
                    .outputFormat(format)
                    .outputQuality(0.85)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("生成图片变体失败（最长边 {}）: {}", maxSide, e.getMessage());
            return null;
        }
    }

    /**
     * 只读文件头拿宽高，不整图解码。
     *
     * @return {@code [width, height]}；无法识别时返回 {@code null}
     */
    private int[] readSize(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            log.debug("读取图片尺寸失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 扩展名归一：jpeg → jpg；只放行 ImageIO 在 JDK 8 能稳定解码的格式。
     */
    private String normalizeFormat(String ext) {
        if (!StringUtils.hasText(ext)) {
            return null;
        }
        String lower = ext.toLowerCase(Locale.ROOT);
        if ("jpeg".equals(lower)) {
            return "jpg";
        }
        if ("jpg".equals(lower) || "png".equals(lower) || "gif".equals(lower) || "bmp".equals(lower)) {
            return lower;
        }
        return null;
    }
}
