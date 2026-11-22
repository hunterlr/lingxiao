package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员第三方登录服务商配置（mem_oauth_provider）。
 *
 * <p>一张表承载所有服务商：启停、ClientId / Secret、回调地址、scope 与端点覆写。
 * 内置端点在代码的 Provider 注册表里，这里留空即使用默认值；这样新增服务商
 * 不需要改表，也不需要把几十个端点写死在迁移脚本里。</p>
 *
 * <p>密钥只写不读：后台列表只返回“是否已配置”，修改时留空表示沿用原值。</p>
 */
@Data
@TableName("mem_oauth_provider")
public class MemOAuthProvider {

    /** 租户 ID；当前固定 0 */
    private Long tenantId;

    /** 稳定编码，例如 wechat_open / github */
    @TableId(value = "provider", type = IdType.INPUT)
    private String provider;

    private String displayName;

    /** 0 启用 / 1 停用 */
    private String status;

    private String clientId;

    /** ClientSecret / AppSecret / 私钥；接口不下发 */
    private String clientSecret;

    /** 留空时按门户地址自动生成 */
    private String redirectUri;

    /** 留空使用内置默认 scope */
    private String scope;

    private String authorizeUrl;

    private String tokenUrl;

    private String userInfoUrl;

    /** 预留：PKCE、团队 ID、密钥 ID 等服务商差异参数 */
    private String extraJson;

    private Integer sort;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
