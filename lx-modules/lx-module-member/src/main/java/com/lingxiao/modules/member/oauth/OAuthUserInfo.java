package com.lingxiao.modules.member.oauth;

import lombok.Data;

/**
 * 第三方平台返回的归一化用户信息。
 *
 * <p>各平台字段差异很大（id / sub / openid / user_id），由 Provider 适配成这组字段；
 * 会员服务只认这里的 {@code provider + openId}，不再感知上游 JSON 结构。</p>
 */
@Data
public class OAuthUserInfo {

    private String provider;

    /** 平台内唯一标识，绑定表的唯一键之一 */
    private String openId;

    /** 跨应用统一标识（微信 unionid、QQ 暂无） */
    private String unionId;

    private String nickname;

    /** 头像 URL；只接受 http(s)，不下载转存 */
    private String avatar;

    private String email;

    /** 最近一次用户信息 JSON，截断后入库，仅排障用 */
    private String rawData;
}
