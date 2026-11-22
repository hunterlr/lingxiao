package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/** 用一次性 ticket 换会员 Token；ticket 不承载 Token，也不写入 URL。 */
@Data
public class MemberOAuthExchangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.member.oauth.ticket.required}")
    @Size(max = 64, message = "{valid.member.oauth.ticket.max}")
    private String ticket;
}
