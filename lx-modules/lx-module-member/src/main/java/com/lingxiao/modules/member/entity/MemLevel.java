package com.lingxiao.modules.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员等级（mem_level）。
 *
 * <p>等级按成长值区间划分，成长值由后台手动调整或后续积分任务计算。
 * {@code benefits_json} 预留给 VIP 折扣、下载次数等权益，v1.2 第一刀不解析。</p>
 *
 * <p>默认种子数据：注册会员 / 活跃会员 / VIP 会员，level_id 固定为 1/2/3，
 * 方便其它模块引用；后台修改名称不影响 ID。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mem_level")
public class MemLevel extends BaseEntity {

    @TableId(value = "level_id", type = IdType.ASSIGN_ID)
    private Long levelId;

    /** 租户 ID；单租户部署固定 0 */
    private Long tenantId;

    /** 租户内稳定键，例如 registered / active / vip */
    private String levelCode;

    private String levelName;

    /** 区间下界（含） */
    private Integer minGrowth;

    /** 区间上界（含）；NULL 表示不封顶 */
    private Integer maxGrowth;

    /** 权益 JSON，v1.2 预留 */
    private String benefitsJson;

    private Integer sort;

    /** 0 启用 / 1 停用 */
    private String status;

    private String remark;
}
