package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysOperLog;
import com.lingxiao.modules.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 操作日志查询。
 *
 * <p>只提供“查 + 清空”，不提供修改：审计数据被改过就失去意义。
 * 清空是危险操作，接口单独一个权限码，不与列表权限混用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperLogService {

    private final SysOperLogMapper operLogMapper;

    public PageResult<SysOperLog> page(long pageNum, long pageSize, String title, String operName,
                                       Integer businessType, String status,
                                       LocalDateTime beginTime, LocalDateTime endTime) {
        Page<SysOperLog> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .like(StringUtils.hasText(title), SysOperLog::getTitle, title)
                .like(StringUtils.hasText(operName), SysOperLog::getOperName, operName)
                .eq(businessType != null, SysOperLog::getBusinessType, businessType)
                .eq(StringUtils.hasText(status), SysOperLog::getStatus, status)
                .ge(beginTime != null, SysOperLog::getOperTime, beginTime)
                .le(endTime != null, SysOperLog::getOperTime, endTime)
                .orderByDesc(SysOperLog::getOperTime);
        Page<SysOperLog> result = operLogMapper.selectPage(page, wrapper);

        PageResult<SysOperLog> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    public SysOperLog detail(Long operId) {
        SysOperLog log = operLogMapper.selectById(operId);
        if (log == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "操作日志不存在");
        }
        return log;
    }

    /**
     * 清空全部操作日志。
     *
     * <p>wrapper 里显式带主键条件：一是表达“确实要清空”，二是通过
     * {@code BlockAttackInnerInterceptor} 的“禁止无 where 删除”检查。
     * 本次清空动作本身仍会被切面记一条，属于预期。</p>
     *
     * @return 删除条数
     */
    public int clean() {
        int count = operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>()
                .isNotNull(SysOperLog::getOperId));
        log.warn("清空操作日志: {} 条, 操作人={}", count, LoginHelper.getUsername());
        return count;
    }
}
