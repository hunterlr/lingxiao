package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员自助修改资料。
 *
 * <p>只允许改昵称 / 头像 / 性别；手机号、邮箱涉及换绑验证，留给后续
 * “绑定 / 换绑”流程，不在资料表单里直接改。</p>
 */
@Data
public class MemberProfileUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.nickname.required}")
    @Size(max = 64, message = "{valid.nickname.max}")
    private String nickname;

    @Size(max = 255, message = "{valid.avatar.max}")
    private String avatar;

    /** 0 男 / 1 女 / 2 未知；Service 校验取值 */
    private String sex;
}
