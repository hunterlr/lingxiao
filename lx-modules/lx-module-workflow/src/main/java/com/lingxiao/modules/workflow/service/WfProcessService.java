package com.lingxiao.modules.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.workflow.dto.DeploymentInfo;
import com.lingxiao.modules.workflow.dto.ProcessDefVO;
import com.lingxiao.modules.workflow.entity.WfCategory;
import com.lingxiao.modules.workflow.entity.WfForm;
import com.lingxiao.modules.workflow.entity.WfProcessDef;
import com.lingxiao.modules.workflow.mapper.WfCategoryMapper;
import com.lingxiao.modules.workflow.mapper.WfFormMapper;
import com.lingxiao.modules.workflow.mapper.WfProcessDefMapper;
import com.lingxiao.modules.workflow.spi.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程定义服务：部署、列表、启停、删除，以及内置示例流程的自动部署。
 *
 * <p>部署失败（BPMN 不合法）由引擎抛异常，这里不再二次包装，让前端看到原始原因。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.workflow.enabled", havingValue = "true")
public class WfProcessService {

    /** 内置示例流程 key（两级审批），保留给文档与排查引用 */
    public static final String DEMO_PROCESS_KEY = "demo_leave";

    /** 内置示例统一挂到“通用”分类 */
    private static final String DEMO_CATEGORY = "common";

    /**
     * 内置示例流程：每项 = 资源文件 + 绑定的表单 + 说明。
     *
     * <p>顺序即部署顺序；表单必须先于流程部署（见 WorkflowModuleConfig）。</p>
     */
    private static final List<DemoProcess> DEMOS = Arrays.asList(
            new DemoProcess("demo_leave", "processes/demo_leave.bpmn20.xml",
                    WfFormService.DEMO_FORM_KEY, "内置示例：两级审批"),
            new DemoProcess("demo_countersign", "processes/demo_countersign.bpmn20.xml",
                    WfFormService.DEMO_COUNTERSIGN_FORM_KEY, "内置示例：三人会签，按比例通过"),
            new DemoProcess("demo_orsign", "processes/demo_orsign.bpmn20.xml",
                    WfFormService.DEMO_ORSIGN_FORM_KEY, "内置示例：或签，首人通过"),
            new DemoProcess("demo_amount", "processes/demo_amount.bpmn20.xml",
                    WfFormService.DEMO_AMOUNT_FORM_KEY, "内置示例：金额大于 5000 走总经理")
    );

    private final WfProcessDefMapper defMapper;

    private final WfCategoryMapper categoryMapper;

    private final WfFormMapper formMapper;

    private final WorkflowEngine engine;

    public PageResult<ProcessDefVO> page(long pageNum, long pageSize, String keyword, String categoryKey) {
        Page<WfProcessDef> page = new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        LambdaQueryWrapper<WfProcessDef> wrapper = new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getDelFlag, "0")
                .and(StringUtils.hasText(keyword), w -> w
                        .like(WfProcessDef::getName, keyword)
                        .or().like(WfProcessDef::getProcessKey, keyword))
                .eq(StringUtils.hasText(categoryKey), WfProcessDef::getCategoryKey, categoryKey)
                .orderByDesc(WfProcessDef::getCreateTime);
        Page<WfProcessDef> result = defMapper.selectPage(page, wrapper);

        Map<String, String> categoryNames = categoryMapper.selectList(
                        new LambdaQueryWrapper<WfCategory>().eq(WfCategory::getDelFlag, "0"))
                .stream()
                .collect(Collectors.toMap(WfCategory::getCategoryKey, WfCategory::getName, (a, b) -> a));

        Map<String, String> formNames = formMapper.selectList(new LambdaQueryWrapper<WfForm>()
                        .eq(WfForm::getDelFlag, "0"))
                .stream()
                .collect(Collectors.toMap(WfForm::getFormKey, WfForm::getName, (a, b) -> a));

        List<ProcessDefVO> rows = result.getRecords().stream().map(def -> {
            ProcessDefVO vo = new ProcessDefVO();
            vo.setDefId(def.getDefId());
            vo.setProcessKey(def.getProcessKey());
            vo.setName(def.getName());
            vo.setCategoryKey(def.getCategoryKey());
            vo.setCategoryName(categoryNames.get(def.getCategoryKey()));
            vo.setFormKey(def.getFormKey());
            vo.setFormName(formNames.get(def.getFormKey()));
            vo.setProcessVersion(def.getProcessVersion());
            vo.setDeploymentId(def.getDeploymentId());
            vo.setStatus(def.getStatus());
            vo.setRemark(def.getRemark());
            vo.setRunningCount(engine.countRunning(def.getProcessKey()));
            vo.setCreateTime(def.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), rows);
    }

