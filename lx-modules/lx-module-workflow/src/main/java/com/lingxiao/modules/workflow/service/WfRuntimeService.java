package com.lingxiao.modules.workflow.service;

import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.FormSchema;
import com.lingxiao.modules.workflow.dto.InstanceDetailVO;
import com.lingxiao.modules.workflow.dto.InstanceFormVO;
import com.lingxiao.modules.workflow.dto.InstanceVO;
import com.lingxiao.modules.workflow.dto.StartProcessCmd;
import com.lingxiao.modules.workflow.dto.StartProcessRequest;
import com.lingxiao.modules.workflow.dto.TaskVO;
import com.lingxiao.modules.workflow.entity.WfForm;
import com.lingxiao.modules.workflow.entity.WfProcessDef;
import com.lingxiao.modules.workflow.spi.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 运行时服务：发起、待办 / 已办 / 我发起的、详情、同意 / 驳回 / 撤回。
 *
 * <p>发起时按流程绑定的表单校验取值、定格实例快照，再把校验后的值写进流程变量。
 * 业务校验（流程停用、表单未发布）在这里；越权校验在引擎层。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class WfRuntimeService {

    private final WfProcessService processService;

    private final WfFormService formService;

    private final WfFormValidator validator;

    private final WorkflowEngine engine;

    /**
     * 发起流程：校验表单 → 启动实例 → 落实例表单快照，同一事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public String start(StartProcessRequest request, Long userId, String userName) {
        WfProcessDef def = processService.mustGetActive(request.getProcessKey());

        WfForm form = formService.mustPublished(def.getFormKey());
        FormSchema schema = form == null ? new FormSchema() : formService.schemaOf(form, form.getVersion());
        boolean hasFormData = request.getFormData() != null && !request.getFormData().isEmpty();
        if (form == null && hasFormData) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "这个流程没有绑定表单，不能提交表单值");
        }

        Map<String, Object> formValues = validator.validate(schema, request.getFormData());
        Map<String, Object> variables = new LinkedHashMap<>(formValues);
        // 系统键后写，防止表单字段覆盖发起人信息（字段定义时已禁止使用保留名）
        variables.put("title", request.getTitle());
        variables.put("remark", request.getRemark());
        variables.put("initiator", userId);
        variables.put("initiatorName", userName);
        variables.put("assigneeNames", request.getAssigneeNames() == null
                ? new HashMap<String, String>() : request.getAssigneeNames());

        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setProcessKey(def.getProcessKey());
        cmd.setBusinessKey("manual:" + UUID.randomUUID().toString().replace("-", ""));
        cmd.setUserId(userId);
        cmd.setTitle(request.getTitle());
        cmd.setRemark(request.getRemark());
        cmd.setVariables(variables);
        String processInstanceId = engine.start(cmd);

        if (form != null) {
            formService.saveInstanceForm(processInstanceId, form, form.getVersion(),
                    formService.toJson(formValues));
        }
        return processInstanceId;
    }

    /** 发起页用：取流程绑定的表单定义；没绑定返回 null。 */
    public Map<String, Object> formOfProcess(String processKey) {
        WfProcessDef def = processService.mustGetActive(processKey);
        WfForm form = formService.mustPublished(def.getFormKey());
        if (form == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formKey", form.getFormKey());
        result.put("formName", form.getName());
        result.put("formVersion", form.getVersion());
        result.put("schema", formService.schemaOf(form, form.getVersion()));
        return result;
    }

    /** 详情抽屉用：实例表单快照（可能是 null）。 */
    public InstanceFormVO instanceForm(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程实例 ID 不能为空");
        }
        return formService.instanceForm(processInstanceId);
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