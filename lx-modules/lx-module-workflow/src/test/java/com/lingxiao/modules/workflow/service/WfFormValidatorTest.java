package com.lingxiao.modules.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.FormField;
import com.lingxiao.modules.workflow.dto.FormSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表单取值校验：必填、范围、选项、保留名都在后端拦住。
 */
class WfFormValidatorTest {

    private WfFormValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WfFormValidator(new ObjectMapper());
    }

    private FormSchema schema(FormField... fields) {
        FormSchema schema = new FormSchema();
        schema.setFields(Arrays.asList(fields));
        return schema;
    }

    private FormField number(String key, String label, String min, String max, boolean required) {
        FormField field = new FormField();
        field.setKey(key);
        field.setLabel(label);
        field.setType(WfFormValidator.TYPE_NUMBER);
        field.setRequired(required);
        field.setMin(new BigDecimal(min));
        field.setMax(new BigDecimal(max));
        return field;
    }

    @Test
    void requiredFieldRejectedWhenBlank() {
        Map<String, Object> data = new HashMap<>();
        data.put("reason", "  ");

        ServiceException e = assertThrows(ServiceException.class,
                () -> validator.validate(schema(text("reason", "事由")), data));

        assertTrue(e.getMessage().contains("不能为空"));
    }

    @Test
    void numberOutOfRangeRejected() {
        Map<String, Object> data = new HashMap<>();
        data.put("amount", "600");

        ServiceException e = assertThrows(ServiceException.class,
                () -> validator.validate(schema(number("amount", "金额", "0", "500", true)), data));

        assertTrue(e.getMessage().contains("不能大于 500"));
    }

    @Test
    void normalizedValuesKeepTypes() {
        FormField amount = number("amount", "金额", "0", "10000", true);
        FormField urgent = new FormField();
        urgent.setKey("urgent");
        urgent.setLabel("加急");
        urgent.setType(WfFormValidator.TYPE_SWITCH);
        FormField approvers = new FormField();
        approvers.setKey("approvers");
        approvers.setLabel("会签人");
        approvers.setType(WfFormValidator.TYPE_USER);
        approvers.setMultiple(true);
        approvers.setRequired(true);

        Map<String, Object> data = new HashMap<>();
        data.put("amount", "12.50");
        data.put("urgent", "true");
        data.put("approvers", Arrays.asList("1", "2"));
        data.put("junk", "丢弃");

        Map<String, Object> result = validator.validate(schema(amount, urgent, approvers), data);

        assertEquals(new BigDecimal("12.50"), result.get("amount"));
        assertEquals(Boolean.TRUE, result.get("urgent"));
        assertEquals(Arrays.asList("1", "2"), result.get("approvers"));
        assertFalse(result.containsKey("junk"));
    }

    @Test
    void optionOutsideListRejected() {
        FormField type = new FormField();
        type.setKey("type");
        type.setLabel("类型");
        type.setType(WfFormValidator.TYPE_SELECT);
        com.lingxiao.modules.workflow.dto.FormOption option = new com.lingxiao.modules.workflow.dto.FormOption();
        option.setLabel("事假");
        option.setValue("personal");
        type.setOptions(java.util.Collections.singletonList(option));

        Map<String, Object> data = new HashMap<>();
        data.put("type", "sick");

        ServiceException e = assertThrows(ServiceException.class,
                () -> validator.validate(schema(type), data));
        assertTrue(e.getMessage().contains("不在选项内"));
    }

    @Test
    void reservedKeyRejectedOnSave() {
        FormField field = text("title", "标题");

        ServiceException e = assertThrows(ServiceException.class, () -> validator.checkSchema(schema(field)));

        assertTrue(e.getMessage().contains("保留名"));
    }

    @Test
    void duplicateKeyRejectedOnSave() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> validator.checkSchema(schema(text("reason", "事由"), text("reason", "事由2"))));

        assertTrue(e.getMessage().contains("重复"));
    }

    private FormField text(String key, String label) {
        FormField field = new FormField();
        field.setKey(key);
        field.setLabel(label);
        field.setType(WfFormValidator.TYPE_TEXTAREA);
        field.setRequired(true);
        field.setMaxLength(500);
        return field;
    }
}