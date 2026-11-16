package com.lingxiao.modules.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 重置密码请求。
 *
 * <p>密码走请求体，**不要**用查询参数传（会被访问日志、浏览器历史记录留存）。</p>
 */
@Data
public class PasswordResetRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在 6~64 之间")
    private String newPassword;
}
