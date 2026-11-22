package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员账号合并审计（mem_merge_log）。
 *
 * <p>记录谁把哪个旧账号并入了哪个新账号、迁移与跳过了多少条数据，
 * 便于出现争议时回溯。合并不会物理删除旧账号，只停用并踢掉会话。</p>
 */
@Data
@TableName("mem_merge_log")
public class MemMergeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "merge_id", type = IdType.ASSIGN_ID)
    private Long mergeId;

    private Long tenantId;

    private Long sourceMemberId;

    private Long targetMemberId;

    private String sourceUsername;

    private String targetUsername;

    private Integer favoriteMoved;

    private Integer favoriteSkipped;

    private Integer likeMoved;

    private Integer likeSkipped;

    private Integer commentMoved;

    private Integer socialMoved;

    private Integer socialSkipped;

    private Long operatorId;

    private String remark;

    private LocalDateTime createTime;
}
