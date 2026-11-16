package com.lingxiao.admin.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应。
 *
 * <p>Token 通过响应体返回，前端存内存或 localStorage；不要塞进 URL。</p>
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /** 请求头名称，前端后续请求带上它 */
    private String tokenName;

    /** Token 值 */
    private String tokenValue;

    /** 有效期（秒） */
    private long expiresIn;
}
