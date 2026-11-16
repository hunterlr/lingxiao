package com.lingxiao.common.log.store;

import com.lingxiao.common.log.domain.OperLogRecord;

/**
 * 操作日志存储 SPI。
 *
 * <p>切面负责采集，存储实现负责落库。这样 {@code lx-common-log} 不依赖
 * {@code lx-module-system}；没有实现（例如裁剪掉系统模块、或纯粹的单元测试）
 * 时切面自动退化为只打应用日志，不影响主业务。</p>
 *
 * <p>实现方必须自行保证：<b>不得向调用方抛异常</b>。日志失败不能影响业务。</p>
 */
public interface OperLogStore {

    /**
     * 保存一条操作日志。
     *
     * @param record 已采集、已脱敏的记录
     */
    void save(OperLogRecord record);
}
