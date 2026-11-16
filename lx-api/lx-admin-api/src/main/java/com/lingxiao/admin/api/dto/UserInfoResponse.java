package com.lingxiao.admin.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户信息。
 *
 * <p>前端用它渲染头像、昵称、角色标识与按钮权限。</p>
 */
@Data
public class UserInfoResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String avatar;

    /** 个人中心展示与编辑用；不用于登录 */
    private String email;
    private String phone;

    /** 角色标识列表，例如 ["admin"] */
    private List<String> roles = new ArrayList<>();

    /** 权限码列表，例如 ["platform:system:user:list"]；超管返回 ["*:*:*"] */
    private List<String> permissions = new ArrayList<>();
}
