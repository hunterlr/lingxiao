package com.lingxiao.modules.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.FormField;
import com.lingxiao.modules.workflow.dto.FormRequest;
import com.lingxiao.modules.workflow.dto.FormSchema;
import com.lingxiao.modules.workflow.entity.WfForm;
import com.lingxiao.modules.workflow.entity.WfFormVersion;
import com.lingxiao.modules.workflow.mapper.WfFormMapper;
import com.lingxiao.modules.workflow.mapper.WfFormVersionMapper;
import com.lingxiao.modules.workflow.mapper.WfInstanceFormMapper;
import com.lingxiao.modules.workflow.mapper.WfProcessDefMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表单发布与删除保护：版本只增、被流程或实例引用时不能删。
 */
class WfFormServiceTest {

    private WfFormMapper formMapper;

    private WfFormVersionMapper versionMapper;

    private WfProcessDefMapper defMapper;

    private WfFormValidator validator;

    private WfFormService service;

    @BeforeEach
    void setUp() {
        formMapper = mock(WfFormMapper.class);
        versionMapper = mock(WfFormVersionMapper.class);
        defMapper = mock(WfProcessDefMapper.class);
        validator = new WfFormValidator(new ObjectMapper());
        service = new WfFormService(formMapper, versionMapper, mock(WfInstanceFormMapper.class),
                defMapper, validator, new ObjectMapper());
    }

    private WfForm form(int version) {
        WfForm form = new WfForm();
        form.setFormId(1L);
        form.setFormKey("leave_form");
        form.setName("请假");
        form.setSchemaJson("{\"fields\":[{\"key\":\"reason\",\"label\":\"事由\",\"type\":\"textarea\",\"required\":true}]}");
        form.setVersion(version);
        form.setDelFlag("0");
        return form;
    }

    @Test
    void publishBumpsVersionAndWritesSnapshot() {
        when(formMapper.selectById(1L)).thenReturn(form(1));

        int next = service.publish(1L, 1L);

        assertEquals(2, next);
        ArgumentCaptor<WfFormVersion> captor = ArgumentCaptor.forClass(WfFormVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getVersion());
        assertEquals(1L, captor.getValue().getFormId());
        ArgumentCaptor<WfForm> updated = ArgumentCaptor.forClass(WfForm.class);
        verify(formMapper).updateById(updated.capture());
        assertEquals(2, updated.getValue().getVersion());
    }

    @Test
    void publishedFormKeyCannotChange() {
        when(formMapper.selectById(1L)).thenReturn(form(1));
        FormRequest request = new FormRequest();
        request.setFormKey("another_key");
        request.setName("请假");
        FormSchema schema = new FormSchema();
        FormField field = new FormField();
        field.setKey("reason");
        field.setLabel("事由");
        field.setType(WfFormValidator.TYPE_TEXTAREA);
        field.setRequired(true);
        schema.setFields(Collections.singletonList(field));
        request.setSchema(schema);

        ServiceException e = assertThrows(ServiceException.class, () -> service.save(1L, request, 1L));

        assertTrue(e.getMessage().contains("不能再改表单标识"));
    }

    @Test
    void deleteRejectedWhenProcessDefStillBound() {
        when(formMapper.selectById(1L)).thenReturn(form(1));
        when(defMapper.selectCount(any())).thenReturn(1L);

        ServiceException e = assertThrows(ServiceException.class, () -> service.delete(1L));

        assertTrue(e.getMessage().contains("流程定义"));
        verify(formMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void schemaOfMissingVersionRejected() {
        when(versionMapper.selectOne(any())).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.schemaOf(form(2), 2));

        assertTrue(e.getMessage().contains("发布快照"));
    }
}