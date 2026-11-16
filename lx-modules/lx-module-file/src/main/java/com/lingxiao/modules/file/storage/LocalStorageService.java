package com.lingxiao.modules.file.storage;

import com.lingxiao.modules.file.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储。
 *
 * <p>路径安全：所有写入都先 {@code normalize()} 并校验仍在根目录内，
 * 防止 {@code ../} 路径穿越。文件名为 UUID，不使用用户原始文件名。</p>
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    private final FileProperties properties;
    private Path root;

    public LocalStorageService(FileProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        this.root = Paths.get(properties.getLocalPath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("本地文件存储根目录: {}", root);
    }

    @Override
    public String store(InputStream in, String relativePath) {
        try {
            Path target = resolveSafely(relativePath);
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return relativePath;
        } catch (IOException e) {
            throw new IllegalStateException("保存文件失败: " + relativePath, e);
        }
    }

    @Override
    public Resource load(String relativePath) {
        Path target = resolveSafely(relativePath);
        if (!Files.exists(target)) {
            throw new IllegalStateException("文件不存在: " + relativePath);
        }
        return new FileSystemResource(target);
    }

    @Override
    public boolean delete(String relativePath) {
        try {
            return Files.deleteIfExists(resolveSafely(relativePath));
        } catch (IOException e) {
            log.warn("删除文件失败: {}", relativePath, e);
            return false;
        }
    }

    @Override
    public String type() {
        return "local";
    }

    /**
     * 解析并校验路径，确保不会跳出存储根目录。
     */
    private Path resolveSafely(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法文件路径: " + relativePath);
        }
        return target;
    }
}
