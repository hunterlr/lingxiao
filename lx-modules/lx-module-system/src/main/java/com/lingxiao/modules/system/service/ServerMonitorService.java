package com.lingxiao.modules.system.service;

import com.lingxiao.modules.system.dto.ServerInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务监控（W4 系统监控）。
 *
 * <p><b>为什么不引入 OSHI / Prometheus：</b>v1.0 的监控页只需要“现在这台机器怎么样”，
 * JDK 自带的 MXBean 已经能拿到 CPU、物理内存、JVM 堆、线程、类加载；
 * 磁盘用 {@link FileStore} 读取。多一个依赖就多一次升级与兼容性评估，
 * 对基础框架不划算。</p>
 *
 * <p><b>兼容 JDK 8 / 11 / 17：</b>{@code getCpuLoad()} 是 JDK 14 才加的，
 * {@code getSystemCpuLoad()} 在 17 里已标记废弃但仍在。这里用反射先找新方法、
 * 失败再退回旧方法，编译期不绑定某一个 JDK 的签名。</p>
 *
 * <p><b>取不到就返回 -1：</b>CPU 使用率在部分容器 / 受限环境里不可用。
 * 返回 -1 比伪造 0 更诚实，前端显示“不可用”。</p>
 */
@Slf4j
@Service
public class ServerMonitorService {

    /**
     * 采集一次服务指标。
     *
     * <p>只读，不缓存：监控页刷新时应该看到最新值；单次采集是毫秒级，
     * 也不需要为它加缓存层。</p>
     */
    public ServerInfoVO sample() {
        ServerInfoVO vo = new ServerInfoVO();
        vo.setSampleTime(LocalDateTime.now());

        java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean osBean =
                baseBean instanceof com.sun.management.OperatingSystemMXBean
                        ? (com.sun.management.OperatingSystemMXBean) baseBean : null;

        vo.setCpu(readCpu(osBean, baseBean));
        vo.setMemory(readMemory(osBean));
        vo.setJvm(readJvm());
        vo.setSystem(readSystem(baseBean));
        vo.setDisks(readDisks());
        return vo;
    }

    private ServerInfoVO.Cpu readCpu(com.sun.management.OperatingSystemMXBean osBean,
                                     java.lang.management.OperatingSystemMXBean baseBean) {
        ServerInfoVO.Cpu cpu = new ServerInfoVO.Cpu();
        cpu.setCores(baseBean.getAvailableProcessors());
        cpu.setLoadAverage(baseBean.getSystemLoadAverage());
        // JDK 14+ 叫 getCpuLoad，8 ~ 13 叫 getSystemCpuLoad
        cpu.setSystemUsage(invokePercent(osBean, "getCpuLoad", "getSystemCpuLoad"));
        cpu.setProcessUsage(invokePercent(osBean, "getProcessCpuLoad"));
        return cpu;
    }

    private ServerInfoVO.Memory readMemory(com.sun.management.OperatingSystemMXBean osBean) {
        ServerInfoVO.Memory memory = new ServerInfoVO.Memory();
        if (osBean == null) {
            return memory;
        }
        long total = osBean.getTotalPhysicalMemorySize();
        long free = osBean.getFreePhysicalMemorySize();
        memory.setTotal(total);
        memory.setFree(free);
        memory.setUsed(Math.max(total - free, 0));
        memory.setUsage(percent(memory.getUsed(), total));

        long swapTotal = osBean.getTotalSwapSpaceSize();
        long swapFree = osBean.getFreeSwapSpaceSize();
        memory.setSwapTotal(Math.max(swapTotal, 0));
        memory.setSwapUsed(Math.max(swapTotal - swapFree, 0));
        return memory;
    }

