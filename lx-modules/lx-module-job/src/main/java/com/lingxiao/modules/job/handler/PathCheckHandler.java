package com.lingxiao.modules.job.handler;

import com.lingxiao.common.core.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传目录可写性检查。
 *
 * <p>文件中心最难排查的故障不是“上传报错”，而是“上传成功但文件没了”：
 * 目录被挂载成只读、磁盘满、容器重启后挂载点变化。建一个每天执行的任务，
 * 在磁盘出问题的当天就能从任务日志里看到失败，而不是等用户投诉。</p>
 *
 * <p>检查方式：在目录里创建临时文件、写 2 字节、再删掉。
 * 只检查“能创建”不够，写满磁盘时创建可能成功、写入才失败。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class PathCheckHandler implements JobHandler {

    private final String localPath;

    public PathCheckHandler(@Value("${lx.file.local-path:./data/upload}") String localPath) {
        this.localPath = localPath;
    }

    @Override
    public String name() {
        return "pathCheck";
    }

    @Override
    public String description() {
        return "检查本地上传目录是否存在且可写（创建 / 写入 / 删除临时文件）";
    }

    @Override
    public String execute(String params) throws Exception {
        Path dir = Paths.get(localPath).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path probe = Files.createTempFile(dir, ".lx-job-probe-", ".tmp");
        try {
            Files.write(probe, "ok".getBytes(StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(probe);
        }
        return "上传目录可写：" + dir;
    }
}
