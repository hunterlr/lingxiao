package com.lingxiao.modules.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxiao.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * CMS 内容模型（cms_model）。
 *
 * <p>模型按站点隔离：每个站点都有内置的 article / gallery / page 三个模型。
 * 栏目通过 {@code model_id} 绑定文章或图集模型；单页固定使用 page 模型。</p>
 *
 * <p>内置模型由迁移或 {@code CmsModelService.ensureBuiltIn} 初始化，不允许删除或改 key。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_model")
public class CmsModel extends BaseEntity {

    @TableId(value = "model_id", type = IdType.ASSIGN_ID)
    private Long modelId;

    @NotNull(message = "{valid.cms.site.required}")
    private Long siteId;

    @NotBlank(message = "{valid.cms.model.key.required}")
    @Size(max = 64, message = "{valid.cms.model.key.max}")
    private String modelKey;

    @NotBlank(message = "{valid.cms.model.name.required}")
    @Size(max = 100, message = "{valid.cms.model.name.max}")
    private String modelName;

    /** article / gallery / page */
    @NotBlank(message = "{valid.cms.model.type}")
    private String modelType;

    /** 1=内置模型：不可删除、不可改 key */
    private String builtIn;

    /** 0 启用 / 1 停用 */
    private String status;

    private Integer sort;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;

    /** 字段定义；详情接口回填，不是表字段 */
    @TableField(exist = false)
    private List<CmsModelField> fields = new ArrayList<>();
}
