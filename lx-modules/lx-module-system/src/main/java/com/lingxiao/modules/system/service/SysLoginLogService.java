package com.lingxiao.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.system.entity.SysLoginLog;
import com.lingxiao.modules.system.mapper.SysLoginLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 登录日志查询。
 *
 * <p>写入在 {@code AuthService} 登录流程里完成，本类只负责查与清空。
 * 登录日志和操作日志分开两个接口：前者面向“谁在什么时候登录”，
 * 后者面向“登录后做了什么”，权限码也各自独立。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    public PageResult<SysLoginLog> page(long pageNum, long pageSize, String username, String ip,
                                        String status, LocalDateTime beginTime, LocalDateTime endTime) {
        Page<SysLoginLog> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .like(StringUtils.hasText(username), SysLoginLog::getUsername, username)
                .like(StringUtils.hasText(ip), SysLoginLog::getIp, ip)
                .eq(StringUtils.hasText(status), SysLoginLog::getStatus, status)
                .ge(beginTime != null, SysLoginLog::getLoginTime, beginTime)
                .le(endTime != null, SysLoginLog::getLoginTime, endTime)
                .orderByDesc(SysLoginLog::getLoginTime);
        Page<SysLoginLog> result = loginLogMapper.selectPage(page, wrapper);

        PageResult<SysLoginLog> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }

    /** 清空全部登录日志，返回删除条数。 */
    public int clean() {
        int count = loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                .isNotNull(SysLoginLog::getInfoId));
        log.warn("清空登录日志: {} 条, 操作人={}", count, LoginHelper.getUsername());
        return count;
    }
}