    private ServerInfoVO.Jvm readJvm() {
        ServerInfoVO.Jvm jvm = new ServerInfoVO.Jvm();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();

        jvm.setName(runtime.getVmName());
        jvm.setVersion(runtime.getVmVersion());
        jvm.setVendor(runtime.getVmVendor());
        jvm.setStartTime(runtime.getStartTime());
        jvm.setUptime(runtime.getUptime());
        jvm.setInputArguments(String.join(" ", runtime.getInputArguments()));

        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        jvm.setHeapUsed(heap.getUsed());
        jvm.setHeapCommitted(heap.getCommitted());
        jvm.setHeapMax(heap.getMax());
        jvm.setHeapUsage(percent(heap.getUsed(), heap.getMax()));
        jvm.setNonHeapUsed(nonHeap.getUsed());

        jvm.setThreadCount(threads.getThreadCount());
        jvm.setPeakThreadCount(threads.getPeakThreadCount());
        jvm.setTotalStartedThreadCount(threads.getTotalStartedThreadCount());
        jvm.setLoadedClassCount(classes.getLoadedClassCount());
        jvm.setUnloadedClassCount(classes.getUnloadedClassCount());

        jvm.setJavaVersion(System.getProperty("java.version"));
        jvm.setJavaHome(System.getProperty("java.home"));
        return jvm;
    }

    private ServerInfoVO.SystemInfo readSystem(java.lang.management.OperatingSystemMXBean baseBean) {
        ServerInfoVO.SystemInfo info = new ServerInfoVO.SystemInfo();
        info.setOsName(baseBean.getName());
        info.setOsVersion(baseBean.getVersion());
        info.setOsArch(baseBean.getArch());
        info.setAvailableProcessors(baseBean.getAvailableProcessors());
        info.setUserDir(System.getProperty("user.dir"));
        try {
            info.setHostName(InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            // 容器里常常拿不到主机名；不是故障，留空即可
            info.setHostName("unknown");
        }
        return info;
    }

    private List<ServerInfoVO.Disk> readDisks() {
        List<ServerInfoVO.Disk> disks = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) {
            return disks;
        }
        for (File root : roots) {
            try {
                FileStore store = Files.getFileStore(root.toPath());
                long total = store.getTotalSpace();
                // 空光驱 / 未挂载分区 total 为 0，跳过没有意义
                if (total <= 0) {
                    continue;
                }
                long usable = store.getUsableSpace();
                ServerInfoVO.Disk disk = new ServerInfoVO.Disk();
                disk.setPath(root.getAbsolutePath());
                disk.setType(store.type());
                disk.setTotal(total);
                disk.setUsable(usable);
                disk.setUsed(total - usable);
                disk.setUsage(percent(total - usable, total));
                disks.add(disk);
            } catch (Exception e) {
                log.debug("读取磁盘失败: path={}, msg={}", root, e.getMessage());
            }
        }
        return disks;
    }

    /**
     * 反射调用返回 0~1 小数的 CPU 指标，转成 0~100。
     *
     * <p>必须从公开接口 {@link com.sun.management.OperatingSystemMXBean} 取 Method：
     * JDK 8 的运行时实现类 {@code sun.management.OperatingSystemImpl} 不是 public，
     * 直接 {@code target.getClass().getMethod(...)} 然后 invoke 会抛
     * IllegalAccessException，表现为“CPU 使用率永远是 -1”。</p>
     *
     * @return 不可用时返回 -1
     */
    private double invokePercent(Object target, String... methodNames) {
        if (target == null) {
            return -1;
        }
        for (String methodName : methodNames) {
            try {
                Method method = com.sun.management.OperatingSystemMXBean.class.getMethod(methodName);
                Object value = method.invoke(target);
                if (value instanceof Number) {
                    double load = ((Number) value).doubleValue();
                    return load < 0 ? -1 : round(load * 100);
                }
            } catch (Exception e) {
                // JDK 8 没有 getCpuLoad；继续尝试下一个方法名
            }
        }
        return -1;
    }

    /** 已用 / 总量，返回 0~100；总量无效时返回 0。 */
    private double percent(long used, long total) {
        if (total <= 0) {
            return 0;
        }
        return round(used * 100.0 / total);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
