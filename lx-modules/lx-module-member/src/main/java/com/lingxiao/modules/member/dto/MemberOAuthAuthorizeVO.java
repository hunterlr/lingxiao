package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/** 授权跳转地址；由门户拿到后自行跳转，避免代理层处理 302 时丢失参数。 */
@Data
public class MemberOAuthAuthorizeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;

    private String authorizeUrl;
}
