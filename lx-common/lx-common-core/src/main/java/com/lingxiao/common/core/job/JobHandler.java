package com.lingxiao.common.core.job;

/**
 * 定时任务执行器 SPI。
 *
 * <p>为什么放在 common-core 而不是 job 模块：
 * 系统模块需要提供“清理过期日志”执行器，但不能反向依赖 job 模块
 * （依赖方向必须是 modules → common / framework）。这里只定义最小契约，
 * job 模块负责发现与调度，系统模块只负责实现。</p>
 *
 * <p>约定：</p>
 * <ul>
 *   <li>{@link #name()} 写进 {@code sys_job.invoke_target}，必须全局唯一；</li>
 *   <li>{@link #execute(String)} 返回值会写进任务日志的 {@code job_message}，
 *       建议返回“做了多少事”的可核对数字；</li>
 *   <li>抛异常即视为失败：job 模块会记录异常摘要与堆栈，任务本身不会被删除。</li>
 * </ul>
 */
public interface JobHandler {

    /** 执行器名称，例如 {@code jobLogClean}。 */
    String name();

    /** 给运营看的中文说明，展示在任务表单的执行器下拉里。 */
    String description();

    /**
     * 执行一次。
     *
     * @param params 预留参数；v1.0 不支持传参，固定为 {@code null}
     * @return 执行结果摘要，写入任务日志；可为 {@code null}
     * @throws Exception 任何异常都会记为任务失败
     */
    String execute(String params) throws Exception;
}
