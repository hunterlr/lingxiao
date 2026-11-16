package com.lingxiao.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.system.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 *
 * <p>清空走 `BaseMapper.delete(wrapper)`，wrapper 里带主键条件，
 * 既表达意图，也能通过防全表删除插件的检查。</p>
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
