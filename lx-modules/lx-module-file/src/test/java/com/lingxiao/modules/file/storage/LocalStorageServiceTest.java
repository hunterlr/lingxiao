package com.lingxiao.modules.file.storage;

import com.lingxiao.modules.file.config.FileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地存储测试。
 *
 * <p>用 JUnit {@link TempDir} 真写磁盘，不依赖 MySQL / Redis，CI 可直接跑。
 * 核心是路径安全：{@code ../} 必须被拒绝；文件不存在要抛异常而不是返回空资源。</p>
 */
@DisplayName("LocalStorageService 本地存储")
class LocalStorageServiceTest {

    @TempDir
    Path root;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() throws IOException {
        FileProperties properties = new FileProperties();
        properties.setLocalPath(root.toString());
        storage = new LocalStorageService(properties);
        storage.init();
    }

    @Test
    @DisplayName("store / load / delete 闭环")
    void storeLoadDelete() {
        String relative = "2026/11/15/hello.txt";

        String saved = storage.store(stream("你好 LingXiao"), relative);

        assertThat(saved).isEqualTo(relative);
        assertThat(Files.exists(root.resolve(relative))).isTrue();
        assertThat(storage.load(relative).exists()).isTrue();

        assertThat(storage.delete(relative)).isTrue();
        assertThat(Files.exists(root.resolve(relative))).isFalse();
        assertThat(storage.delete(relative)).isFalse();
    }

    @Test
    @DisplayName("load 不存在的文件抛 IllegalStateException")
    void loadMissing() {
        assertThatThrownBy(() -> storage.load("not-exists.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    @DisplayName("路径穿越 ../ 被拒绝（load / store / delete 三条入口一致）")
    void rejectsTraversal() {
        assertThatThrownBy(() -> storage.load("../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法文件路径");
        assertThatThrownBy(() -> storage.store(stream("x"), "../../evil.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法文件路径");
        assertThatThrownBy(() -> storage.delete("../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法文件路径");
    }

    @Test
    @DisplayName("存储类型固定为 local，供 StorageService 选择实现时判断")
    void typeIsLocal() {
        assertThat(storage.type()).isEqualTo("local");
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
