package com.lingxiao.modules.system.service.captcha;

import java.util.Collections;
import java.util.Map;

/**
 * 验证码存储。
 *
 * <p>验证码是“一次写入、一次读取、随即删除”的短命数据，天然适合放缓存。
 * v1.0 默认用 Caffeine 单机缓存；多实例部署把 {@code lx.redis.enabled}
 * 打开后自动切换到 Redis，业务代码（{@code CaptchaService}）不需要改。</p>
 *
 * <p>接口刻意做得很窄：只支持按键存取与删除，不暴露底层缓存细节。
 * 这样换实现时不会因为“某个实现多出来的方法”把调用方绑死。</p>
 */
public interface CaptchaStore {

    /**
     * 写入验证码。
     *
     * @param key        验证码标识（登录时原样带回）
     * @param code       验证码文本
     * @param ttlSeconds 有效期（秒）
     */
    void put(String key, String code, long ttlSeconds);

    /**
     * 读取验证码；不存在或已过期返回 {@code null}。
     */
    String get(String key);

    /**
     * 删除验证码；不存在时静默忽略。
     */
    void remove(String key);

    /**
     * 当前实现名，仅用于启动日志与排查。
     */
    String mode();

    /**
     * 缓存运行统计，供“缓存监控”页展示。
     *
     * <p>用默认方法而不是抽象方法：验证码存储的职责很窄，统计属于可选的运维能力。
     * 以后新增第三种存储（例如 Caffeine + Redis 二级缓存）时，不实现也能编译，
     * 页面显示“暂无统计”即可，不会因为一个监控字段把主流程改坏。</p>
     *
     * <p>返回的是“展示用”的扁平 Map，字段名由各实现自己约定；
     * 监控是只读旁路，不值得为一个页面引入一套强类型模型。</p>
     */
    default Map<String, Object> stats() {
        return Collections.emptyMap();
    }
}
