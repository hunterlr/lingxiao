package com.lingxiao.modules.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.FormField;
import com.lingxiao.modules.workflow.dto.FormSchema;
import com.lingxiao.modules.workflow.dto.StartProcessCmd;
import com.lingxiao.modules.workflow.dto.StartProcessRequest;
import com.lingxiao.modules.workflow.entity.WfForm;
import com.lingxiao.modules.workflow.entity.WfProcessDef;
import com.lingxiao.modules.workflow.spi.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发起流程：表单校验、系统变量、实例快照。
 */
class WfRuntimeServiceTest {

    private WfProcessService processService;

    private WfFormService formService;

    private WorkflowEngine engine;

    private WfRuntimeService service;

    @BeforeEach
    void setUp() {
        processService = mock(WfProcessService.class);
        formService = mock(WfFormService.class);
        engine = mock(WorkflowEngine.class);
        service = new WfRuntimeService(processService, formService,
                new WfFormValidator(new ObjectMapper()), engine);
    }

    private WfProcessDef def(String formKey) {
        WfProcessDef def = new WfProcessDef();
        def.setProcessKey("demo_leave");
        def.setFormKey(formKey);
        return def;
    }

    private StartProcessRequest request(Map<String, Object> formData) {
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessKey("demo_leave");
        request.setTitle("请假申请");
        request.setRemark("事假一天");
        request.setFormData(formData);
        request.setAssigneeNames(Collections.singletonMap("10", "张三"));
        return request;
    }

    @Test
    void startWithoutFormRejectsFormData() {
        when(processService.mustGetActive("demo_leave")).thenReturn(def(null));
        when(formService.mustPublished(null)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.start(request(Collections.singletonMap("reason", "x")), 1L, "管理员"));

        assertTrue(e.getMessage().contains("没有绑定表单"));
        verify(engine, never()).start(any());
    }

    @Test
    void startWritesValidatedFormDataAndSnapshot() {
        WfForm form = new WfForm();
        form.setFormKey("demo_leave_form");
        form.setName("请假申请");
        form.setVersion(2);
        when(processService.mustGetActive("demo_leave")).thenReturn(def("demo_leave_form"));
        when(formService.mustPublished("demo_leave_form")).thenReturn(form);

        FormSchema schema = new FormSchema();
        FormField reason = new FormField();
        reason.setKey("reason");
        reason.setLabel("事由");
        reason.setType(WfFormValidator.TYPE_TEXTAREA);
        reason.setRequired(true);
        schema.setFields(Collections.singletonList(reason));
        when(formService.schemaOf(form, 2)).thenReturn(schema);
        when(engine.start(any())).thenReturn("pi-1");
        when(formService.toJson(any())).thenReturn("{\"reason\":\"家中有事\"}");

        Map<String, Object> formData = new HashMap<>();
        formData.put("reason", " 家中有事 ");
        String id = service.start(request(formData), 1L, "管理员");

        assertEquals("pi-1", id);
        ArgumentCaptor<StartProcessCmd> captor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(engine).start(captor.capture());
        StartProcessCmd cmd = captor.getValue();
        assertEquals("家中有事", cmd.getVariables().get("reason"));
        assertEquals("请假申请", cmd.getVariables().get("title"));
        assertEquals(1L, cmd.getVariables().get("initiator"));
        assertEquals("管理员", cmd.getVariables().get("initiatorName"));
        assertTrue(cmd.getBusinessKey().startsWith("manual:"));
        verify(formService).saveInstanceForm(eq("pi-1"), eq(form), eq(2), anyString());
    }

    @Test
    void startRejectsMissingRequiredField() {
        WfForm form = new WfForm();
        form.setFormKey("demo_leave_form");
        form.setVersion(1);
        when(processService.mustGetActive("demo_leave")).thenReturn(def("demo_leave_form"));
        when(formService.mustPublished("demo_leave_form")).thenReturn(form);

        FormSchema schema = new FormSchema();
        FormField approver = new FormField();
        approver.setKey("approver");
        approver.setLabel("一级审批人");
        approver.setType(WfFormValidator.TYPE_USER);
        approver.setRequired(true);
        schema.setFields(Arrays.asList(approver));
        when(formService.schemaOf(form, 1)).thenReturn(schema);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.start(request(new HashMap<>()), 1L, "管理员"));

        assertTrue(e.getMessage().contains("一级审批人"));
        verify(engine, never()).start(any());
        verify(formService, never()).saveInstanceForm(anyString(), any(), any(), anyString());
    }
}