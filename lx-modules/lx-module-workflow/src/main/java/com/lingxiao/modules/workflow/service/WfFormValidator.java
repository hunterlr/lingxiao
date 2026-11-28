package com.lingxiao.modules.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.FormField;
import com.lingxiao.modules.workflow.dto.FormOption;
import com.lingxiao.modules.workflow.dto.FormSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 动态表单的解析、定义校验与取值校验。
 *
 * <p>前端渲染时也会按同一份 schema 校验一次，但**后端为准**：接口直接调用、
 * 或前端版本不一致时，脏值不能进流程变量。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfFormValidator {

    public static final String TYPE_INPUT = "input";
    public static final String TYPE_TEXTAREA = "textarea";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_SELECT = "select";
    public static final String TYPE_RADIO = "radio";
    public static final String TYPE_DATE = "date";
    public static final String TYPE_DATETIME = "datetime";
    public static final String TYPE_SWITCH = "switch";
    public static final String TYPE_USER = "user";

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            TYPE_INPUT, TYPE_TEXTAREA, TYPE_NUMBER, TYPE_SELECT, TYPE_RADIO,
            TYPE_DATE, TYPE_DATETIME, TYPE_SWITCH, TYPE_USER));

    private static final Set<String> OPTION_TYPES = new HashSet<>(Arrays.asList(TYPE_SELECT, TYPE_RADIO));

    /** 系统占用的流程变量名，表单字段不能用，否则会覆盖发起人 / 标题 */
    private static final Set<String> RESERVED_KEYS = new HashSet<>(Arrays.asList(
            "title", "remark", "initiator", "initiatorName", "assigneeNames"));

    private final ObjectMapper objectMapper;

    /** JSON → Schema；解析失败按参数错误处理，不把 Jackson 异常抛给前端。 */
    public FormSchema parse(String json) {
        if (!StringUtils.hasText(json)) {
            return new FormSchema();
        }
        try {
            FormSchema schema = objectMapper.readValue(json, FormSchema.class);
            return schema == null ? new FormSchema() : schema;
        } catch (Exception e) {
            log.warn("表单 schema 解析失败：{}", e.getMessage());
            throw new ServiceException(ErrorCode.BAD_REQUEST, "表单定义不是合法的 JSON");
        }
    }

    public String toJson(FormSchema schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "表单定义序列化失败");
        }
    }

    /**
     * 保存时的定义校验：字段不能为空、key 唯一、类型合法、选项与范围自洽。
     */
    public void checkSchema(FormSchema schema) {
        if (schema == null || schema.getFields() == null || schema.getFields().isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "表单至少要有一个字段");
        }
        Set<String> keys = new HashSet<>();
        for (FormField field : schema.getFields()) {
            if (!StringUtils.hasText(field.getKey())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "字段 key 不能为空");
            }
            if (!field.getKey().matches("^[a-zA-Z][a-zA-Z0-9_]{0,63}$")) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "字段 key 只能以字母开头，包含字母、数字、下划线：" + field.getKey());
            }
            if (RESERVED_KEYS.contains(field.getKey())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "字段 key 是系统保留名，不能使用：" + field.getKey());
            }
            if (!keys.add(field.getKey())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "字段 key 重复：" + field.getKey());
            }
            if (!StringUtils.hasText(field.getLabel())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "字段「" + field.getKey() + "」缺少名称");
            }
            if (!TYPES.contains(field.getType())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "字段「" + field.getLabel() + "」类型不合法：" + field.getType());
            }
            if (OPTION_TYPES.contains(field.getType())) {
                if (field.getOptions() == null || field.getOptions().isEmpty()) {
                    throw new ServiceException(ErrorCode.BAD_REQUEST,
                            "字段「" + field.getLabel() + "」至少要有一个选项");
                }
                for (FormOption option : field.getOptions()) {
                    if (!StringUtils.hasText(option.getValue())) {
                        throw new ServiceException(ErrorCode.BAD_REQUEST,
                                "字段「" + field.getLabel() + "」的选项值不能为空");
                    }
                }
            }
            if (field.getPattern() != null) {
                try {
                    Pattern.compile(field.getPattern());
                } catch (PatternSyntaxException e) {
                    throw new ServiceException(ErrorCode.BAD_REQUEST,
                            "字段「" + field.getLabel() + "」的正则不合法");
                }
            }
            if (field.getMin() != null && field.getMax() != null
                    && field.getMin().compareTo(field.getMax()) > 0) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "字段「" + field.getLabel() + "」的最小值大于最大值");
            }
        }
    }

    /**
     * 取值校验 + 归一化：返回可以直接写进流程变量的 Map。
     *
     * <p>只保留 schema 里定义过的 key，多余字段丢弃，避免脏变量进引擎。</p>
     */
    public Map<String, Object> validate(FormSchema schema, Map<String, Object> raw) {
        FormSchema target = schema == null ? new FormSchema() : schema;
        Map<String, Object> source = raw == null ? new LinkedHashMap<>() : raw;
        Map<String, Object> result = new LinkedHashMap<>();
        for (FormField field : target.getFields()) {
            Object value = source.get(field.getKey());
            boolean required = Boolean.TRUE.equals(field.getRequired());
            if (isEmpty(value)) {
                if (required) {
                    throw new ServiceException(ErrorCode.BAD_REQUEST,
                            "字段「" + field.getLabel() + "」不能为空");
                }
                continue;
            }
            result.put(field.getKey(), normalize(field, value));
        }
        return result;
    }

    private Object normalize(FormField field, Object value) {
        String type = field.getType();
        switch (type) {
            case TYPE_INPUT:
            case TYPE_TEXTAREA:
                return checkText(field, String.valueOf(value));
            case TYPE_NUMBER:
                return checkNumber(field, value);
            case TYPE_SELECT:
            case TYPE_RADIO:
                return checkOption(field, String.valueOf(value));
            case TYPE_DATE:
                return checkDate(field, String.valueOf(value));
            case TYPE_DATETIME:
                return checkDateTime(field, String.valueOf(value));
            case TYPE_SWITCH:
                return checkSwitch(field, value);
            case TYPE_USER:
                return checkUser(field, value);
            default:
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "字段「" + field.getLabel() + "」类型不支持：" + type);
        }
    }

    private String checkText(FormField field, String text) {
        String value = text.trim();
        if (field.getMaxLength() != null && value.length() > field.getMaxLength()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "字段「" + field.getLabel() + "」不能超过 " + field.getMaxLength() + " 个字符");
        }
        if (field.getPattern() != null && !Pattern.matches(field.getPattern(), value)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "字段「" + field.getLabel() + "」格式不正确");
        }
        return value;
    }

    private BigDecimal checkNumber(FormField field, Object value) {
        BigDecimal number;
        try {
            number = value instanceof Number
                    ? new BigDecimal(value.toString())
                    : new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "字段「" + field.getLabel() + "」必须是数字");
        }
        if (field.getMin() != null && number.compareTo(field.getMin()) < 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "字段「" + field.getLabel() + "」不能小于 " + strip(field.getMin()));
        }
        if (field.getMax() != null && number.compareTo(field.getMax()) > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "字段「" + field.getLabel() + "」不能大于 " + strip(field.getMax()));
        }
        return number;
    }

    private String checkOption(FormField field, String value) {
        for (FormOption option : field.getOptions()) {
            if (option.getValue().equals(value)) {
                return value;
            }
        }
        throw new ServiceException(ErrorCode.BAD_REQUEST,
                "字段「" + field.getLabel() + "」的值不在选项内");
    }

    private String checkDate(FormField field, String value) {
        try {
            return LocalDate.parse(value).toString();
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "字段「" + field.getLabel() + "」日期格式应为 yyyy-MM-dd");
        }
    }

    private String checkDateTime(FormField field, String value) {
        try {
            return LocalDateTime.parse(value).toString();
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "字段「" + field.getLabel() + "」时间格式应为 yyyy-MM-ddTHH:mm:ss");
        }
    }

    private Boolean checkSwitch(FormField field, Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        throw new ServiceException(ErrorCode.BAD_REQUEST,
                "字段「" + field.getLabel() + "」只能是 true / false");
    }

    private Object checkUser(FormField field, Object value) {
        if (Boolean.TRUE.equals(field.getMultiple())) {
            List<String> ids = new ArrayList<>();
            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (!isEmpty(item)) {
                        ids.add(String.valueOf(item));
                    }
                }
            } else {
                for (String item : String.valueOf(value).split(",")) {
                    if (StringUtils.hasText(item)) {
                        ids.add(item.trim());
                    }
                }
            }
            if (ids.isEmpty()) {
                throw new ServiceException(ErrorCode.BAD_REQUEST,
                        "字段「" + field.getLabel() + "」至少选择一个用户");
            }
            return ids;
        }
        return String.valueOf(value);
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence) {
            return !StringUtils.hasText(value.toString());
        }
        if (value instanceof List) {
            return ((List<?>) value).isEmpty();
        }
        return false;
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}