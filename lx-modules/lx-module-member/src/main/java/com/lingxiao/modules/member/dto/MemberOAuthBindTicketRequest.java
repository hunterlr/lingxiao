package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 完成第三方账号绑定。
 *
 * <p>用户在第三方登录时若命中了已有会员，后端不下发会话，只返回一次性
 * {@code bindTicket}；用户用原账号登录后把它提交到本接口，才真正建立绑定。</p>
 */
@Data
public class MemberOAuthBindTicketRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.member.oauth.ticket.required}")
    @Size(max = 64, message = "{valid.member.oauth.ticket.max}")
    private String ticket;
}
