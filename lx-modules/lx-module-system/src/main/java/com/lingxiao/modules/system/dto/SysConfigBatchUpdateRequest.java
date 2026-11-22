package com.lingxiao.modules.system.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.List;

/**
 * 批量修改参数值请求。
 *
 * <p>用于会员设置这类“一个页面一组参数、点一次保存”的场景。</p>
 */
@Data
public class SysConfigBatchUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "{valid.config.items.required}")
    @Valid
    private List<SysConfigValueRequest> items;
}
