package com.lingxiao.admin.api.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 个人中心 - 修改资料。
 *
 * <p>只允许改展示类字段：昵称、邮箱、手机号。用户名、状态、角色、部门
 * 属于管理员维护范围，这里不开放，避免用户自助提权或改登录标识。</p>
 */
@Data
public class ProfileUpdateRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @Size(max = 32, message = "手机号不能超过 32 个字符")
    private String phone;
}