    public List<WfCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<WfCategory>()
                .eq(WfCategory::getDelFlag, "0")
                .eq(WfCategory::getStatus, "0")
                .orderByAsc(WfCategory::getSort));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessDefVO importBpmn(String fileName, byte[] bytes, String categoryKey, String remark, String formKey,
                                   Long operatorId) {
        if (!StringUtils.hasText(fileName) || bytes == null || bytes.length == 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请上传 BPMN 文件");
        }
        if (!fileName.endsWith(".bpmn20.xml") && !fileName.endsWith(".bpmn")) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "只支持 .bpmn20.xml 或 .bpmn 文件");
        }
        DeploymentInfo info = engine.deploy(fileName, bytes);

        WfProcessDef def = new WfProcessDef();
        def.setProcessKey(info.getProcessKey());
        def.setName(info.getName());
        def.setCategoryKey(StringUtils.hasText(categoryKey) ? categoryKey : DEMO_CATEGORY);
        def.setFormKey(StringUtils.hasText(formKey) ? formKey : null);
        def.setProcessVersion(info.getVersion());
        def.setDeploymentId(info.getDeploymentId());
        def.setDefinitionId(info.getDefinitionId());
        def.setResourceName(info.getResourceName());
        def.setStatus("0");
        def.setRemark(remark);
        def.setCreateBy(operatorId);
        def.setCreateTime(LocalDateTime.now());
        def.setDelFlag("0");
        defMapper.insert(def);
        log.info("部署流程 {} v{}，deployment={}", info.getProcessKey(), info.getVersion(), info.getDeploymentId());
        return toVO(def, null);
    }

    public void changeStatus(Long defId, String status, Long operatorId) {
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "状态只能是 0（启用）或 1（停用）");
        }
        WfProcessDef def = mustGet(defId);
        def.setStatus(status);
        def.setUpdateBy(operatorId);
        def.setUpdateTime(LocalDateTime.now());
        defMapper.updateById(def);
    }

    public void delete(Long defId) {
        WfProcessDef def = mustGet(defId);
        long running = engine.countRunning(def.getProcessKey());
        if (running > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "该流程还有 " + running + " 个运行中实例，不能删除");
        }
        engine.deleteDeployment(def.getDeploymentId());
        defMapper.deleteById(defId);
    }

    /**
     * 取某个 key 最新且启用的版本；发起流程时用它校验“流程存在且没停用”。
     */
    public WfProcessDef mustGetActive(String processKey) {
        // 不用 LIMIT：多库兼容约定禁止在 SQL 里写 LIMIT，列表按版本倒序取第一条
        List<WfProcessDef> defs = defMapper.selectList(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getProcessKey, processKey)
                .eq(WfProcessDef::getStatus, "0")
                .eq(WfProcessDef::getDelFlag, "0")
                .orderByDesc(WfProcessDef::getProcessVersion));
        WfProcessDef def = defs.isEmpty() ? null : defs.get(0);
        if (def == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程不存在或已停用：" + processKey);
        }
        return def;
    }

    /**
     * 内置示例流程：每个 key 只部署一次；老库升级时补写 form_key，不重复部署。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureDemoProcess() {
        for (DemoProcess demo : DEMOS) {
            ensureDemoProcess(demo);
        }
    }

    private void ensureDemoProcess(DemoProcess demo) {
        Long exists = defMapper.selectCount(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getProcessKey, demo.processKey)
                .eq(WfProcessDef::getDelFlag, "0"));
        if (exists != null && exists > 0) {
            // 从第一刀 / B1 升级上来：老示例流程可能还没绑表单，补一次，不要求人工重导入
            defMapper.update(null, new LambdaUpdateWrapper<WfProcessDef>()
                    .eq(WfProcessDef::getProcessKey, demo.processKey)
                    .isNull(WfProcessDef::getFormKey)
                    .set(WfProcessDef::getFormKey, demo.formKey));
            return;
        }
        ClassPathResource resource = new ClassPathResource(demo.resource);
        if (!resource.exists()) {
            log.warn("内置示例流程资源缺失：{}", demo.resource);
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = StreamUtils.copyToByteArray(in);
            DeploymentInfo info = engine.deploy(demo.resource, bytes);

            WfProcessDef def = new WfProcessDef();
            def.setProcessKey(info.getProcessKey());
            def.setName(info.getName());
            def.setCategoryKey(DEMO_CATEGORY);
            def.setFormKey(demo.formKey);
            def.setProcessVersion(info.getVersion());
            def.setDeploymentId(info.getDeploymentId());
            def.setDefinitionId(info.getDefinitionId());
            def.setResourceName(info.getResourceName());
            def.setStatus("0");
            def.setRemark(demo.remark);
            def.setCreateTime(LocalDateTime.now());
            def.setDelFlag("0");
            defMapper.insert(def);
            log.info("内置示例流程已部署：{}（绑定表单 {}）", demo.processKey, demo.formKey);
        } catch (IOException e) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "读取内置示例流程失败：" + e.getMessage());
        }
    }

    /** 内置示例的静态描述，避免为 4 条记录再建一张表 */
    private static final class DemoProcess {

        private final String processKey;

        private final String resource;

        private final String formKey;

        private final String remark;

        private DemoProcess(String processKey, String resource, String formKey, String remark) {
            this.processKey = processKey;
            this.resource = resource;
            this.formKey = formKey;
            this.remark = remark;
        }
    }

    private WfProcessDef mustGet(Long defId) {
        WfProcessDef def = defMapper.selectById(defId);
        if (def == null || !"0".equals(def.getDelFlag())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程定义不存在");
        }
        return def;
    }

    private ProcessDefVO toVO(WfProcessDef def, Map<String, String> categoryNames) {
        ProcessDefVO vo = new ProcessDefVO();
        vo.setDefId(def.getDefId());
        vo.setProcessKey(def.getProcessKey());
        vo.setName(def.getName());
        vo.setCategoryKey(def.getCategoryKey());
        vo.setCategoryName(categoryNames == null ? null : categoryNames.get(def.getCategoryKey()));
        vo.setFormKey(def.getFormKey());
        vo.setProcessVersion(def.getProcessVersion());
        vo.setDeploymentId(def.getDeploymentId());
        vo.setStatus(def.getStatus());
        vo.setRemark(def.getRemark());
        vo.setCreateTime(def.getCreateTime());
        return vo;
    }

}