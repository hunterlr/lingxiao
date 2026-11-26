package com.lingxiao.modules.i18n.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 语言包版本（i18n_pack）。
 *
 * <p>{@code snapshotJson} 是发布时的 {@code keyId -> valueText} 快照；
 * 回滚时按快照恢复 i18n_value，保证“回滚真的回滚译文”。</p>
 */
@Data
@TableName("i18n_pack")
public class I18nPack implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "pack_id", type = IdType.ASSIGN_ID)
    private Long packId;

    private Long tenantId;

    private String languageCode;

    private String namespace;

    private Integer version;

    private String checksum;

    /** 0 草稿/历史 / 1 当前发布 / 2 已废弃 */
    private String status;

    private String snapshotJson;

    private Long publishedBy;

    private LocalDateTime publishedAt;
}
