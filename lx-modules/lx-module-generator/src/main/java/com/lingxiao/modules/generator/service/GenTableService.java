package com.lingxiao.modules.generator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.modules.generator.dto.DbTableVO;
import com.lingxiao.modules.generator.dto.ImportResultVO;
import com.lingxiao.modules.generator.dto.ImportTableRequest;
import com.lingxiao.modules.generator.dto.TableMeta;
import com.lingxiao.modules.generator.entity.GenTable;
import com.lingxiao.modules.generator.entity.GenTableColumn;
import com.lingxiao.modules.generator.mapper.GenTableColumnMapper;
import com.lingxiao.modules.generator.mapper.GenTableMapper;
import com.lingxiao.modules.generator.support.GenConstants;
import com.lingxiao.modules.generator.support.NamingUtils;
import com.lingxiao.modules.generator.support.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 生成配置管理：导入表、改配置、删配置。
 *
 * <p>三个职责边界：</p>
 * <ol>
 *   <li><b>元数据只读</b>：表结构永远以数据库为准，本类不缓存、不猜测；</li>
 *   <li><b>命名可改</b>：类名 / 模块名 / 业务名允许人工调整，但必须过白名单正则，
 *       因为它们会进入包名、URL、权限码和模板变量；</li>
 *   <li><b>数据库事实不可改</b>：列名、是否主键、是否自增由导入时确定，
 *       更新接口只接收展示与生成策略字段。</li>
 * </ol>
 *
 * <p>删除是物理删除：先删子表再删主表，与 sys_user_role 等关联表一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.generator.enabled", havingValue = "true")
public class GenTableService {

    private static final Pattern IDENTIFIER = Pattern.compile(GenConstants.IDENTIFIER_REGEX);
    private static final Pattern CLASS_NAME = Pattern.compile(GenConstants.CLASS_NAME_REGEX);
    private static final Pattern MODULE_NAME = Pattern.compile(GenConstants.MODULE_NAME_REGEX);
    private static final Pattern BUSINESS_NAME = Pattern.compile(GenConstants.BUSINESS_NAME_REGEX);
    private static final Pattern JAVA_FIELD = Pattern.compile(GenConstants.JAVA_FIELD_REGEX);
    private static final Pattern GEN_PATH = Pattern.compile(GenConstants.GEN_PATH_REGEX);
    private static final Pattern DICT_TYPE = Pattern.compile("^[A-Za-z0-9_]{1,100}$");

    private final GenTableMapper tableMapper;
    private final GenTableColumnMapper columnMapper;
    private final DatabaseMetaService databaseMetaService;

    /**
     * 系统模块不一定被装配（裁剪部署），所以用 ObjectProvider 而不是强制注入。
     * 没有实现时回退当前登录用户，再回退 default。
     */
    private final ObjectProvider<ConfigProvider> configProvider;

    /* ==================== 查询 ==================== */

    public PageResult<GenTable> page(long pageNum, long pageSize, String tableName, String tableComment) {
        long safePageNum = Math.max(pageNum, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), 100);

