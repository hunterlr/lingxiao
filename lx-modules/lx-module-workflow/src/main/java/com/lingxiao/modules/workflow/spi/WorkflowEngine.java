package com.lingxiao.modules.workflow.spi;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.modules.workflow.dto.DeploymentInfo;
import com.lingxiao.modules.workflow.dto.InstanceDetailVO;
import com.lingxiao.modules.workflow.dto.InstanceVO;
import com.lingxiao.modules.workflow.dto.StartProcessCmd;
import com.lingxiao.modules.workflow.dto.TaskVO;

/**
 * 工作流引擎 SPI。
 *
 * <p>业务代码只依赖本接口，不直接注入 Flowable 的 {@code RuntimeService} /
 * {@code TaskService}。换引擎时只改 {@code FlowableWorkflowEngine}。</p>
 *
 * <p>第一刀只覆盖：部署 / 发起 / 待办 / 已办 / 我发起的 / 详情 / 同意 / 驳回 / 撤回。
 * 转办、加签、流程图等方法等后续刀再补，不在这里留空实现。</p>
 */
public interface WorkflowEngine {

    /** 部署 BPMN，返回引擎给出的部署信息。 */
    DeploymentInfo deploy(String resourceName, byte[] bpmn);

    /** 删除部署（级联删流程定义）；调用前必须确认没有运行中实例。 */
    void deleteDeployment(String deploymentId);

    /** 某流程 key 的运行中实例数。 */
    long countRunning(String processKey);

    /** 发起流程，返回 processInstanceId。 */
    String start(StartProcessCmd cmd);

    /** 同意：写意见并完成任务。 */
    void complete(String taskId, Long userId, String comment);

    /** 驳回：写意见并终止流程（第一刀驳回即终止）。 */
    void reject(String taskId, Long userId, String comment);

    /** 撤回：仅发起人，且还没有任何任务被办理过。 */
    void withdraw(String processInstanceId, Long userId);

    PageResult<TaskVO> pageTodo(Long userId, String keyword, long pageNum, long pageSize);

    PageResult<TaskVO> pageDone(Long userId, String keyword, long pageNum, long pageSize);

    PageResult<InstanceVO> pageMine(Long userId, String keyword, long pageNum, long pageSize);

    InstanceDetailVO detail(String processInstanceId);
}