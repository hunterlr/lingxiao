package com.lingxiao.common.core.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应。
 *
 * <p>字段名与前端表格组件对齐：{@code total / pageNum / pageSize / records}。
 * 不要直接把 MyBatis-Plus 的 {@code Page} 返回给前端，避免把内部字段暴露出去。</p>
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.total = 0;
        result.pageNum = pageNum;
        result.pageSize = pageSize;
        result.records = Collections.emptyList();
        return result;
    }
}
