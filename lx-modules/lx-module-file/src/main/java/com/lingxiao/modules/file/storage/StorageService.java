package com.lingxiao.modules.file.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * 存储抽象。
 *
 * <p>v1.0 只有本地磁盘实现；后续 MinIO / OSS / COS / S3 都实现本接口，
 * 业务代码只认 {@code path} 与 {@link Resource}，不关心底层是谁。</p>
 *
 * <p>路径规则由实现方决定，但必须满足：</p>
 * <ul>
 *   <li>不直接使用用户原始文件名，避免路径穿越与中文乱码；</li>
 *   <li>返回的 path 是相对路径，存进 sys_file.path；</li>
 *   <li>删除只做物理删除，是否允许删由上层按 ref_count 判断。</li>
 * </ul>
 */
public interface StorageService {

    /**
     * 保存文件。
     *
     * @param in           输入流
     * @param relativePath 相对路径，例如 {@code notice/2026/11/uuid.jpg}
     * @return 实际存储的相对路径
     */
    String store(InputStream in, String relativePath);

    /**
     * 读取文件。
     *
     * @param relativePath 相对路径
     */
    Resource load(String relativePath);

    /**
     * 删除文件；不存在时返回 false，不抛异常。
     */
    boolean delete(String relativePath);

    /**
     * 存储类型标识：local / minio / oss ...
     */
    String type();
}
