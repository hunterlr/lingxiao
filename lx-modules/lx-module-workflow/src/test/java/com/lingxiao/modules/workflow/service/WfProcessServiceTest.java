package com.lingxiao.modules.workflow.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.entity.WfProcessDef;
import com.lingxiao.modules.workflow.mapper.WfCategoryMapper;
import com.lingxiao.modules.workflow.mapper.WfFormMapper;
import com.lingxiao.modules.workflow.mapper.WfProcessDefMapper;
import com.lingxiao.modules.workflow.spi.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程定义的业务校验：不启引擎，只验“该拦的拦住了”。
 */
class WfProcessServiceTest {

    private WfProcessDefMapper defMapper;

    private WorkflowEngine engine;

    private WfProcessService service;

    @BeforeEach
    void setUp() {
        defMapper = mock(WfProcessDefMapper.class);
        engine = mock(WorkflowEngine.class);
        service = new WfProcessService(defMapper, mock(WfCategoryMapper.class), mock(WfFormMapper.class), engine);
    }

    @Test
    void deleteRejectedWhenInstanceRunning() {
        WfProcessDef def = new WfProcessDef();
        def.setDefId(1L);
        def.setProcessKey("demo_leave");
        def.setDeploymentId("dep-1");
        def.setDelFlag("0");
        when(defMapper.selectById(1L)).thenReturn(def);
        when(engine.countRunning("demo_leave")).thenReturn(2L);

        ServiceException e = assertThrows(ServiceException.class, () -> service.delete(1L));

        assertTrue(e.getMessage().contains("运行中"));
        verify(engine, never()).deleteDeployment(any());
    }

    @Test
    void deleteRemovesDeploymentAndRow() {
        WfProcessDef def = new WfProcessDef();
        def.setDefId(1L);
        def.setProcessKey("demo_leave");
        def.setDeploymentId("dep-1");
        def.setDelFlag("0");
        when(defMapper.selectById(1L)).thenReturn(def);
        when(engine.countRunning("demo_leave")).thenReturn(0L);

        service.delete(1L);

        verify(engine).deleteDeployment("dep-1");
        verify(defMapper).deleteById(1L);
    }

    @Test
    void importRejectsNonBpmnFile() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.importBpmn("leave.txt", new byte[]{1}, null, null, null, 1L));

        assertTrue(e.getMessage().contains("bpmn"));
        verify(engine, never()).deploy(any(), any());
    }

    @Test
    void mustGetActiveFailsWhenProcessDisabledOrMissing() {
        when(defMapper.selectOne(any())).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class, () -> service.mustGetActive("not_exists"));

        assertEquals("流程不存在或已停用：not_exists", e.getMessage());
    }
}