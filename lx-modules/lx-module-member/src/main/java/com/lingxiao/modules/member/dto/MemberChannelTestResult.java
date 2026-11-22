package com.lingxiao.modules.member.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 测试发送结果。
 *
 * <p>开发模式会回传验证码，便于运营确认日志通道确实写入了内容；
 * 生产模式不回传验证码，只返回通道与目标。</p>
 */
@Data
public class MemberChannelTestResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelType;

    private String mode;

    private String provider;

    private String target;

    /** 仅开发模式返回 */
    private String code;

    private String message;
}
