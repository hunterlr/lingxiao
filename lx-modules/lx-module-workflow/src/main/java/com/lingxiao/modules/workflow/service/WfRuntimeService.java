package com.lingxiao.modules.workflow.service;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.InstanceDetailVO;
import com.lingxiao.modules.workflow.dto.InstanceVO;
import com.lingxiao.modules.workflow.dto.StartProcessCmd;
import com.lingxiao.modules.workflow.dto.StartProcessRequest;
import com.lingxiao.modules.workflow.dto.TaskVO;
import com.lingxiao.modules.workflow.entity.WfProcessDef;
import com.lingxiao.modules.workflow.spi.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 运行时服务：发起、待办 / 已办 / 我发起的、详情、同意 / 驳回 / 撤回。
 *
 * <p>业务校验（流程是否停用、审批人是否必填）在这里做；越权校验在引擎层做，
 * 两边都不依赖前端隐藏按钮。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class WfRuntimeService {

    private final WfProcessService processService;

    private final WorkflowEngine engine;

    public String start(StartProcessRequest request, Long userId, String userName) {
        WfProcessDef def = processService.mustGetActive(request.getProcessKey());
        if (request.getApprover() == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请选择一级审批人");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("approver", String.valueOf(request.getApprover()));
        variables.put("approverName", request.getApproverName());
        if (request.getApprover2() != null) {
            variables.put("approver2", String.valueOf(request.getApprover2()));
            variables.put("approver2Name", request.getApprover2Name());
        }
        // 详情页按 assignee 反查姓名，不再跨模块查 sys_user
        Map<String, String> names = new HashMap<>();
        if (request.getApproverName() != null) {
            names.put(String.valueOf(request.getApprover()), request.getApproverName());
        }
        if (request.getApprover2() != null && request.getApprover2Name() != null) {
            names.put(String.valueOf(request.getApprover2()), request.getApprover2Name());
        }
        variables.put("assigneeNames", names);
        variables.put("initiatorName", userName);

        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setProcessKey(def.getProcessKey());
        cmd.setBusinessKey("manual:" + UUID.randomUUID().toString().replace("-", ""));
        cmd.setUserId(userId);
        cmd.setTitle(request.getTitle());
        cmd.setRemark(request.getRemark());
        cmd.setVariables(variables);
        return engine.start(cmd);
    }

    public PageResult<TaskVO> todo(Long userId, String keyword, long pageNum, long pageSize) {
        return engine.pageTodo(userId, keyword, pageNum, pageSize);
    }

    public PageResult<TaskVO> done(Long userId, String keyword, long pageNum, long pageSize) {
        return engine.pageDone(userId, keyword, pageNum, pageSize);
    }

    public PageResult<InstanceVO> mine(Long userId, String keyword, long pageNum, long pageSize) {
        return engine.pageMine(userId, keyword, pageNum, pageSize);
    }

    public InstanceDetailVO detail(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程实例 ID 不能为空");
        }
        return engine.detail(processInstanceId);
    }

    public void complete(String taskId, Long userId, String comment) {
        engine.complete(taskId, userId, comment);
    }

    public void reject(String taskId, Long userId, String comment) {
        engine.reject(taskId, userId, comment);
    }

    public void withdraw(String processInstanceId, Long userId) {
        engine.withdraw(processInstanceId, userId);
    }
}