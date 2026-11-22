package com.lingxiao.admin.api.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 个人中心 - 修改资料。
 *
 * <p>只允许改展示类字段：昵称、头像、邮箱、手机号。用户名、状态、角色、部门
 * 属于管理员维护范围，这里不开放，避免用户自助提权或改登录标识。</p>
 *
 * <p>{@code avatar} 兼容两种值：</p>
 * <ul>
 *   <li>本系统文件中心的 {@code fileId}（推荐，便于统一管理 / 清理）；</li>
 *   <li>{@code http(s)://...} 外链，第三方登录（微信 / QQ / GitHub）回写的头像地址。</li>
 * </ul>
 */
@Data
public class ProfileUpdateRequest {

    @NotBlank(message = "{valid.nickname.required}")
    @Size(max = 64, message = "{valid.nickname.max}")
    private String nickname;

    @Size(max = 255, message = "{valid.avatar.max}")
    private String avatar;

    @Email(message = "{valid.email.format}")
    @Size(max = 128, message = "{valid.email.max}")
    private String email;

    @Size(max = 32, message = "{valid.phone.max}")
    private String phone;
}
