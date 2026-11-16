package com.lingxiao.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务监控快照。
 *
 * <p><b>数据全部来自 JDK 自带的 MXBean</b>，不引入 Prometheus / OSHI 等依赖：
 * v1.0 是基础框架，多一个监控组件就多一份运维成本；JDK 8 ~ 17 的
 * {@code OperatingSystemMXBean} / {@code MemoryMXBean} / {@code ThreadMXBean}
 * 已能覆盖 CPU、物理内存、JVM 堆、线程、磁盘这几项核心指标。</p>
 *
 * <p>所有比例字段都是 0~100 的百分数（保留两位小数），前端直接塞进进度条；
 * 后端不返回 0~1 的小数，避免每个页面都要记得乘 100。</p>
 */
@Data
public class ServerInfoVO {

    /** 采样时间，前端显示“数据截至” */
    private LocalDateTime sampleTime;

    private Cpu cpu;

    /** 物理内存（整机），不是 JVM 堆 */
    private Memory memory;

    private Jvm jvm;

    private SystemInfo system;

    private List<Disk> disks = new ArrayList<>();

    /** CPU 使用率 */
    @Data
    public static class Cpu {
        /** 逻辑核数 */
        private int cores;
        /** 整机 CPU 使用率（0~100），取不到时为 -1 */
        private double systemUsage;
        /** 当前 JVM 进程 CPU 使用率（0~100），取不到时为 -1 */
        private double processUsage;
        /** 系统 1 分钟平均负载；Windows 上通常为 -1 */
        private double loadAverage;
    }

    /** 物理内存 / 交换区 */
    @Data
    public static class Memory {
        private long total;
        private long used;
        private long free;
        /** 已用百分比（0~100） */
        private double usage;
        private long swapTotal;
        private long swapUsed;
    }

    /** JVM 运行时 */
    @Data
    public static class Jvm {
        private String name;
        private String version;
        private String vendor;
        private long startTime;
        private long uptime;
        private long heapUsed;
        private long heapCommitted;
        private long heapMax;
        private double heapUsage;
        private long nonHeapUsed;
        private int threadCount;
        private int peakThreadCount;
        private long totalStartedThreadCount;
        private int loadedClassCount;
        private long unloadedClassCount;
        private String javaVersion;
        private String javaHome;
        private String inputArguments;
    }

    /** 操作系统与运行目录 */
    @Data
    public static class SystemInfo {
        private String osName;
        private String osVersion;
        private String osArch;
        private String hostName;
        private String userDir;
        private int availableProcessors;
    }

    /** 磁盘分区 */
    @Data
    public static class Disk {
        /** 挂载点 / 盘符，例如 {@code C:\}、{@code /} */
        private String path;
        private String type;
        private long total;
        private long usable;
        private long used;
        /** 已用百分比（0~100） */
        private double usage;
    }
}
