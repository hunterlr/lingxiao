package com.lingxiao.modules.generator.service;

import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.modules.generator.dto.DbTableVO;
import com.lingxiao.modules.generator.dto.TableMeta;
import com.lingxiao.modules.generator.entity.GenTableColumn;
import com.lingxiao.modules.generator.support.GenConstants;
import com.lingxiao.modules.generator.support.JavaTypeResolver;
import com.lingxiao.modules.generator.support.NamingUtils;
import com.lingxiao.modules.generator.support.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据库表结构读取。
 *
 * <p><b>为什么用 {@link DatabaseMetaData} 而不是 information_schema：</b>
 * 达梦、人大金仓、PostgreSQL 的元数据表名和字段都不一样，写四套查询等于写四个生成器。
 * JDBC 规范把这层差异抹平了，代价是个别数据库的注释 / 自增信息可能缺失，
 * 所以这里的原则是“读不到就给默认值，绝不让导入失败”。</p>
 *
 * <p>只读当前连接对应的库：不接收 catalog / schema 参数，避免生成器被拿来
 * 读取别的库。表名、列名只用于查询元数据，不会拼进业务 SQL。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lx.module.modules.generator.enabled", havingValue = "true")
public class DatabaseMetaService {

    /** 工具表 / 框架表不参与业务代码生成。 */
    private static final Set<String> INTERNAL_TABLES = new HashSet<>(Arrays.asList(
            "gen_table", "gen_table_column", "flyway_schema_history", "qrtz_locks"));

    private static final Pattern IDENTIFIER = Pattern.compile(GenConstants.IDENTIFIER_REGEX);

    private final DataSource dataSource;

