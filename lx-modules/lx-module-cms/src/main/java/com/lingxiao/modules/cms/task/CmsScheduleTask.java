package com.lingxiao.modules.cms.task;

import com.lingxiao.modules.cms.service.CmsArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CMS 定时发布扫描。
 *
 * <p>与系统日志清理不同，这里不依赖 job 模块：内容排期是 CMS 自身的业务能力，
 * CMS 开启就应该生效，关掉 job 模块也不该让文章永远发不出去。</p>
 *
 * <p><b>多实例安全：</b>扫描只负责找候选 ID，真正发布由
 * {@code CmsArticleMapper#publishScheduledById} 的条件 UPDATE 抢占
 * （{@code status='3' → '1'}）。多台机器同时扫到同一篇时，只有一台影响行数为 1，
 * 其余自然跳过，相当于用数据库行锁而不是内存锁保证幂等。</p>
 *
 * <p><b>为什么不逐篇开事务：</b>单篇发布就是一条 UPDATE，本身就是原子的；
 * 一批里某一篇失败不应该回滚已经发出的其它文章，下一轮会自动重试剩余项。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.cms.enabled", havingValue = "true")
public class CmsScheduleTask {

    private final CmsArticleService articleService;

    @Value("${lx.cms.schedule-batch-size:200}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${lx.cms.schedule-scan-interval-ms:30000}",
            initialDelayString = "${lx.cms.schedule-scan-initial-delay-ms:15000}"
    )
    public void scanDueArticles() {
        try {
            int published = articleService.publishDueScheduled(batchSize);
            if (published > 0) {
                log.info("CMS 定时发布：本轮发布 {} 篇到点文章", published);
            }
        } catch (Exception e) {
            // 调度线程里抛异常会让后续轮次停摆，这里兜住并留日志，下一轮继续
            log.error("CMS 定时发布扫描失败，将在下一轮重试", e);
        }
    }
}
