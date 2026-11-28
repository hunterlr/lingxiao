package com.lingxiao.modules.workflow.engine;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.DeploymentInfo;
import com.lingxiao.modules.workflow.dto.InstanceDetailVO;
import com.lingxiao.modules.workflow.dto.InstanceVO;
import com.lingxiao.modules.workflow.dto.StartProcessCmd;
import com.lingxiao.modules.workflow.dto.TaskVO;
import com.lingxiao.modules.workflow.dto.TimelineVO;
import com.lingxiao.modules.workflow.spi.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Flowable 6.8 适配器。
 *
 * <p>这是本模块唯一直接依赖 Flowable API 的类；业务层只看到 {@link WorkflowEngine}。
 * 所有 Query 都按“当前用户”加过滤条件，越权在引擎层就被拦住，不靠前端隐藏按钮。</p>
 *
 * <p>状态映射：Flowable 只有“运行中 / 已结束”，没有驳回与撤回；
 * 这里用删除实例的原因前缀区分——{@code reject:} → rejected，{@code withdraw:} → canceled。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class FlowableWorkflowEngine implements WorkflowEngine {

    private static final String REJECT_PREFIX = "reject:";

    private static final String WITHDRAW_PREFIX = "withdraw:";

    private static final String VAR_TITLE = "title";

    private static final String VAR_REMARK = "remark";

    private static final String VAR_NAMES = "assigneeNames";

    private final ProcessEngine processEngine;

    private RuntimeService runtimeService() {
        return processEngine.getRuntimeService();
    }

    private TaskService taskService() {
        return processEngine.getTaskService();
    }

    private HistoryService historyService() {
        return processEngine.getHistoryService();
    }

    private RepositoryService repositoryService() {
        return processEngine.getRepositoryService();
    }

    // ------------------------------------------------------------------ 部署

    @Override
    public DeploymentInfo deploy(String resourceName, byte[] bpmn) {
        Deployment deployment = repositoryService().createDeployment()
                .name(resourceName)
                .addBytes(resourceName, bpmn)
                .deploy();
        ProcessDefinition definition = repositoryService().createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        if (definition == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "BPMN 里没有可执行的流程定义，请检查 process 节点");
        }
        DeploymentInfo info = new DeploymentInfo();
        info.setDeploymentId(deployment.getId());
        info.setProcessKey(definition.getKey());
        info.setName(StringUtils.hasText(definition.getName()) ? definition.getName() : definition.getKey());
        info.setVersion(definition.getVersion());
        info.setDefinitionId(definition.getId());
        info.setResourceName(definition.getResourceName());
        return info;
    }

    @Override
    public void deleteDeployment(String deploymentId) {
        repositoryService().deleteDeployment(deploymentId, true);
    }

    @Override
    public long countRunning(String processKey) {
        return runtimeService().createProcessInstanceQuery()
                .processDefinitionKey(processKey)
                .count();
    }

    // ------------------------------------------------------------------ 运行

    @Override
    public String start(StartProcessCmd cmd) {
        Map<String, Object> variables = new HashMap<>(cmd.getVariables());
        variables.put(VAR_TITLE, cmd.getTitle());
        variables.put(VAR_REMARK, cmd.getRemark());
        variables.put("initiator", cmd.getUserId());
        Authentication.setAuthenticatedUserId(String.valueOf(cmd.getUserId()));
        try {
            ProcessInstance instance = runtimeService().startProcessInstanceByKey(
                    cmd.getProcessKey(), cmd.getBusinessKey(), variables);
            return instance.getId();
        } finally {
            // 线程复用，必须清掉，否则下一个请求会被记成同一个发起人
            Authentication.setAuthenticatedUserId(null);
        }
    }

    @Override
    public void complete(String taskId, Long userId, String comment) {
        Task task = mustOwnTask(taskId, userId);
        if (StringUtils.hasText(comment)) {
            taskService().addComment(taskId, task.getProcessInstanceId(), comment);
        }
        taskService().complete(taskId);
    }

    @Override
    public void reject(String taskId, Long userId, String comment) {
        Task task = mustOwnTask(taskId, userId);
        String text = StringUtils.hasText(comment) ? comment : "";
        taskService().addComment(taskId, task.getProcessInstanceId(), text);
        runtimeService().deleteProcessInstance(task.getProcessInstanceId(),
                REJECT_PREFIX + userId + ":" + text);
    }

    @Override
    public void withdraw(String processInstanceId, Long userId) {
        HistoricProcessInstance instance = mustGetInstance(processInstanceId);
        if (instance.getEndTime() != null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程已结束，不能撤回");
        }
        if (!String.valueOf(userId).equals(instance.getStartUserId())) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "只有发起人可以撤回");
        }
        long handled = historyService().createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .count();
        if (handled > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "已有节点办理过，不能撤回");
        }
        runtimeService().deleteProcessInstance(processInstanceId, WITHDRAW_PREFIX + userId);
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public PageResult<TaskVO> pageTodo(Long userId, String keyword, long pageNum, long pageSize) {
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        TaskQuery query = taskService().createTaskQuery()
                .taskAssignee(String.valueOf(userId))
                .orderByTaskCreateTime().desc();
        if (StringUtils.hasText(keyword)) {
            query.or().taskNameLike("%" + keyword + "%").processDefinitionNameLike("%" + keyword + "%").endOr();
        }
        long total = query.count();
        List<Task> tasks = query.listPage((int) ((current - 1) * size), (int) size);
        List<TaskVO> rows = new ArrayList<>();
        for (Task task : tasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setProcessKey(processKeyOf(task.getProcessDefinitionId()));
            vo.setProcessName(task.getProcessDefinitionId() == null ? null : nameOfDefinition(task.getProcessDefinitionId()));
            vo.setTitle(stringVar(taskService().getVariable(task.getId(), VAR_TITLE)));
            vo.setInitiator(stringVar(taskService().getVariable(task.getId(), "initiator")));
            vo.setInitiatorName(stringVar(taskService().getVariable(task.getId(), "initiatorName")));
            vo.setAssignee(task.getAssignee());
            vo.setAssigneeName(nameOfAssignee(taskService().getVariable(task.getId(), VAR_NAMES), task.getAssignee()));
            vo.setCreateTime(toLocal(task.getCreateTime()));
            rows.add(vo);
        }
        return PageResult.of(total, current, size, rows);
    }

    @Override
    public PageResult<TaskVO> pageDone(Long userId, String keyword, long pageNum, long pageSize) {
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        HistoricTaskInstanceQuery query = historyService().createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(userId))
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc();
        if (StringUtils.hasText(keyword)) {
            query.or().taskNameLike("%" + keyword + "%").processDefinitionNameLike("%" + keyword + "%").endOr();
        }
        long total = query.count();
        List<HistoricTaskInstance> tasks = query.listPage((int) ((current - 1) * size), (int) size);
        List<TaskVO> rows = new ArrayList<>();
        for (HistoricTaskInstance task : tasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setProcessKey(processKeyOf(task.getProcessDefinitionId()));
            vo.setProcessName(task.getProcessDefinitionId() == null ? null : nameOfDefinition(task.getProcessDefinitionId()));
            Map<String, Object> vars = variablesOf(task.getProcessInstanceId());
            vo.setTitle(stringVar(vars.get(VAR_TITLE)));
            vo.setInitiator(stringVar(vars.get("initiator")));
            vo.setInitiatorName(stringVar(vars.get("initiatorName")));
            vo.setAssignee(task.getAssignee());
            vo.setAssigneeName(nameOfAssignee(vars.get(VAR_NAMES), task.getAssignee()));
            vo.setCreateTime(toLocal(task.getCreateTime()));
            vo.setEndTime(toLocal(task.getEndTime()));
            vo.setComment(lastCommentOf(task.getId()));
            rows.add(vo);
        }
        return PageResult.of(total, current, size, rows);
    }

    @Override
    public PageResult<InstanceVO> pageMine(Long userId, String keyword, long pageNum, long pageSize) {
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        org.flowable.engine.history.HistoricProcessInstanceQuery query = historyService()
                .createHistoricProcessInstanceQuery()
                .startedBy(String.valueOf(userId))
                .orderByProcessInstanceStartTime().desc();
        // 标题是流程变量，实例表里没有这一列；历史实例查询本身支持按变量值模糊过滤
        if (StringUtils.hasText(keyword)) {
            query.variableValueLike(VAR_TITLE, "%" + keyword + "%");
        }
        long total = query.count();
        List<HistoricProcessInstance> list = query.listPage((int) ((current - 1) * size), (int) size);
        List<InstanceVO> rows = list.stream().map(this::toInstanceVO).collect(Collectors.toList());
        return PageResult.of(total, current, size, rows);
    }

    @Override
    public InstanceDetailVO detail(String processInstanceId) {
        HistoricProcessInstance instance = mustGetInstance(processInstanceId);
        Map<String, Object> vars = variablesOf(processInstanceId);
        InstanceDetailVO vo = new InstanceDetailVO();
        fillInstance(vo, instance, vars);
        vo.setRemark(stringVar(vars.get(VAR_REMARK)));
        vo.setInitiator(stringVar(vars.get("initiator")));
        vo.setInitiatorName(stringVar(vars.get("initiatorName")));
        vo.setTimeline(buildTimeline(instance, vars));
        return vo;
    }

    // ------------------------------------------------------------------ 内部

    private Task mustOwnTask(String taskId, Long userId) {
        Task task = taskService().createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "任务不存在或已办理");
        }
        if (!String.valueOf(userId).equals(task.getAssignee())) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "只有当前审批人可以办理该任务");
        }
        return task;
    }

    private HistoricProcessInstance mustGetInstance(String processInstanceId) {
        HistoricProcessInstance instance = historyService().createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程实例不存在");
        }
        return instance;
    }

    private InstanceVO toInstanceVO(HistoricProcessInstance instance) {
        InstanceVO vo = new InstanceVO();
        fillInstance(vo, instance, variablesOf(instance.getId()));
        return vo;
    }

    private void fillInstance(InstanceVO vo, HistoricProcessInstance instance, Map<String, Object> vars) {
        vo.setProcessInstanceId(instance.getId());
        vo.setProcessKey(instance.getProcessDefinitionKey());
        vo.setProcessName(StringUtils.hasText(instance.getProcessDefinitionName())
                ? instance.getProcessDefinitionName() : instance.getProcessDefinitionKey());
        vo.setTitle(stringVar(vars.get(VAR_TITLE)));
        vo.setRemark(stringVar(vars.get(VAR_REMARK)));
        vo.setStatus(statusOf(instance));
        vo.setStartTime(toLocal(instance.getStartTime()));
        vo.setEndTime(toLocal(instance.getEndTime()));
        vo.setCurrentTaskName(currentTaskName(instance.getId()));
    }

    private String statusOf(HistoricProcessInstance instance) {
        if (instance.getEndTime() == null) {
            return "running";
        }
        String reason = instance.getDeleteReason();
        if (reason != null && reason.startsWith(REJECT_PREFIX)) {
            return "rejected";
        }
        if (reason != null && reason.startsWith(WITHDRAW_PREFIX)) {
            return "canceled";
        }
        return "completed";
    }

    private String currentTaskName(String processInstanceId) {
        List<Task> tasks = taskService().createTaskQuery().processInstanceId(processInstanceId).list();
        return tasks.isEmpty() ? null : tasks.get(0).getName();
    }

    private Map<String, Object> variablesOf(String processInstanceId) {
        List<HistoricVariableInstance> list = historyService().createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();
        Map<String, Object> map = new HashMap<>();
        for (HistoricVariableInstance var : list) {
            map.put(var.getVariableName(), var.getValue());
        }
        return map;
    }

    private List<TimelineVO> buildTimeline(HistoricProcessInstance instance, Map<String, Object> vars) {
        Map<String, String> comments = commentsOf(instance.getId());
        List<TimelineVO> timeline = new ArrayList<>();

        TimelineVO start = new TimelineVO();
        start.setNodeName("发起");
        start.setAction("发起");
        start.setAssignee(instance.getStartUserId());
        start.setAssigneeName(stringVar(vars.get("initiatorName")));
        start.setTime(toLocal(instance.getStartTime()));
        timeline.add(start);

        List<HistoricTaskInstance> tasks = historyService().createHistoricTaskInstanceQuery()
                .processInstanceId(instance.getId())
                .orderByHistoricTaskInstanceStartTime().asc()
                .list();
        for (HistoricTaskInstance task : tasks) {
            TimelineVO item = new TimelineVO();
            item.setNodeName(task.getName());
            item.setAssignee(task.getAssignee());
            item.setAssigneeName(nameOfAssignee(vars.get(VAR_NAMES), task.getAssignee()));
            item.setComment(comments.get(task.getId()));
            if (task.getEndTime() != null) {
                item.setAction("同意");
                item.setTime(toLocal(task.getEndTime()));
            } else if (instance.getEndTime() != null) {
                String reason = instance.getDeleteReason();
                item.setAction(reason != null && reason.startsWith(REJECT_PREFIX) ? "驳回" : "撤回");
                item.setTime(toLocal(instance.getEndTime()));
            } else {
                item.setAction("待办理");
            }
            timeline.add(item);
        }
        timeline.sort(Comparator.comparing(TimelineVO::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return timeline;
    }

    private Map<String, String> commentsOf(String processInstanceId) {
        Map<String, String> map = new HashMap<>();
        List<Comment> comments = taskService().getProcessInstanceComments(processInstanceId);
        for (Comment comment : comments) {
            if (comment.getTaskId() != null && StringUtils.hasText(comment.getFullMessage())) {
                map.put(comment.getTaskId(), comment.getFullMessage());
            }
        }
        return map;
    }

    private String lastCommentOf(String taskId) {
        List<Comment> comments = taskService().getTaskComments(taskId);
        return comments.isEmpty() ? null : comments.get(comments.size() - 1).getFullMessage();
    }

    private String processKeyOf(String processDefinitionId) {
        // 引擎 ID 形如 demo_leave:1:xxxx，前缀就是 key
        return processDefinitionId == null ? null : processDefinitionId.split(":")[0];
    }

    private String nameOfDefinition(String processDefinitionId) {
        ProcessDefinition definition = repositoryService().createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (definition == null) {
            return processKeyOf(processDefinitionId);
        }
        return StringUtils.hasText(definition.getName()) ? definition.getName() : definition.getKey();
    }

    @SuppressWarnings("unchecked")
    private String nameOfAssignee(Object namesVar, String assignee) {
        if (!(namesVar instanceof Map) || assignee == null) {
            return null;
        }
        Object name = ((Map<String, Object>) namesVar).get(assignee);
        return name == null ? null : String.valueOf(name);
    }

    private String stringVar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}