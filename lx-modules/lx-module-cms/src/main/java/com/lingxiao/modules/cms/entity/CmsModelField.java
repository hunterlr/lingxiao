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

/**
 * CMS 模型字段（cms_model_field）。
 *
 * <p>字段定义只描述“编辑表单长什么样 + 值怎么校验”，值本身统一存
 * {@code cms_content_ext.ext_json}，避免每次加字段都改业务主表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_model_field")
public class CmsModelField extends BaseEntity {

    @TableId(value = "field_id", type = IdType.ASSIGN_ID)
    private Long fieldId;

    @NotNull(message = "{valid.cms.model.required}")
    private Long modelId;

    private Long siteId;

    @NotBlank(message = "{valid.cms.field.key.required}")
    @Size(max = 64, message = "{valid.cms.field.key.max}")
    private String fieldKey;

    @NotBlank(message = "{valid.cms.field.name.required}")
    @Size(max = 100, message = "{valid.cms.field.name.max}")
    private String fieldName;

    /** text/textarea/richtext/number/date/select/checkbox/image/images/file */
    @NotBlank(message = "{valid.cms.field.type}")
    private String fieldType;

    /** 1=必填；数据库列 is_required */
    @TableField("is_required")
    private String required;

    @Size(max = 500, message = "{valid.cms.field.default.max}")
    private String defaultValue;

    @Size(max = 4000, message = "{valid.cms.field.options.max}")
    private String optionsJson;

    @Size(max = 1000, message = "{valid.cms.field.validation.max}")
    private String validationJson;

    /** 1=列表显示；数据库列 is_list */
    @TableField("is_list")
    private String listVisible;

    /** 1=可搜索；数据库列 is_search */
    @TableField("is_search")
    private String searchable;

    private Integer sort;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;
}
