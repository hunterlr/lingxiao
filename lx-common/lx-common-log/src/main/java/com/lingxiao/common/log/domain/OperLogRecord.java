package com.lingxiao.common.log.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志记录（与 {@code sys_oper_log} 字段一一对应）。
 *
 * <p>放在 common 层是为了让切面只依赖“数据结构 + 存储接口”，
 * 不反向依赖系统模块；具体落哪张表由 {@link com.lingxiao.common.log.store.OperLogStore}
 * 的实现决定。</p>
 */
@Data
@Builder
public class OperLogRecord {

    /** 模块名，例如“用户管理” */
    private String title;

    /** 业务类型，取 {@code BusinessType.ordinal()} */
    private Integer businessType;

    /** 方法签名，例如 SysUserController.add(..) */
    private String method;

    /** HTTP 方法：POST / PUT / DELETE */
    private String requestMethod;

    /** 操作人账号 */
    private String operName;

    /** 部门名；v1.0 会话里没有部门，暂为空 */
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

    /** 失败原因；成功为空 */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime operTime;

    /** 耗时（毫秒） */
    private Long costTime;
}
