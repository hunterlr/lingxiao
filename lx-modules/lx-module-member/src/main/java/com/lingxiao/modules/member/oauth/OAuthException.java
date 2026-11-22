package com.lingxiao.modules.member.oauth;

/**
 * 第三方登录流程中的可预期异常。
 *
 * <p>消息只用于服务端日志，回调接口会把异常转成稳定的错误码再跳回门户，
 * 不把上游返回的错误原文暴露给浏览器。</p>
 */
public class OAuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OAuthException(String message) {
        super(message);
    }

    public OAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
