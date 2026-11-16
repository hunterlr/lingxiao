package com.lingxiao.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.file.entity.SysFileNameLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件改名历史 Mapper。
 */
@Mapper
public interface SysFileNameLogMapper extends BaseMapper<SysFileNameLog> {
}
