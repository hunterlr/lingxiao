package com.lingxiao.modules.member.dto;

import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 后台修改第三方登录服务商配置。
 *
 * <p>端点 / scope / 回调留空时使用内置默认值；Secret 留空时沿用原值，
 * 只有显式 {@code clearSecret=true} 才会清空。</p>
 */
@Data
public class MemberOAuthProviderUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 64, message = "{valid.member.oauth.displayName.max}")
    private String displayName;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 255, message = "{valid.member.oauth.clientId.max}")
    private String clientId;

    @Size(max = 4000, message = "{valid.member.oauth.clientSecret.max}")
    private String clientSecret;

    /** 是否清空已保存的 Secret */
    private Boolean clearSecret;

    @Size(max = 500, message = "{valid.member.oauth.redirectUri.max}")
    private String redirectUri;

    @Size(max = 500, message = "{valid.member.oauth.scope.max}")
    private String scope;

    @Size(max = 500, message = "{valid.member.oauth.authorizeUrl.max}")
    private String authorizeUrl;

    @Size(max = 500, message = "{valid.member.oauth.tokenUrl.max}")
    private String tokenUrl;

    @Size(max = 500, message = "{valid.member.oauth.userInfoUrl.max}")
    private String userInfoUrl;

    @Size(max = 2000, message = "{valid.member.oauth.extraJson.max}")
    private String extraJson;

    private Integer sort;

    @Size(max = 500, message = "{valid.member.oauth.remark.max}")
    private String remark;
}
