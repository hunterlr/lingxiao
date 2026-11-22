package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员消息通道配置（mem_channel_config）。
 *
 * <p>每个通道一行：{@code sms} / {@code email}。{@code mode=dev} 使用写日志的
 * 开发实现，{@code mode=prod} 使用部署方提供的 {@code MemberSmsSender} /
 * {@code MemberEmailSender} 实现；运行期从后台「会员管理 → 渠道配置」修改，
 * 不需要重启，也不占用通用参数设置。</p>
 *
 * <p>{@code accessSecret} 只接收、不回传；接口响应不会把密钥带到前端。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mem_channel_config")
public class MemChannelConfig extends BaseEntity {

    @TableId(value = "config_id", type = IdType.ASSIGN_ID)
    private Long configId;

    /** sms / email */
    private String channelType;

    /** dev 开发日志 / prod 生产通道 */
    private String mode;

    /** 服务商标识：dev / aliyun / tencent / smtp / custom */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String provider;

    /** 服务商接口地址；SMTP 可填主机与端口 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String endpoint;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String accessKey;

    /** 密钥只写不读，前端留空表示不修改 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String accessSecret;

    /** 短信签名 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String signName;

    /** 短信模板编码 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String templateCode;

    /** 邮件发件人 / 短信发送方标识 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String sender;

    /** 0 启用 / 1 停用 */
    private String status;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;
}