        LambdaQueryWrapper<GenTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(tableName), GenTable::getTableName,
                tableName == null ? null : tableName.trim());
        wrapper.like(StringUtils.hasText(tableComment), GenTable::getTableComment,
                tableComment == null ? null : tableComment.trim());
        wrapper.orderByDesc(GenTable::getCreateTime);

        Page<GenTable> page = tableMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        PageResult<GenTable> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setRecords(page.getRecords());
        return result;
    }

    /** 详情：表配置 + 字段配置，字段按 sort 升序。 */
    public GenTable detail(Long tableId) {
        GenTable table = requireTable(tableId);
        table.setColumns(listColumns(tableId));
        return table;
    }

    public List<GenTableColumn> listColumns(Long tableId) {
        return columnMapper.selectList(new LambdaQueryWrapper<GenTableColumn>()
                .eq(GenTableColumn::getTableId, tableId)
                .orderByAsc(GenTableColumn::getSort)
                .orderByAsc(GenTableColumn::getColumnId));
    }

    /**
     * 可导入表清单。
     *
     * <p>{@code keyword} 同时匹配表名与注释；注释为空时只看表名。
     * 已导入的表仍然返回，只是 {@code imported=true}，前端置灰。</p>
     */
    public List<DbTableVO> listCandidateTables(String keyword) {
        Set<String> imported = tableMapper.selectList(new LambdaQueryWrapper<GenTable>()
                        .select(GenTable::getTableName))
                .stream()
                .map(GenTable::getTableName)
                .filter(StringUtils::hasText)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<DbTableVO> result = new ArrayList<>();
        for (DbTableVO table : databaseMetaService.listTables()) {
            table.setImported(imported.contains(table.getTableName().toLowerCase(Locale.ROOT)));
            if (needle.isEmpty()
                    || table.getTableName().toLowerCase(Locale.ROOT).contains(needle)
                    || (table.getTableComment() != null
                    && table.getTableComment().toLowerCase(Locale.ROOT).contains(needle))) {
                result.add(table);
            }
        }
        return result;
    }

    /* ==================== 导入 ==================== */

    /**
     * 导入表。
     *
     * <p>逐表处理、逐表反馈：某张表没有主键不应该让另外九张表也导不进来。
     * 未捕获的数据库异常仍会整批回滚，保证不会留下“表配置有了、字段只写了一半”的脏数据。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importTables(ImportTableRequest request) {
        ImportResultVO result = new ImportResultVO();
        Set<String> seen = new HashSet<>();

        for (String raw : request.getTables()) {
            String tableName = raw == null ? "" : raw.trim();
            if (tableName.isEmpty()) {
                continue;
            }
            if (!seen.add(tableName.toLowerCase(Locale.ROOT))) {
                result.addSkipped(tableName, "重复选择");
                continue;
            }
            if (!IDENTIFIER.matcher(tableName).matches()) {
                result.addSkipped(tableName, "表名不合法");
                continue;
            }
            if (existsTable(tableName)) {
                result.addSkipped(tableName, "已导入，请先删除旧配置");
                continue;
            }
            try {
                TableMeta meta = databaseMetaService.readTable(tableName);
                GenTable table = buildTable(meta);
                tableMapper.insert(table);
                for (GenTableColumn column : meta.getColumns()) {
                    column.setTableId(table.getTableId());
                    columnMapper.insert(column);
                }
                result.setImported(result.getImported() + 1);
            } catch (ServiceException e) {
                // 表结构层面的问题（不存在 / 无主键 / 复合主键）按“跳过”反馈
                result.addSkipped(tableName, e.getMessage());
            }
        }
        return result;
    }

    private boolean existsTable(String tableName) {
        return tableMapper.selectCount(new LambdaQueryWrapper<GenTable>()
                .eq(GenTable::getTableName, tableName)) > 0;
    }

    private GenTable buildTable(TableMeta meta) {
        String tableName = meta.getTableName();
        GenTableColumn pk = meta.getColumns().stream()
                .filter(column -> GenConstants.YES.equals(column.getIsPk()))
                .findFirst()
                .orElse(null);
        if (pk == null) {
            throw new ServiceException("表 " + tableName + " 没有识别到主键");
        }
        if (!GenConstants.PK_JAVA_TYPES.contains(pk.getJavaType())) {
            throw new ServiceException("表 " + tableName + " 的主键类型 " + pk.getJavaType()
                    + " 不支持，v1.0 只支持 Long / Integer / String 主键");
        }
        String className = NamingUtils.toPascalCase(tableName);
        if (!CLASS_NAME.matcher(className).matches()) {
            throw new ServiceException("表 " + tableName + " 无法推导出合法类名，请导入后手工调整");
        }
        String businessName = NamingUtils.safeJavaField(NamingUtils.toCamelCase(tableName));
        if (!BUSINESS_NAME.matcher(businessName).matches()) {
            throw new ServiceException("表 " + tableName + " 无法推导出合法业务名，请导入后手工调整");
        }
        String moduleName = deriveModuleName(tableName);

        GenTable table = new GenTable();
        table.setTableName(tableName);
        table.setTableComment(cut(meta.getTableComment(), 500));
        table.setClassName(className);
        table.setModuleName(moduleName);
        table.setBusinessName(businessName);
        table.setFunctionName(defaultFunctionName(meta, className));
        table.setFunctionAuthor(resolveAuthor());
        table.setGenType(GenConstants.GEN_TYPE_SINGLE);
        table.setGenPath("/" + moduleName + "/" + businessName);
        table.setTemplateGroup(GenConstants.TEMPLATE_GROUP_DEFAULT);
        table.setStatus("0");
        table.setCreateBy(currentUserId());
        table.setCreateTime(LocalDateTime.now());
        return table;
    }

    /**
     * 模块名默认取表名第一段：{@code biz_order} → {@code biz}。
     * 第一段不合法（纯数字、过长）时回退 {@code business}，用户在页面上可以再改。
     */
    private String deriveModuleName(String tableName) {
        int idx = tableName.indexOf('_');
        String first = (idx > 0 ? tableName.substring(0, idx) : tableName).toLowerCase(Locale.ROOT);
        return MODULE_NAME.matcher(first).matches() ? first : "business";
    }

    private String defaultFunctionName(TableMeta meta, String className) {
        String comment = meta.getTableComment();
        if (StringUtils.hasText(comment)) {
            String cleaned = comment.trim();
            // 很多表注释以“表”结尾，如“订单表”，菜单里读起来别扭
            if (cleaned.length() > 1 && cleaned.endsWith("表")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            return cut(cleaned, 64);
        }
        return cut(className, 64);
    }

    private String resolveAuthor() {
        ConfigProvider provider = configProvider.getIfAvailable();
        if (provider != null) {
            try {
                String author = provider.getValue("sys.gen.author");
                if (StringUtils.hasText(author)) {
                    return cut(author.trim(), 64);
                }
            } catch (Exception e) {
                log.warn("读取 sys.gen.author 失败，回退登录用户：{}", e.getMessage());
            }
        }
        try {
            String nickname = LoginHelper.getNickname();
            if (StringUtils.hasText(nickname)) {
                return cut(nickname, 64);
            }
            String username = LoginHelper.getUsername();
            if (StringUtils.hasText(username)) {
                return cut(username, 64);
            }
        } catch (Exception ignored) {
            // 没有登录上下文时继续回退
        }
        return "lingxiao";
    }

    /* ==================== 更新 ==================== */

    /**
     * 更新表级配置。
     *
     * <p>只接收命名 / 描述 / 路由；{@code genType}、{@code templateGroup}、{@code status}
     * 在 v1.0 没有第二种取值，开放编辑只会让用户配出“看起来支持、实际不生成”的状态。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTable(Long tableId, GenTable request) {
        GenTable table = requireTable(tableId);
        if (request == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "缺少表配置");
        }

        String className = trim(request.getClassName());
        if (!CLASS_NAME.matcher(className).matches()) {
            throw new ServiceException("实体类名必须以大写字母开头，只允许字母和数字，最长 64 位");
        }
        String moduleName = trim(request.getModuleName());
        if (!MODULE_NAME.matcher(moduleName).matches()) {
            throw new ServiceException("模块名必须以小写字母开头，只允许小写字母和数字，最长 32 位");
        }
        String businessName = trim(request.getBusinessName());
        if (!BUSINESS_NAME.matcher(businessName).matches()) {
            throw new ServiceException("业务名必须以小写字母开头，只允许字母和数字，最长 64 位");
        }
        String functionName = trim(request.getFunctionName());
        if (functionName.isEmpty() || functionName.length() > 64) {
            throw new ServiceException("功能名不能为空，且不能超过 64 个字符");
        }
        String functionAuthor = trim(request.getFunctionAuthor());
        if (functionAuthor.length() > 64) {
            throw new ServiceException("作者不能超过 64 个字符");
        }
        String genPath = trim(request.getGenPath());
        if (!GEN_PATH.matcher(genPath).matches() || genPath.contains("..") || genPath.contains("//")) {
            throw new ServiceException("前端路由必须以 / 开头，只允许字母、数字、下划线、中划线和斜杠");
        }
        String tableComment = TextUtils.cleanComment(request.getTableComment(), "");
        if (tableComment.length() > 500) {
            throw new ServiceException("表注释不能超过 500 个字符");
        }
        table.setTableComment(tableComment);
        table.setClassName(className);
        table.setModuleName(moduleName);
        table.setBusinessName(businessName);
        table.setFunctionName(functionName);
        table.setFunctionAuthor(functionAuthor);
        table.setGenPath(genPath);
        table.setUpdateBy(currentUserId());
        table.setUpdateTime(LocalDateTime.now());
        tableMapper.updateById(table);
    }

    /**
     * 批量更新字段配置。
     *
     * <p>前端一次提交整张表的字段（字段数量有限），后端做全量校验后再逐行更新：
     * 只更新请求里出现的字段，避免“只改一行却把其它行重置”的误操作。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateColumns(Long tableId, List<GenTableColumn> request) {
        requireTable(tableId);
        if (request == null || request.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "没有需要保存的字段配置");
        }

        Map<Long, GenTableColumn> existing = new HashMap<>();
        for (GenTableColumn column : listColumns(tableId)) {
            existing.put(column.getColumnId(), column);
        }

        Set<String> usedFields = new HashSet<>();
        for (GenTableColumn item : request) {
            if (item == null || item.getColumnId() == null) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "字段配置缺少 columnId");
            }
            GenTableColumn target = existing.get(item.getColumnId());
            if (target == null) {
                throw new ServiceException("字段不属于该表：" + item.getColumnId());
            }
            applyColumnUpdate(target, item, usedFields);
        }

        for (GenTableColumn item : request) {
            columnMapper.updateById(existing.get(item.getColumnId()));
        }
    }

    private void applyColumnUpdate(GenTableColumn target, GenTableColumn request, Set<String> usedFields) {
        String javaField = trim(request.getJavaField());
        if (!JAVA_FIELD.matcher(javaField).matches()) {
            throw new ServiceException("字段 " + target.getColumnName() + " 的 Java 字段名不合法");
        }
        if (!NamingUtils.safeJavaField(javaField).equals(javaField)) {
            throw new ServiceException("字段 " + javaField + " 是 Java 关键字，请换一个名字");
        }
        if (!usedFields.add(javaField.toLowerCase(Locale.ROOT))) {
            throw new ServiceException("Java 字段名重复：" + javaField);
        }

        String javaType = trim(request.getJavaType());
        if (!GenConstants.JAVA_TYPES.contains(javaType)) {
            throw new ServiceException("字段 " + target.getColumnName() + " 的 Java 类型不支持：" + javaType);
        }
        if (GenConstants.YES.equals(target.getIsPk()) && !GenConstants.PK_JAVA_TYPES.contains(javaType)) {
            throw new ServiceException("主键只能使用 Long / Integer / String 类型：" + target.getColumnName());
        }
        String queryType = trim(request.getQueryType()).toUpperCase(Locale.ROOT);
        if (!GenConstants.QUERY_TYPES.contains(queryType)) {
            throw new ServiceException("字段 " + target.getColumnName() + " 的查询方式不支持：" + queryType);
        }
        if ("LIKE".equals(queryType) && !"String".equals(javaType)) {
            throw new ServiceException("只有字符串字段可以使用 LIKE 查询：" + target.getColumnName());
        }
        String htmlType = trim(request.getHtmlType());
        if (!GenConstants.HTML_TYPES.contains(htmlType)) {
            throw new ServiceException("字段 " + target.getColumnName() + " 的表单控件不支持：" + htmlType);
        }

        String comment = TextUtils.cleanComment(request.getColumnComment(), target.getColumnName());
        if (comment.length() > 500) {
            throw new ServiceException("字段 " + target.getColumnName() + " 的注释不能超过 500 个字符");
        }
        String dictType = trim(request.getDictType());
        if (!dictType.isEmpty() && !DICT_TYPE.matcher(dictType).matches()) {
            throw new ServiceException("字段 " + target.getColumnName() + " 的字典类型只能包含字母、数字、下划线");
        }

        int sort = request.getSort() == null ? target.getSort() : request.getSort();
        if (sort < 0 || sort > 9999) {
            throw new ServiceException("字段 " + target.getColumnName() + " 的排序号必须在 0~9999 之间");
        }

        target.setColumnComment(comment);
        target.setJavaType(javaType);
        target.setJavaField(javaField);
        target.setIsRequired(flag(request.getIsRequired(), target.getIsRequired()));
        target.setIsInsert(flag(request.getIsInsert(), target.getIsInsert()));
        target.setIsEdit(flag(request.getIsEdit(), target.getIsEdit()));
        target.setIsList(flag(request.getIsList(), target.getIsList()));
        target.setIsQuery(flag(request.getIsQuery(), target.getIsQuery()));
        target.setQueryType(queryType);
        target.setHtmlType(htmlType);
        target.setDictType(dictType);
        target.setSort(sort);
    }

    /** 只接受 0 / 1，其它值回退原值，避免前端传 "true" 落库成脏数据。 */
    private String flag(String value, String fallback) {
        String flag = trim(value);
        return GenConstants.YES.equals(flag) || GenConstants.NO.equals(flag) ? flag : fallback;
    }

    /* ==================== 删除 ==================== */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long tableId) {
        requireTable(tableId);
        columnMapper.deleteByTableId(tableId);
        tableMapper.deleteById(tableId);
    }

    /* ==================== 公共方法 ==================== */

    public GenTable requireTable(Long tableId) {
        if (tableId == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "缺少表配置 ID");
        }
        GenTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "生成配置不存在或已删除");
        }
        return table;
    }

    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
