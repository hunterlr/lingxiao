package com.lingxiao.modules.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.FormField;
import com.lingxiao.modules.workflow.dto.FormOption;
import com.lingxiao.modules.workflow.dto.FormRequest;
import com.lingxiao.modules.workflow.dto.FormSchema;
import com.lingxiao.modules.workflow.dto.FormVO;
import com.lingxiao.modules.workflow.dto.InstanceFormVO;
import com.lingxiao.modules.workflow.entity.WfForm;
import com.lingxiao.modules.workflow.entity.WfFormVersion;
import com.lingxiao.modules.workflow.entity.WfInstanceForm;
import com.lingxiao.modules.workflow.entity.WfProcessDef;
import com.lingxiao.modules.workflow.mapper.WfFormMapper;
import com.lingxiao.modules.workflow.mapper.WfFormVersionMapper;
import com.lingxiao.modules.workflow.mapper.WfInstanceFormMapper;
import com.lingxiao.modules.workflow.mapper.WfProcessDefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 表单定义与实例快照服务。
 *
 * <p>编辑与发布分离：{@code wf_form.schema_json} 是草稿，{@code wf_form_version}
 * 是已发布快照。发起流程只认已发布快照，所以“改了一半没发布”不会影响新单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfFormService {

    /** 内置示例表单 key，与 demo_leave 流程配套 */
    public static final String DEMO_FORM_KEY = "demo_leave_form";

    /** 会签示例表单：approvers 多选 + approvePercent 比例 */
    public static final String DEMO_COUNTERSIGN_FORM_KEY = "demo_countersign_form";

    /** 或签示例表单 */
    public static final String DEMO_ORSIGN_FORM_KEY = "demo_orsign_form";

    /** 金额分支示例表单 */
    public static final String DEMO_AMOUNT_FORM_KEY = "demo_amount_form";

    private final WfFormMapper formMapper;

    private final WfFormVersionMapper versionMapper;

    private final WfInstanceFormMapper instanceFormMapper;

    private final WfProcessDefMapper defMapper;

    private final WfFormValidator validator;

    private final ObjectMapper objectMapper;

    public PageResult<FormVO> page(long pageNum, long pageSize, String keyword) {
        Page<WfForm> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<WfForm> wrapper = new LambdaQueryWrapper<WfForm>()
                .eq(WfForm::getDelFlag, "0")
                .and(StringUtils.hasText(keyword), w -> w
                        .like(WfForm::getName, keyword)
                        .or().like(WfForm::getFormKey, keyword))
                .orderByDesc(WfForm::getCreateTime);
        Page<WfForm> result = formMapper.selectPage(page, wrapper);
        List<FormVO> rows = result.getRecords().stream()
                .map(form -> toVO(form, false))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), rows);
    }

    public FormVO detail(Long formId) {
        return toVO(mustGet(formId), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long formId, FormRequest request, Long operatorId) {
        validator.checkSchema(request.getSchema());
        String schemaJson = validator.toJson(request.getSchema());
        LocalDateTime now = LocalDateTime.now();

        if (formId == null) {
            Long exists = formMapper.selectCount(new LambdaQueryWrapper<WfForm>()
                    .eq(WfForm::getFormKey, request.getFormKey())
                    .eq(WfForm::getDelFlag, "0"));
            if (exists != null && exists > 0) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "表单标识已存在：" + request.getFormKey());
            }
            WfForm form = new WfForm();
            form.setFormKey(request.getFormKey());
            form.setName(request.getName());
            form.setSchemaJson(schemaJson);
            form.setVersion(0);
            form.setRemark(request.getRemark());
            form.setCreateBy(operatorId);
            form.setCreateTime(now);
            form.setDelFlag("0");
            formMapper.insert(form);
            return form.getFormId();
        }

        WfForm form = mustGet(formId);
        if (!form.getFormKey().equals(request.getFormKey()) && form.getVersion() > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "表单已发布，不能再改表单标识");
        }
        if (!form.getFormKey().equals(request.getFormKey())) {
            Long exists = formMapper.selectCount(new LambdaQueryWrapper<WfForm>()
                    .eq(WfForm::getFormKey, request.getFormKey())
                    .eq(WfForm::getDelFlag, "0")
                    .ne(WfForm::getFormId, formId));
            if (exists != null && exists > 0) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "表单标识已存在：" + request.getFormKey());
            }
        }
        form.setFormKey(request.getFormKey());
        form.setName(request.getName());
        form.setSchemaJson(schemaJson);
        form.setRemark(request.getRemark());
        form.setUpdateBy(operatorId);
        form.setUpdateTime(now);
        formMapper.updateById(form);
        return form.getFormId();
    }

    /**
     * 发布：版本 +1，写快照。发布后新发起的单用新版本，历史单仍按旧快照渲染。
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer publish(Long formId, Long operatorId) {
        WfForm form = mustGet(formId);
        FormSchema schema = validator.parse(form.getSchemaJson());
        validator.checkSchema(schema);

        int next = (form.getVersion() == null ? 0 : form.getVersion()) + 1;
        LocalDateTime now = LocalDateTime.now();
        WfFormVersion version = new WfFormVersion();
        version.setFormId(form.getFormId());
        version.setVersion(next);
        version.setSchemaJson(form.getSchemaJson());
        version.setCreateBy(operatorId);
        version.setCreateTime(now);
        versionMapper.insert(version);

        form.setVersion(next);
        form.setUpdateBy(operatorId);
        form.setUpdateTime(now);
        formMapper.updateById(form);
        return next;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long formId) {
        WfForm form = mustGet(formId);
        Long usedByDef = defMapper.selectCount(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getFormKey, form.getFormKey())
                .eq(WfProcessDef::getDelFlag, "0"));
        if (usedByDef != null && usedByDef > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "还有流程定义在绑定这个表单，不能删除");
        }
        Long usedByInstance = instanceFormMapper.selectCount(new LambdaQueryWrapper<WfInstanceForm>()
                .eq(WfInstanceForm::getFormKey, form.getFormKey()));
        if (usedByInstance != null && usedByInstance > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "已有实例使用过这个表单，不能删除");
        }
        versionMapper.delete(new LambdaQueryWrapper<WfFormVersion>()
                .eq(WfFormVersion::getFormId, formId));
        formMapper.deleteById(formId);
    }

    /**
     * 取已发布表单（发起流程用）；没绑定表单返回 null，绑了但没发布则拒绝。
     */
    public WfForm mustPublished(String formKey) {
        if (!StringUtils.hasText(formKey)) {
            return null;
        }
        WfForm form = formMapper.selectOne(new LambdaQueryWrapper<WfForm>()
                .eq(WfForm::getFormKey, formKey)
                .eq(WfForm::getDelFlag, "0"));
        if (form == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程绑定的表单不存在：" + formKey);
        }
        if (form.getVersion() == null || form.getVersion() <= 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程绑定的表单还没发布：" + formKey);
        }
        return form;
    }

    /** 取某个已发布版本的 schema。 */
    public FormSchema schemaOf(WfForm form, Integer version) {
        WfFormVersion snapshot = versionMapper.selectOne(new LambdaQueryWrapper<WfFormVersion>()
                .eq(WfFormVersion::getFormId, form.getFormId())
                .eq(WfFormVersion::getVersion, version));
        if (snapshot == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "表单「" + form.getName() + "」缺少 v" + version + " 的发布快照");
        }
        return validator.parse(snapshot.getSchemaJson());
    }

    /** 发起时定格：写实例表单快照。 */
    public void saveInstanceForm(String processInstanceId, WfForm form, Integer version, String dataJson) {
        WfInstanceForm snapshot = new WfInstanceForm();
        snapshot.setProcessInstanceId(processInstanceId);
        snapshot.setFormKey(form.getFormKey());
        snapshot.setFormVersion(version);
        snapshot.setDataJson(dataJson);
        snapshot.setCreateTime(LocalDateTime.now());
        instanceFormMapper.insert(snapshot);
    }

    /** 详情 / 办理页读实例快照；没有表单的实例返回 null。 */
    public InstanceFormVO instanceForm(String processInstanceId) {
        WfInstanceForm snapshot = instanceFormMapper.selectOne(new LambdaQueryWrapper<WfInstanceForm>()
                .eq(WfInstanceForm::getProcessInstanceId, processInstanceId));
        if (snapshot == null) {
            return null;
        }
        WfForm form = formMapper.selectOne(new LambdaQueryWrapper<WfForm>()
                .eq(WfForm::getFormKey, snapshot.getFormKey()));
        if (form == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "实例引用的表单已被删除：" + snapshot.getFormKey());
        }
        InstanceFormVO vo = new InstanceFormVO();
        vo.setFormKey(snapshot.getFormKey());
        vo.setFormVersion(snapshot.getFormVersion());
        vo.setSchema(schemaOf(form, snapshot.getFormVersion()));
        vo.setData(parseData(snapshot.getDataJson()));
        return vo;
    }

    /** 实例表单值序列化，供发起时落快照。 */
    public String toJson(java.util.Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? new java.util.LinkedHashMap<>() : data);
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "实例表单数据序列化失败");
        }
    }

    /**
     * 内置示例表单：四个流程各配一份，首次启用时创建并发布 v1。
     *
     * <p>会签 / 或签共用“人员多选”，条件分支用金额；字段 key 必须与示例 BPMN
     * 里的变量名一致（approvers / approvePercent / amount / approver / boss）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureDemoForm() {
        ensureForm(DEMO_FORM_KEY, "示例：请假申请", leaveSchema(), "内置示例，与 demo_leave 流程配套");
        ensureForm(DEMO_COUNTERSIGN_FORM_KEY, "示例：会签申请", countersignSchema(),
                "内置示例，与 demo_countersign 流程配套：会签人 + 通过比例");
        ensureForm(DEMO_ORSIGN_FORM_KEY, "示例：或签申请", orsignSchema(),
                "内置示例，与 demo_orsign 流程配套：任意一人通过即可");
        ensureForm(DEMO_AMOUNT_FORM_KEY, "示例：报销申请", amountSchema(),
                "内置示例，与 demo_amount 流程配套：金额大于 5000 走总经理");
    }

    private void ensureForm(String formKey, String name, FormSchema schema, String remark) {
        Long exists = formMapper.selectCount(new LambdaQueryWrapper<WfForm>()
                .eq(WfForm::getFormKey, formKey)
                .eq(WfForm::getDelFlag, "0"));
        if (exists != null && exists > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        WfForm form = new WfForm();
        form.setFormKey(formKey);
        form.setName(name);
        form.setSchemaJson(validator.toJson(schema));
        form.setVersion(1);
        form.setRemark(remark);
        form.setCreateTime(now);
        form.setDelFlag("0");
        formMapper.insert(form);

        WfFormVersion version = new WfFormVersion();
        version.setFormId(form.getFormId());
        version.setVersion(1);
        version.setSchemaJson(form.getSchemaJson());
        version.setCreateTime(now);
        versionMapper.insert(version);
        log.info("内置示例表单已发布：{} v1", formKey);
    }

    private FormSchema leaveSchema() {
        FormSchema schema = new FormSchema();
        schema.setFields(Arrays.asList(
                userField("approver", "一级审批人", false),
                userField("approver2", "二级审批人", false),
                numberField("days", "请假天数", new BigDecimal("0.5"), new BigDecimal("365")),
                textareaField("reason", "请假事由", 500)
        ));
        return schema;
    }

    private FormSchema countersignSchema() {
        FormSchema schema = new FormSchema();
        schema.setFields(Arrays.asList(
                userField("approvers", "会签人", true),
                percentField("approvePercent", "通过比例（%）"),
                textareaField("reason", "申请事由", 500)
        ));
        return schema;
    }

    private FormSchema orsignSchema() {
        FormSchema schema = new FormSchema();
        schema.setFields(Arrays.asList(
                userField("approvers", "或签人", true),
                textareaField("reason", "申请事由", 500)
        ));
        return schema;
    }

    private FormSchema amountSchema() {
        FormSchema schema = new FormSchema();
        schema.setFields(Arrays.asList(
                numberField("amount", "报销金额", new BigDecimal("1"), new BigDecimal("1000000")),
                userField("approver", "主管", false),
                userField("boss", "总经理", false),
                textareaField("reason", "报销事由", 500)
        ));
        return schema;
    }

    private FormField userField(String key, String label, boolean multiple) {
        FormField field = new FormField();
        field.setKey(key);
        field.setLabel(label);
        field.setType(WfFormValidator.TYPE_USER);
        field.setRequired(true);
        field.setMultiple(multiple);
        return field;
    }

    private FormField numberField(String key, String label, BigDecimal min, BigDecimal max) {
        FormField field = new FormField();
        field.setKey(key);
        field.setLabel(label);
        field.setType(WfFormValidator.TYPE_NUMBER);
        field.setRequired(true);
        field.setMin(min);
        field.setMax(max);
        field.setPrecision(2);
        return field;
    }

    private FormField percentField(String key, String label) {
        FormField field = new FormField();
        field.setKey(key);
        field.setLabel(label);
        field.setType(WfFormValidator.TYPE_NUMBER);
        field.setRequired(true);
        field.setMin(BigDecimal.ONE);
        field.setMax(new BigDecimal("100"));
        field.setPrecision(0);
        field.setDefaultValue("50");
        return field;
    }

    private FormField textareaField(String key, String label, Integer maxLength) {
        FormField field = new FormField();
        field.setKey(key);
        field.setLabel(label);
        field.setType(WfFormValidator.TYPE_TEXTAREA);
        field.setRequired(true);
        field.setMaxLength(maxLength);
        return field;
    }

    private FormVO toVO(WfForm form, boolean withSchema) {
        FormVO vo = new FormVO();
        vo.setFormId(form.getFormId());
        vo.setFormKey(form.getFormKey());
        vo.setName(form.getName());
        vo.setRemark(form.getRemark());
        vo.setVersion(form.getVersion());
        vo.setCreateTime(form.getCreateTime());
        vo.setUpdateTime(form.getUpdateTime());
        if (withSchema) {
            FormSchema schema = validator.parse(form.getSchemaJson());
            vo.setSchema(schema);
        }
        vo.setChanged(hasUnpublishedChange(form));
        return vo;
    }

    private boolean hasUnpublishedChange(WfForm form) {
        if (form.getVersion() == null || form.getVersion() <= 0) {
            return true;
        }
        WfFormVersion snapshot = versionMapper.selectOne(new LambdaQueryWrapper<WfFormVersion>()
                .eq(WfFormVersion::getFormId, form.getFormId())
                .eq(WfFormVersion::getVersion, form.getVersion()));
        return snapshot == null || !Objects.equals(snapshot.getSchemaJson(), form.getSchemaJson());
    }

    private WfForm mustGet(Long formId) {
        WfForm form = formMapper.selectById(formId);
        if (form == null || !"0".equals(form.getDelFlag())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "表单不存在");
        }
        return form;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> parseData(String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return new java.util.LinkedHashMap<>();
            }
            return objectMapper.readValue(json, new TypeReference<java.util.LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "实例表单数据解析失败");
        }
    }

}