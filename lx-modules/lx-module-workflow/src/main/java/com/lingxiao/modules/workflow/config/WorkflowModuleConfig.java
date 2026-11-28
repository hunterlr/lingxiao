package com.lingxiao.modules.workflow.config;

import com.lingxiao.modules.workflow.service.WfProcessService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 工作流模块装配。
 *
 * <p>不用 Flowable 的 starter 自动装配，而是自己建 {@link SpringProcessEngineConfiguration}：
 * 自动装配会在模块关闭时照样启动引擎并建 ACT_* 表，违背“关闭模块不产生副作用”。
 * 这里整个配置挂在 {@code lx.module.modules.workflow.enabled=true} 上，
 * 关闭时没有引擎 Bean，ACT_* 不会出现。</p>
 *
 * <p>事务：与业务共用主数据源和同一个事务管理器，审批落库和业务更新在一个事务里，
 * 避免“流程通过但状态没改”。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class WorkflowModuleConfig {

    @Bean
    public ProcessEngineFactoryBean processEngine(DataSource dataSource,
                                                  PlatformTransactionManager transactionManager) {
        SpringProcessEngineConfiguration cfg = new SpringProcessEngineConfiguration();
        cfg.setEngineName("lx-workflow");
        cfg.setDataSource(dataSource);
        cfg.setTransactionManager(transactionManager);

        // ACT_* 由引擎自建；业务表由 Flyway 的 db/migration/workflow 管，两边互不扫描
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        // 第一刀没有异步任务（没有定时边界、没有异步服务任务）
        cfg.setAsyncExecutorActivate(false);
        // audit 足够回放审批过程，又比 full 少存大量变量快照
        cfg.setHistoryLevel(HistoryLevel.AUDIT);
        // 用户 / 组仍走 sys_*，不需要 ACT_ID_*；事件注册表第一刀也不用
        cfg.setDisableIdmEngine(true);
        cfg.setDisableEventRegistry(true);

        ProcessEngineFactoryBean factory = new ProcessEngineFactoryBean();
        factory.setProcessEngineConfiguration(cfg);
        return factory;
    }

    /**
     * 启动时确保内置示例流程已部署。
     *
     * <p>失败只记日志、不阻断启动：示例流程是演示数据，不能因为 BPMN 解析失败
     * 让整个后台起不来。</p>
     */
    @Bean
    public ApplicationRunner workflowDemoDeployer(WfProcessService processService) {
        return args -> {
            try {
                processService.ensureDemoProcess();
            } catch (Exception e) {
                log.warn("内置示例流程部署失败，跳过：{}", e.getMessage());
            }
        };
    }
}