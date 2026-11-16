package com.lingxiao.modules.system.service.log;

import com.lingxiao.common.log.domain.OperLogRecord;
import com.lingxiao.common.log.store.OperLogStore;
import com.lingxiao.modules.system.entity.SysOperLog;
import com.lingxiao.modules.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作日志落库实现。
 *
 * <p>切面只负责采集，本类负责把 {@link OperLogRecord} 映射到
 * {@code sys_oper_log}。单独成类而不是塞进 Service，是为了让
 * “存储接口的实现”与“查询业务”职责分开，后续换异步 / 消息队列只改这里。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysOperLogStore implements OperLogStore {

    private final SysOperLogMapper operLogMapper;

    @Override
    public void save(OperLogRecord record) {
        SysOperLog entity = new SysOperLog();
        entity.setTitle(record.getTitle());
        entity.setBusinessType(record.getBusinessType());
        entity.setMethod(record.getMethod());
        entity.setRequestMethod(record.getRequestMethod());
        entity.setOperName(record.getOperName());
        entity.setDeptName(record.getDeptName());
        entity.setOperUrl(record.getOperUrl());
        entity.setOperIp(record.getOperIp());
        entity.setOperParam(record.getOperParam());
        entity.setJsonResult(record.getJsonResult());
        entity.setStatus(record.getStatus());
        entity.setErrorMsg(record.getErrorMsg());
        entity.setOperTime(record.getOperTime() == null ? LocalDateTime.now() : record.getOperTime());
        entity.setCostTime(record.getCostTime() == null ? 0L : record.getCostTime());
        operLogMapper.insert(entity);
    }
}
