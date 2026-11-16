package com.lingxiao.modules.job.service;

import com.lingxiao.common.core.job.JobHandler;
import com.lingxiao.modules.job.dto.JobHandlerVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行器注册表。
 *
 * <p>启动时把容器里所有 {@link JobHandler} 按 {@code name()} 建索引。
 * 重名不阻断启动（否则一个手误让整个系统起不来），但会打 WARN 并保留先注册的那个，
 * 任务表单里也只展示一个。</p>
 *
 * <p>注册表是任务保存时的校验依据：执行器不存在直接拒绝，
 * 不把“注定失败的任务”留到凌晨才暴露。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> handlerList) {
        Map<String, JobHandler> map = new LinkedHashMap<>();
        for (JobHandler handler : handlerList) {
            JobHandler previous = map.putIfAbsent(handler.name(), handler);
            if (previous != null) {
                log.warn("执行器名称重复：{}（{} 与 {}），保留先注册的",
                        handler.name(), previous.getClass().getName(), handler.getClass().getName());
            }
        }
        this.handlers = Collections.unmodifiableMap(map);
        log.info("定时任务执行器已加载 {} 个：{}", map.size(), map.keySet());
    }

    /** 找不到返回 {@code null}，由调用方决定是报错还是跳过。 */
    public JobHandler get(String name) {
        return name == null ? null : handlers.get(name);
    }

    public List<JobHandlerVO> options() {
        List<JobHandlerVO> result = new ArrayList<>(handlers.size());
        handlers.forEach((name, handler) -> result.add(new JobHandlerVO(name, handler.description())));
        return result;
    }
}
