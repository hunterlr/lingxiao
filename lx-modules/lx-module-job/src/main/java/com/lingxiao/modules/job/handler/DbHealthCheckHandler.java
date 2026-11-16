package com.lingxiao.modules.job.handler;

import com.lingxiao.common.core.job.JobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库健康检查。
 *
 * <p>用途：部署后建一个“每 5 分钟”的任务，就能在任务日志里留下一条
 * “数据库什么时候开始连不上”的时间线，比等业务报错更早发现问题。</p>
 *
 * <p>这里只执行 {@code SELECT 1} 和一张表的 count，不做写入：
 * 健康检查本身不能改变数据。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.job.enabled", havingValue = "true")
public class DbHealthCheckHandler implements JobHandler {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "dbHealthCheck";
    }

    @Override
    public String description() {
        return "检查数据库连接，并统计未删除的定时任务数";
    }

    @Override
    public String execute(String params) {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (one == null || one != 1) {
            throw new IllegalStateException("SELECT 1 返回值异常：" + one);
        }
        Long jobCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_job WHERE del_flag = '0'", Long.class);
        return "数据库连接正常，未删除任务 " + (jobCount == null ? 0 : jobCount) + " 条";
    }
}
