package com.lingxiao.common.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页响应测试。
 *
 * <p>字段名是前端表格契约（total / pageNum / pageSize / records），
 * 不要为了“好看”改成 list / rows，否则 18 个页面一起改。</p>
 */
@DisplayName("PageResult 分页响应")
class PageResultTest {

    @Test
    @DisplayName("空分页：total=0、records 为空列表而不是 null")
    void emptyPage() {
        PageResult<String> result = PageResult.empty(2, 20);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getPageNum()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getRecords()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("普通分页：records 原样保留顺序")
    void recordsKeepOrder() {
        PageResult<String> result = new PageResult<>();
        result.setTotal(3);
        result.setPageNum(1);
        result.setPageSize(10);
        result.setRecords(Arrays.asList("a", "b", "c"));

        assertThat(result.getRecords()).containsExactly("a", "b", "c");
        assertThat(result.getTotal()).isEqualTo(3);
    }
}
