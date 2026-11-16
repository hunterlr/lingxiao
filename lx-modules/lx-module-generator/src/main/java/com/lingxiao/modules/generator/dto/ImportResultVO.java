package com.lingxiao.modules.generator.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入结果。
 *
 * <p>部分失败不抛异常：一次勾了 10 张表，其中 1 张没有主键，
 * 正确的行为是“导入 9 张，把 1 张的原因说清楚”，而不是整批回滚。
 * 每条跳过原因都带表名，前端直接展示。</p>
 */
@Data
public class ImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int imported;

    private List<String> skipped = new ArrayList<>();

    public void addSkipped(String tableName, String reason) {
        this.skipped.add(tableName + "：" + reason);
    }
}
