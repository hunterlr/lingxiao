package com.lingxiao.modules.generator.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.generator.dto.GenPreviewVO;
import com.lingxiao.modules.generator.entity.GenTable;
import com.lingxiao.modules.generator.entity.GenTableColumn;
import com.lingxiao.modules.generator.support.GenConstants;
import com.lingxiao.modules.generator.support.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成：组装模板变量、渲染、打包。
 *
 * <p>生成物固定 7 个文件：</p>
 * <ol>
 *   <li>Entity：按真实列生成，<b>不继承 BaseEntity</b>，避免强行要求 del_flag / version；</li>
 *   <li>Mapper：MyBatis-Plus 空接口；</li>
 *   <li>Service：具体类（与项目里 SysJobService 的风格一致），不做 interface + impl 双份；</li>
 *   <li>Controller：路径 / 权限码 / 操作日志按当前基座规范生成；</li>
 *   <li>前端 api.js；</li>
 *   <li>前端 index.vue（列表 + 查询 + 表单）；</li>
 *   <li>菜单 / 按钮 SQL（占位符由执行者替换）。</li>
 * </ol>
 *
 * <p><b>为什么只下载不写盘：</b>写盘要处理包名白名单、覆盖确认、IDE 热加载、
 * Windows 文件占用等一堆问题；v1.0 先把“生成结果正确”做扎实，
 * 下载后由开发者放进目标模块编译，路径问题交给人的判断。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.generator.enabled", havingValue = "true")
public class CodeGenService {

    private static final Pattern CLASS_NAME = Pattern.compile(GenConstants.CLASS_NAME_REGEX);
    private static final Pattern MODULE_NAME = Pattern.compile(GenConstants.MODULE_NAME_REGEX);
    private static final Pattern BUSINESS_NAME = Pattern.compile(GenConstants.BUSINESS_NAME_REGEX);
    private static final Pattern JAVA_FIELD = Pattern.compile(GenConstants.JAVA_FIELD_REGEX);

    private static final String TEMPLATE_DIR = "templates/gen/";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GenTableService genTableService;

    private VelocityEngine engine;

    @PostConstruct
    public void init() {
        VelocityEngine velocity = new VelocityEngine();
        velocity.setProperty(RuntimeConstants.RESOURCE_LOADERS, "class");
        velocity.setProperty("resource.loader.class.class", ClasspathResourceLoader.class.getName());
        // 变量缺失时输出空串而不是抛异常：注释 / 字典等可选字段本来就允许为空
        velocity.setProperty(RuntimeConstants.RUNTIME_REFERENCES_STRICT, false);
        // 2.3 起 input.encoding 已废弃，统一用 resource.default_encoding
        velocity.setProperty("resource.default_encoding", "UTF-8");
        velocity.setProperty("output.encoding", "UTF-8");
        velocity.init();
        this.engine = velocity;
    }

    /* ==================== 对外接口 ==================== */

    public List<GenPreviewVO> preview(Long tableId) {
        Generation generation = prepare(tableId);
        List<GenPreviewVO> previews = new ArrayList<>();
        for (TemplateSpec spec : specs(generation)) {
            previews.add(new GenPreviewVO(
                    spec.displayName,
                    spec.path,
                    spec.language,
                    render(spec.template, generation.context)));
        }
        return previews;
    }

