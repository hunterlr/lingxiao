package com.lingxiao.modules.generator.support;

/**
 * 数据库类型 → Java 类型。
 *
 * <p>输入是 JDBC {@code DatabaseMetaData.getColumns} 返回的 {@code TYPE_NAME}，
 * 不同数据库写法差异很大（{@code INT UNSIGNED}、{@code NUMBER}、{@code NVARCHAR2}），
 * 所以先做归一化再匹配，匹配不到统一回退 {@code String}：
 * 宁可生成一个字符串字段让人手动改，也不要生成编译不过的类型。</p>
 */
public final class JavaTypeResolver {

    private JavaTypeResolver() {
    }

    public static String resolve(String dbType) {
        String type = normalize(dbType);
        switch (type) {
            case "bigint":
            case "int8":
            case "serial8":
            case "bigserial":
                return "Long";
            case "int":
            case "integer":
            case "int4":
            case "mediumint":
            case "smallint":
            case "int2":
            case "tinyint":
            case "serial":
            case "smallserial":
            case "year":
                return "Integer";
            case "bit":
            case "bool":
            case "boolean":
                return "Boolean";
            case "decimal":
            case "numeric":
            case "number":
            case "money":
                return "BigDecimal";
            case "double":
            case "double precision":
            case "float8":
                return "Double";
            case "float":
            case "real":
            case "float4":
                return "Float";
            case "date":
                return "LocalDate";
            case "datetime":
            case "timestamp":
            case "timestamptz":
            case "timestamp with time zone":
            case "datetime2":
            case "smalldatetime":
                return "LocalDateTime";
            case "time":
            case "timetz":
                return "LocalTime";
            case "binary":
            case "varbinary":
            case "blob":
            case "tinyblob":
            case "mediumblob":
            case "longblob":
            case "bytea":
            case "image":
                return "byte[]";
            default:
                return "String";
        }
    }

    /**
     * 归一化：小写、去掉长度、去掉 unsigned / zerofill 等 MySQL 修饰。
     *
     * <p>例：{@code INT(11) UNSIGNED} → {@code int}；
     * {@code TIMESTAMP(3) WITH TIME ZONE} → {@code timestamp with time zone}。</p>
     */
    static String normalize(String dbType) {
        if (dbType == null) {
            return "";
        }
        String type = dbType.trim().toLowerCase();
        int paren = type.indexOf('(');
        if (paren >= 0) {
            type = type.substring(0, paren).trim();
        }
        type = type.replace(" unsigned", "").replace(" zerofill", "").trim();
        // MySQL 的 "double precision" / PG 的 "timestamp(6) with time zone" 保留单词形式
        return type.replaceAll("\\s+", " ");
    }
}
