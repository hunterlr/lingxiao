package com.lingxiao.modules.i18n.support;

import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 编解码（RFC 4180 最小实现）。
 *
 * <p>为什么不用第三方库：只需要“引号、逗号、换行、UTF-8 BOM”四种情况，
 * 多引一个依赖不如 100 行代码可控；Excel 导出的 CSV 恰好都覆盖到了。</p>
 *
 * <p>约定列：{@code namespace,key,zh-CN,en-US,remark}。导入时按表头找列，
 * 不硬编码列序号，后台将来加语言不用改解析器。</p>
 */
public final class I18nCsvCodec {

    private I18nCsvCodec() {
    }

    /**
     * 解析 CSV 为二维表。
     *
     * <p>支持：字段带引号、引号内逗号 / 换行、双引号转义（{@code ""}）、
     * 行尾 CRLF / LF、文件头 BOM。</p>
     */
    public static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null) {
            return rows;
        }
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (inQuotes) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "error.1001");
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            addRow(rows, row);
        }
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean empty = row.size() == 1 && row.get(0).isEmpty();
        if (!empty) {
            rows.add(row);
        }
    }

    /** 导出单元格：含逗号 / 引号 / 换行时加引号并转义。 */
    public static String cell(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** 拼接一行。 */
    public static String line(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells.get(i)));
        }
        return sb.toString();
    }
}
