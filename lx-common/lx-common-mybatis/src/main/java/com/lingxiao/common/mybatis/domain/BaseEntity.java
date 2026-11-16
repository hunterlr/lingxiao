package com.lingxiao.common.mybatis.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 所有业务实体的公共字段。
 *
 * <p>字段与 docs/05-database.md 的“审计四字段 + del_flag + version”一致：</p>
 * <ul>
 *   <li>{@code createBy / createTime}：创建人、创建时间；</li>
 *   <li>{@code updateBy / updateTime}：最后修改人、最后修改时间；</li>
 *   <li>{@code delFlag}：逻辑删除，0 正常、1 删除；</li>
 *   <li>{@code version}：乐观锁版本号，更新时自动 +1。</li>
 * </ul>
 *
 * <p>注意：ID 不放在这里。每张表的主键名不同（user_id / role_id / file_id），
 * 由具体实体自己声明，避免 BaseEntity 变成“什么都往里塞”的垃圾桶。</p>
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private String delFlag;

    @Version
    private Integer version;
}
