package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 后台测试发送：短信填手机号，邮件填邮箱。
 */
@Data
public class MemberChannelTestRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.member.channel.targetRequired}")
    @Size(max = 128, message = "{valid.member.channel.target.max}")
    private String target;
}
