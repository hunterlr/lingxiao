package com.lingxiao.modules.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件改名历史（sys_file_name_log）。
 *
 * <p>只追加不修改。详情页展示“原名 / 当前名 / 曾用名”，
 * 解决“改名后不知道之前叫什么”的问题。</p>
 */
@Data
@TableName("sys_file_name_log")
public class SysFileNameLog {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long fileId;
    private String oldName;
    private String newName;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
}