    public byte[] download(Long tableId) {
        Generation generation = prepare(tableId);
        String root = generation.table.getTableName() + "/";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (TemplateSpec spec : specs(generation)) {
                zip.putNextEntry(new ZipEntry(root + spec.path));
                zip.write(render(spec.template, generation.context).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            log.error("生成 ZIP 失败：tableId={}", tableId, e);
            throw new ServiceException("生成 ZIP 失败，请稍后重试");
        }
    }

    /* ==================== 变量组装 ==================== */

    private Generation prepare(Long tableId) {
        GenTable table = genTableService.requireTable(tableId);
        List<GenTableColumn> columns = genTableService.listColumns(tableId);
        if (columns.isEmpty()) {
            throw new ServiceException("表 " + table.getTableName() + " 没有字段配置，请重新导入");
        }
        GenTableColumn pk = columns.stream()
                .filter(column -> GenConstants.YES.equals(column.getIsPk()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("表 " + table.getTableName() + " 缺少主键字段配置"));
        validate(table, columns);

        String moduleName = table.getModuleName();
        String businessName = table.getBusinessName();
        String functionName = StringUtils.hasText(table.getFunctionName())
                ? table.getFunctionName().trim() : table.getClassName();

        VelocityContext context = new VelocityContext();
        context.put("table", table);
        context.put("tableName", table.getTableName());
        context.put("tableComment", TextUtils.cleanComment(table.getTableComment(), functionName));
        context.put("className", table.getClassName());
        context.put("moduleName", moduleName);
        context.put("businessName", businessName);
        context.put("functionName", functionName);
        context.put("functionNameHtml", TextUtils.html(functionName));
        context.put("functionNameJava", TextUtils.javaString(functionName));
        context.put("functionNameJs", TextUtils.jsLabel(table.getFunctionName(), table.getClassName()));
        context.put("functionNameSql", TextUtils.sqlString(functionName));
        context.put("author", TextUtils.cleanComment(table.getFunctionAuthor(), "lingxiao"));
        context.put("datetime", LocalDateTime.now().format(TIME_FORMAT));
        context.put("packageName", "com.lingxiao.modules." + moduleName);
        context.put("permissionPrefix", "platform:" + moduleName + ":" + businessName);
        context.put("apiPath", "/" + moduleName + "/" + businessName);
        context.put("routePath", table.getGenPath());
        context.put("viewPath", moduleName + "/" + businessName + "/index");

        context.put("pk", pk);
        context.put("pkField", pk.getJavaField());
        context.put("pkFieldCap", pk.getJavaFieldCap());
        context.put("pkType", pk.getJavaType());
        context.put("columns", columns);

        List<GenTableColumn> listColumns = columns.stream()
                .filter(column -> GenConstants.YES.equals(column.getIsList()))
                .collect(Collectors.toList());
        List<GenTableColumn> queryColumns = columns.stream()
                .filter(column -> GenConstants.YES.equals(column.getIsQuery()))
                .collect(Collectors.toList());
        List<GenTableColumn> formColumns = columns.stream()
                .filter(column -> GenConstants.YES.equals(column.getIsInsert())
                        || GenConstants.YES.equals(column.getIsEdit()))
                .collect(Collectors.toList());
        context.put("listColumns", listColumns);
        context.put("queryColumns", queryColumns);
        context.put("formColumns", formColumns);

        context.put("hasDict", columns.stream().anyMatch(this::hasDict));
        context.put("hasStringQuery", queryColumns.stream()
                .anyMatch(column -> "String".equals(column.getJavaType())));
        context.put("hasDateQuery", queryColumns.stream()
                .anyMatch(column -> isDateType(column.getJavaType())));
        context.put("javaImports", buildJavaImports(columns));

        return new Generation(table, context);
    }

    /**
     * 生成前再校验一次命名。
     *
     * <p>导入时校验过，但配置可以被接口直接修改，DB 也可能被人工改。
     * 生成是所有模板变量的最后一道出口，这里不校验就等于把风险交给模板。</p>
     */
    private void validate(GenTable table, List<GenTableColumn> columns) {
        if (table.getClassName() == null || !CLASS_NAME.matcher(table.getClassName()).matches()) {
            throw new ServiceException("实体类名不合法：" + table.getClassName());
        }
        if (table.getModuleName() == null || !MODULE_NAME.matcher(table.getModuleName()).matches()) {
            throw new ServiceException("模块名不合法：" + table.getModuleName());
        }
        if (table.getBusinessName() == null || !BUSINESS_NAME.matcher(table.getBusinessName()).matches()) {
            throw new ServiceException("业务名不合法：" + table.getBusinessName());
        }
        Set<String> lowerFields = new HashSet<>();
        for (GenTableColumn column : columns) {
            String field = column.getJavaField();
            if (field == null || !JAVA_FIELD.matcher(field).matches()) {
                throw new ServiceException("字段名不合法：" + column.getColumnName() + " -> " + field);
            }
            if (!lowerFields.add(field.toLowerCase(Locale.ROOT))) {
                throw new ServiceException("Java 字段名重复：" + field);
            }
            if (!GenConstants.JAVA_TYPES.contains(column.getJavaType())) {
                throw new ServiceException("字段类型不支持：" + column.getColumnName() + " -> " + column.getJavaType());
            }
            if (GenConstants.YES.equals(column.getIsQuery())
                    && !GenConstants.QUERY_TYPES.contains(column.getQueryType())) {
                throw new ServiceException("字段查询方式不支持：" + column.getColumnName() + " -> " + column.getQueryType());
            }
            if (!GenConstants.HTML_TYPES.contains(column.getHtmlType())) {
                throw new ServiceException("字段表单控件不支持：" + column.getColumnName() + " -> " + column.getHtmlType());
            }
        }
    }

    /**
     * 实体需要 import 的 JDK 类型。
     *
     * <p>用 LinkedHashSet 去重并保持固定顺序，生成结果稳定，方便 diff。</p>
     */
    private List<String> buildJavaImports(List<GenTableColumn> columns) {
        Set<String> types = columns.stream()
                .map(GenTableColumn::getJavaType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> imports = new ArrayList<>();
        if (types.contains("BigDecimal")) {
            imports.add("java.math.BigDecimal");
        }
        if (types.contains("LocalDate")) {
            imports.add("java.time.LocalDate");
        }
        if (types.contains("LocalDateTime")) {
            imports.add("java.time.LocalDateTime");
        }
        if (types.contains("LocalTime")) {
            imports.add("java.time.LocalTime");
        }
        return imports;
    }

    private boolean hasDict(GenTableColumn column) {
        return StringUtils.hasText(column.getDictType());
    }

    private boolean isDateType(String javaType) {
        return "LocalDate".equals(javaType)
                || "LocalDateTime".equals(javaType)
                || "LocalTime".equals(javaType);
    }

    /* ==================== 模板清单 ==================== */

    private List<TemplateSpec> specs(Generation generation) {
        String className = generation.table.getClassName();
        String moduleName = generation.table.getModuleName();
        String businessName = generation.table.getBusinessName();
        String packagePath = "com/lingxiao/modules/" + moduleName;

        List<TemplateSpec> specs = new ArrayList<>();
        specs.add(new TemplateSpec(
                TEMPLATE_DIR + "entity.java.vm",
                "src/main/java/" + packagePath + "/entity/" + className + ".java",
                className + ".java", "java"));
        specs.add(new TemplateSpec(
                TEMPLATE_DIR + "mapper.java.vm",
                "src/main/java/" + packagePath + "/mapper/" + className + "Mapper.java",
                className + "Mapper.java", "java"));
        specs.add(new TemplateSpec(
                TEMPLATE_DIR + "service.java.vm",
                "src/main/java/" + packagePath + "/service/" + className + "Service.java",
                className + "Service.java", "java"));
        specs.add(new TemplateSpec(
                TEMPLATE_DIR + "controller.java.vm",
                "src/main/java/" + packagePath + "/controller/" + className + "Controller.java",
                className + "Controller.java", "java"));
        specs.add(new TemplateSpec(
                TEMPLATE_DIR + "api.js.vm",
                "web/src/api/" + businessName + ".js",
                businessName + ".js", "javascript"));
        specs.add(new TemplateSpec(
                TEMPLATE_DIR + "index.vue.vm",
                "web/src/views/" + moduleName + "/" + businessName + "/index.vue",
                "index.vue", "vue"));
        specs.add(new TemplateSpec(
                TEMPLATE_DIR + "menu.sql.vm",
                "sql/" + generation.table.getTableName() + "_menu.sql",
                generation.table.getTableName() + "_menu.sql", "sql"));
        return specs;
    }

    /* ==================== 渲染 ==================== */

    private String render(String template, VelocityContext context) {
        try {
            StringWriter writer = new StringWriter();
            engine.mergeTemplate(template, "UTF-8", context, writer);
            return writer.toString();
        } catch (Exception e) {
            log.error("模板渲染失败：{}", template, e);
            throw new ServiceException("模板渲染失败：" + template);
        }
    }

    /* ==================== 内部结构 ==================== */

    private static final class Generation {
        private final GenTable table;
        private final VelocityContext context;

        private Generation(GenTable table, VelocityContext context) {
            this.table = table;
            this.context = context;
        }
    }

    private static final class TemplateSpec {
        private final String template;
        private final String path;
        private final String displayName;
        private final String language;

        private TemplateSpec(String template, String path, String displayName, String language) {
            this.template = template;
            this.path = path;
            this.displayName = displayName;
            this.language = language;
        }
    }
}
