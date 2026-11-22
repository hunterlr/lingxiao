package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 后台第三方登录服务商配置视图。
 *
 * <p>密钥只返回“是否已配置”（{@code hasSecret}），不回传明文；
 * 修改时 Secret 留空表示沿用原值，避免运营每次保存都要重新粘贴。</p>
 */
@Data
public class MemberOAuthProviderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;

    private String displayName;

    /** cn：国内；global：国外 */
    private String group;

    /** 是否支持浏览器跳转授权；小程序为 false，门户不展示按钮 */
    private boolean browser = true;

    /** 0 启用 / 1 停用 */
    private String status;

    private String clientId;

    /** 是否已保存 ClientSecret */
    private boolean hasSecret;

    /** 是否已同时配置 ClientId + ClientSecret */
    private boolean configured;

    private String redirectUri;

    private String scope;

    private String authorizeUrl;

    private String tokenUrl;

    private String userInfoUrl;

    private String extraJson;

    private Integer sort;

    private String remark;

    /** 内置回调地址，页面直接展示给运营复制到服务商后台 */
    private String suggestedRedirectUri;
}
