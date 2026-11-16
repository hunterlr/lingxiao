package com.lingxiao.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 凌霄启动类。
 *
 * <p>扫描范围固定为 {@code com.lingxiao}，这样新增模块只要把 jar 放进依赖，
 * 不需要改启动类。Mapper 用 {@code annotationClass = Mapper.class} 限定，
 * 只扫描显式标注 {@code @Mapper} 的接口，避免把普通 Service 接口误当 Mapper。</p>
 *
 * <p>{@code @EnableScheduling} 供系统模块的日志保留清理（{@code LogRetentionTask}）使用；
 * 没有它 {@code @Scheduled} 不会执行，参数 {@code sys.log.keepDays} 也就白配了。</p>
 */
@SpringBootApplication(scanBasePackages = "com.lingxiao")
@MapperScan(basePackages = "com.lingxiao.modules", annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableScheduling
public class LingxiaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingxiaoApplication.class, args);
    }
}
