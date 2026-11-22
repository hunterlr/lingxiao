package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 后台修改会员消息通道。
 *
 * <p>密钥字段允许留空：留空表示沿用原值，避免前端因为不回显密钥而把已有配置清掉。</p>
 */
@Data
public class MemberChannelUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** dev 开发日志 / prod 生产通道 */
    @NotBlank(message = "{valid.member.channel.mode.required}")
    @Pattern(regexp = "^(dev|prod)$", message = "{valid.member.channel.mode}")
    private String mode;

    @Size(max = 32, message = "{valid.member.channel.provider.max}")
    private String provider;

    @Size(max = 255, message = "{valid.member.channel.endpoint.max}")
    private String endpoint;

    @Size(max = 128, message = "{valid.member.channel.accessKey.max}")
    private String accessKey;

    @Size(max = 255, message = "{valid.member.channel.accessSecret.max}")
    private String accessSecret;

    @Size(max = 64, message = "{valid.member.channel.signName.max}")
    private String signName;

    @Size(max = 64, message = "{valid.member.channel.templateCode.max}")
    private String templateCode;

    @Size(max = 128, message = "{valid.member.channel.sender.max}")
    private String sender;

    /** 0 启用 / 1 停用 */
    @NotBlank(message = "{valid.member.channel.status.required}")
    @Pattern(regexp = "^[01]$", message = "{valid.member.channel.status}")
    private String status;

    @Size(max = 500, message = "{valid.member.channel.remark.max}")
    private String remark;
}
