package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 后台手动发送站内消息。
 *
 * <p>一次最多发给 200 个会员；标题 128 字、正文 1000 字，与表字段长度一致。
 * 正文是纯文本，门户渲染时由 Vue 转义。</p>
 */
@Data
public class MemberMessageSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "{valid.message.member.required}")
    @Size(max = 200, message = "{valid.message.member.max}")
    private List<Long> memberIds;

    @NotBlank(message = "{valid.message.title.required}")
    @Size(max = 128, message = "{valid.message.title.max}")
    private String title;

    @NotBlank(message = "{valid.message.content.required}")
    @Size(max = 1000, message = "{valid.message.content.max}")
    private String content;
}