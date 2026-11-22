package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 后台启用 / 停用会员。
 *
 * <p>单独一个 DTO 而不是复用编辑请求：启停不需要昵称、等级等字段，
 * 只传一个状态值，避免前端为了改状态把整条资料回传。</p>
 */
@Data
public class MemberStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0 正常 / 1 停用 */
    @NotBlank(message = "{valid.member.status.required}")
    @Pattern(regexp = "^[01]$", message = "{valid.member.status}")
    private String status;
}
