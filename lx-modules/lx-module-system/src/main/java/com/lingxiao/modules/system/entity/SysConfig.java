package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 系统参数（sys_config）。
 *
 * <p>{@code configType = 'Y'} 是内置参数：允许改值，但**不允许改 key、不允许删除**，
 * 因为代码里可能按 key 读取。</p>
 *
 * <p>v1.0 还没有参数缓存：读取直接查库。后续加 Caffeine / Redis 缓存后，
 * 更新与删除必须主动失效。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    @TableId(value = "config_id", type = IdType.ASSIGN_ID)
    private Long configId;

    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称不能超过 100 个字符")
    private String configName;

    @NotBlank(message = "参数键名不能为空")
    @Size(max = 100, message = "参数键名不能超过 100 个字符")
    private String configKey;

    @NotBlank(message = "参数键值不能为空")
    @Size(max = 500, message = "参数键值不能超过 500 个字符")
    private String configValue;

    /**
     * 控件类型：{@code text / number / switch / select}。
     *
     * <p>决定参数设置页用什么控件编辑；空值按 {@code text} 处理，
     * 兼容 V9 之前没有该列的历史数据。</p>
     */
    @Size(max = 20, message = "控件类型不能超过 20 个字符")
    private String controlType;

    /**
     * 控件选项，格式由控件类型决定；解析与校验见
     * {@code SysConfigService#normalizeAndValidateControl}。
     *
     * <ul>
     *   <li>select：{@code value:label,value:label}，值:显示名；</li>
     *   <li>number：{@code min:max}，整数范围，可只写一侧；</li>
     *   <li>switch：{@code onValue:offValue}，默认 {@code true:false}；</li>
     *   <li>text：忽略。</li>
     * </ul>
     */
    @Size(max = 1000, message = "控件选项不能超过 1000 个字符")
    private String configOptions;

    /** Y 内置 / N 普通 */
    private String configType;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
