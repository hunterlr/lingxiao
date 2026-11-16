package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（sys_oper_log）。
 *
 * <p>只追加，不在界面提供单条修改；允许按条件清空（管理员）。</p>
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(value = "oper_id", type = IdType.ASSIGN_ID)
    private Long operId;

    /** 模块名，例如“用户管理” */
    private String title;

    /** 业务类型枚举序号，见 BusinessType */
    private Integer businessType;

    /** 方法签名 */
    private String method;

    /** HTTP 方法 */
    private String requestMethod;

    /** 操作人账号 */
    private String operName;

    /** 部门名；v1.0 未采集，保留字段 */
    private String deptName;

    /** 请求路径 */
    private String operUrl;

    /** 客户端 IP */
    private String operIp;

    /** 请求参数（已脱敏、已截断） */
    private String operParam;

    /** 响应结果（已截断） */
    private String jsonResult;

    /** 0 成功 / 1 失败 */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    private LocalDateTime operTime;

    /** 耗时（毫秒） */
    private Long costTime;
}
