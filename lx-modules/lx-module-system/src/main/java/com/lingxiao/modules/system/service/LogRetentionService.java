package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxiao.modules.system.entity.SysLoginLog;
import com.lingxiao.modules.system.entity.SysOperLog;
import com.lingxiao.modules.system.mapper.SysLoginLogMapper;
import com.lingxiao.modules.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 日志保留清理。
 *
 * <p>参数设置里的 {@code sys.log.keepDays} 一直被种在库里却没人读，
 * 这一项正好补上：按配置的天数删除过期的操作日志与登录日志，
 * 避免审计表无限增长拖慢查询。</p>
 *
 * <p><b>安全默认值：</b>读参数失败、值非法、或小于 1 天时<b>直接拒绝清理</b>，
 * 而不是退回到某个“猜出来的”天数。清理是不可逆操作，宁可少删。</p>
 *
 * <p>为什么要拆成 Service + Task 两个类：<br>
 * v1.0 引入定时任务模块后，清理动作有两种触发方式——
 * 关闭 job 模块时用系统模块自带的 {@code @Scheduled}，
 * 开启时用页面上的 {@code systemLogClean} 任务。
 * 两条路必须执行<b>同一份</b>逻辑，否则很容易出现“页面删 180 天、兜底删 365 天”的偏差。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogRetentionService {

    /** 参数设置里的键；改键名要同步改这里 */
    public static final String CONFIG_KEEP_DAYS = "sys.log.keepDays";

    /** 上限：10 年，防止误填一个巨大值让清理形同虚设 */
    private static final int MAX_KEEP_DAYS = 3650;

    private final SysConfigService configService;
    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;

    /**
     * 执行一次清理。
     *
     * @return 可写入任务日志的结果摘要
     * @throws IllegalStateException 参数无效或删除失败时抛出，由调用方决定是记失败还是只打日志
     */
    public String cleanNow() {
        int keepDays = resolveKeepDays();
        if (keepDays <= 0) {
            throw new IllegalStateException("日志保留天数无效，已跳过清理（参数 " + CONFIG_KEEP_DAYS + "）");
        }
        LocalDateTime deadline = LocalDateTime.now().minusDays(keepDays);
        // 条件里必须带字段比较：BlockAttackInnerInterceptor 会拦截无 where 的 delete
        int operCount = operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>()
                .lt(SysOperLog::getOperTime, deadline));
        int loginCount = loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                .lt(SysLoginLog::getLoginTime, deadline));

        String message = String.format("保留 %d 天（截止 %s），删除操作日志 %d 条、登录日志 %d 条",
                keepDays, deadline, operCount, loginCount);
        log.info("日志清理完成 | {}", message);
        return message;
    }

    /**
     * 解析保留天数。
     *
     * @return 1~3650 的合法天数；无法确定时返回 0（调用方跳过清理）
     */
    public int resolveKeepDays() {
        String value;
        try {
            value = configService.getValueByKey(CONFIG_KEEP_DAYS);
        } catch (Exception e) {
            log.warn("读取 {} 失败: {}", CONFIG_KEEP_DAYS, e.getMessage());
            return 0;
        }
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            int days = Integer.parseInt(value.trim());
            if (days < 1) {
                return 0;
            }
            return Math.min(days, MAX_KEEP_DAYS);
        } catch (NumberFormatException e) {
            log.warn("{} 不是数字: {}", CONFIG_KEEP_DAYS, value);
            return 0;
        }
    }
}
