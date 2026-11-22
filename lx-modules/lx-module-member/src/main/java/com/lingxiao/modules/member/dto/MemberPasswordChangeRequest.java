package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 会员自助修改密码。
 *
 * <p>校验顺序：原密码 → 新旧不同 → 复杂度策略；成功后注销该会员全部会话。
 * 明文密码不落库、不进日志。</p>
 */
@Data
public class MemberPasswordChangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{valid.password.old.required}")
    @Size(max = 64, message = "{valid.password.old.max}")
    private String oldPassword;

    @NotBlank(message = "{valid.password.new.required}")
    @Size(min = 6, max = 64, message = "{valid.password.new.size}")
    private String newPassword;
}
