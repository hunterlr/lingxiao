package com.lingxiao.modules.i18n.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 回滚语言包到指定历史版本。
 */
@Data
public class PackRollbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "{i18n.pack.notfound}")
    private Long packId;
}