    /**
     * 列出当前库中可导入的表。
     *
     * @return 按表名排序的清单，注释可能为空字符串
     */
    public List<DbTableVO> listTables() {
        List<DbTableVO> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String[] scope = namespace(connection);
            try (ResultSet rs = meta.getTables(scope[0], scope[1], "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name == null || INTERNAL_TABLES.contains(name.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    // 视图在部分数据库也会以 TABLE 返回，用类型再兜一层
                    String type = rs.getString("TABLE_TYPE");
                    if (type != null && !"TABLE".equalsIgnoreCase(type)) {
                        continue;
                    }
                    String comment = rs.getString("REMARKS");
                    tables.add(new DbTableVO(name, comment == null ? "" : comment, false));
                }
            }
        } catch (SQLException e) {
            log.error("读取数据库表清单失败", e);
            throw new ServiceException("读取数据库表清单失败，请检查数据源连接");
        }
        tables.sort(Comparator.comparing(DbTableVO::getTableName, String.CASE_INSENSITIVE_ORDER));
        return tables;
    }

    /**
     * 读取单张表的列、主键、注释。
     *
     * @throws ServiceException 表不存在、没有主键或多个主键
     */
    public TableMeta readTable(String tableName) {
        if (tableName == null || !IDENTIFIER.matcher(tableName.trim()).matches()) {
            throw new ServiceException("表名不合法：" + tableName);
        }
        String requested = tableName.trim();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String[] scope = namespace(connection);

            String actualName = findTableName(meta, scope, requested);
            if (actualName == null) {
                throw new ServiceException("表不存在或不可见：" + requested);
            }

            String tableComment = readTableComment(meta, scope, actualName);
            Set<String> primaryKeys = readPrimaryKeys(meta, scope, actualName);
            if (primaryKeys.isEmpty()) {
                throw new ServiceException("表 " + actualName + " 没有主键，v1.0 生成器不支持无主键表");
            }
            if (primaryKeys.size() > 1) {
                throw new ServiceException("表 " + actualName + " 是复合主键，v1.0 生成器只支持单主键");
            }

            List<GenTableColumn> columns = readColumns(meta, scope, actualName, primaryKeys);
            if (columns.isEmpty()) {
                throw new ServiceException("表 " + actualName + " 没有可生成的列");
            }
            return new TableMeta(actualName, tableComment, columns, primaryKeys.size());
        } catch (SQLException e) {
            log.error("读取表结构失败：{}", requested, e);
            throw new ServiceException("读取表结构失败，请稍后重试或检查数据库权限");
        }
    }

    private String findTableName(DatabaseMetaData meta, String[] scope, String requested) throws SQLException {
        // tableNamePattern 里的 "_" 是通配符，不能直接信任返回结果，必须逐行比对
        try (ResultSet rs = meta.getTables(scope[0], scope[1], "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && name.equalsIgnoreCase(requested)) {
                    return name;
                }
            }
        }
        return null;
    }

    private String readTableComment(DatabaseMetaData meta, String[] scope, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(scope[0], scope[1], "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    String comment = rs.getString("REMARKS");
                    return TextUtils.cleanComment(comment, "");
                }
            }
        }
        return "";
    }

    private Set<String> readPrimaryKeys(DatabaseMetaData meta, String[] scope, String tableName) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(scope[0], scope[1], tableName)) {
            while (rs.next()) {
                // tableNamePattern 中的 "_" 是通配符，必须按 TABLE_NAME 再过滤一次
                if (!tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    continue;
                }
                String name = rs.getString("COLUMN_NAME");
                if (name != null) {
                    keys.add(name);
                }
            }
        }
        return keys;
    }

    private List<GenTableColumn> readColumns(DatabaseMetaData meta, String[] scope, String tableName,
                                            Set<String> primaryKeys) throws SQLException {
        List<RawColumn> rawColumns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(scope[0], scope[1], tableName, "%")) {
            while (rs.next()) {
                // 同样要过滤：列名模式里的 "_" 会匹配多张表
                if (!tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    continue;
                }
                RawColumn raw = new RawColumn();
                raw.columnName = rs.getString("COLUMN_NAME");
                raw.typeName = rs.getString("TYPE_NAME");
                raw.remarks = rs.getString("REMARKS");
                raw.ordinal = rs.getInt("ORDINAL_POSITION");
                raw.nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                raw.autoIncrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
                rawColumns.add(raw);
            }
        }
        rawColumns.sort(Comparator.comparingInt(c -> c.ordinal));

        List<GenTableColumn> columns = new ArrayList<>();
        int sort = 1;
        int queryCount = 0;
        for (RawColumn raw : rawColumns) {
            if (raw.columnName == null || !IDENTIFIER.matcher(raw.columnName).matches()) {
                // 名字不合法的列跳过并留日志，不因为一个奇怪列让整表导入失败
                log.warn("跳过不合法列：table={}, column={}", tableName, raw.columnName);
                continue;
            }
            GenTableColumn column = new GenTableColumn();
            column.setColumnName(raw.columnName);
            column.setColumnComment(TextUtils.cleanComment(raw.remarks, raw.columnName));
            column.setColumnType(raw.typeName == null ? "" : raw.typeName);
            column.setJavaType(JavaTypeResolver.resolve(raw.typeName));
            column.setJavaField(NamingUtils.safeJavaField(NamingUtils.toCamelCase(raw.columnName)));
            boolean pk = primaryKeys.stream().anyMatch(k -> k.equalsIgnoreCase(raw.columnName));
            column.setIsPk(pk ? GenConstants.YES : GenConstants.NO);
            column.setIsIncrement(raw.autoIncrement ? GenConstants.YES : GenConstants.NO);
            column.setIsRequired(!pk && !raw.nullable ? GenConstants.YES : GenConstants.NO);
            // 自增主键不出现在新增 / 编辑表单；其它列默认都出现
            column.setIsInsert(raw.autoIncrement ? GenConstants.NO : GenConstants.YES);
            column.setIsEdit(raw.autoIncrement ? GenConstants.NO : GenConstants.YES);
            column.setHtmlType(htmlDefault(column));
            column.setIsList(isListDefault(column) ? GenConstants.YES : GenConstants.NO);
            column.setDictType(null);
            column.setSort(sort++);

            // 查询条件默认值：主键 EQ，再给最多 2 个字符串列开 LIKE。
            // 全开会让生成的页面顶上一整排搜索框，反而不好用；用户可以逐列改。
            if (pk) {
                column.setIsQuery(GenConstants.YES);
                column.setQueryType("EQ");
                queryCount++;
            } else if (queryCount < 3 && "String".equals(column.getJavaType())
                    && isListDefault(column)) {
                column.setIsQuery(GenConstants.YES);
                column.setQueryType("LIKE");
                queryCount++;
            } else {
                column.setIsQuery(GenConstants.NO);
                column.setQueryType("EQ");
            }
            columns.add(column);
        }
        return columns;
    }

    /** 大文本 / 二进制不进列表，避免一页把几 MB 内容拖下来。 */
    private boolean isListDefault(GenTableColumn column) {
        String javaType = column.getJavaType();
        return !"byte[]".equals(javaType) && !"textarea".equals(column.getHtmlType());
    }

    private String htmlDefault(GenTableColumn column) {
        String javaType = column.getJavaType();
        if ("LocalDate".equals(javaType) || "LocalDateTime".equals(javaType) || "LocalTime".equals(javaType)) {
            return "datetime";
        }
        String dbType = column.getColumnType() == null ? "" : column.getColumnType().toLowerCase(Locale.ROOT);
        if (dbType.contains("text") || "byte[]".equals(javaType)) {
            return "textarea";
        }
        return "input";
    }

    /**
     * 当前连接的 catalog / schema。
     *
     * <p>MySQL 只有 catalog（库名）；PostgreSQL / 达梦 / 金仓通常两者都有。
     * {@code getSchema()} 在个别驱动上会抛 {@code UnsupportedOperationException}，
     * 这里直接吞掉，让 metadata 方法按“不限定 schema”处理。</p>
     */
    private String[] namespace(Connection connection) throws SQLException {
        String catalog = connection.getCatalog();
        String schema = null;
        try {
            schema = connection.getSchema();
        } catch (Exception ignored) {
            // 驱动不支持 getSchema，保持 null
        }
        return new String[]{catalog, schema};
    }

    /** 列元数据的临时载体，避免在读取循环里反复创建实体再排序。 */
    private static final class RawColumn {
        private String columnName;
        private String typeName;
        private String remarks;
        private int ordinal;
        private boolean nullable;
        private boolean autoIncrement;
    }
}
