package com.lingxiao.modules.workflow.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.StartProcessCmd;
import com.lingxiao.modules.workflow.dto.StartProcessRequest;
import com.lingxiao.modules.workflow.entity.WfProcessDef;
import com.lingxiao.modules.workflow.spi.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发起流程的入参整形：审批人、姓名映射、业务键都必须在进引擎前写好。
 */
class WfRuntimeServiceTest {

    private WfProcessService processService;

    private WorkflowEngine engine;

    private WfRuntimeService service;

    @BeforeEach
    void setUp() {
        processService = mock(WfProcessService.class);
        engine = mock(WorkflowEngine.class);
        service = new WfRuntimeService(processService, engine);
        WfProcessDef def = new WfProcessDef();
        def.setProcessKey("demo_leave");
        when(processService.mustGetActive("demo_leave")).thenReturn(def);
    }

    @Test
    void startWritesApproversAndNamesIntoVariables() {
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessKey("demo_leave");
        request.setTitle("请假申请");
        request.setRemark("事假一天");
        request.setApprover(10L);
        request.setApproverName("张三");
        request.setApprover2(20L);
        request.setApprover2Name("李四");
        when(engine.start(any())).thenReturn("pi-1");

        String id = service.start(request, 1L, "管理员");

        assertEquals("pi-1", id);
        ArgumentCaptor<StartProcessCmd> captor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(engine).start(captor.capture());
        StartProcessCmd cmd = captor.getValue();
        assertEquals("demo_leave", cmd.getProcessKey());
        assertEquals(1L, cmd.getUserId());
        assertEquals("请假申请", cmd.getTitle());
        assertTrue(cmd.getBusinessKey().startsWith("manual:"));
        Map<String, Object> vars = cmd.getVariables();
        assertEquals("10", vars.get("approver"));
        assertEquals("20", vars.get("approver2"));
        assertEquals("管理员", vars.get("initiatorName"));
        @SuppressWarnings("unchecked")
        Map<String, String> names = (Map<String, String>) vars.get("assigneeNames");
        assertEquals("张三", names.get("10"));
        assertEquals("李四", names.get("20"));
    }

    @Test
    void startRejectsMissingApprover() {
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessKey("demo_leave");
        request.setTitle("请假申请");

        ServiceException e = assertThrows(ServiceException.class, () -> service.start(request, 1L, "管理员"));

        assertEquals("请选择一级审批人", e.getMessage());
    }
}