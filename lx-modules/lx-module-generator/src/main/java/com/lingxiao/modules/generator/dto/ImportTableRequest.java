package com.lingxiao.modules.generator.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 导入表请求。
 *
 * <p>只传表名，不传数据库 / schema：v1.0 只操作当前数据源连接的库，
 * 防止通过参数把生成器指向别的库。</p>
 */
@Data
public class ImportTableRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "请至少选择一张表")
    @Size(max = 50, message = "一次最多导入 50 张表")
    private List<String> tables;
}
