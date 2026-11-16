package com.lingxiao.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxiao.modules.file.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件主表 Mapper。
 */
@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {
}
