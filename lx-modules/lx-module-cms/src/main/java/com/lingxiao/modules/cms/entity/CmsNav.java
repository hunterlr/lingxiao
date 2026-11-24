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
 * CMS 导航（cms_nav）。
 *
 * <p>一个站点可以有多套导航：顶部、页脚、移动端。导航项在
 * {@code cms_nav_item} 中按 parent_id 组成树。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_nav")
public class CmsNav extends BaseEntity {

    @TableId(value = "nav_id", type = IdType.ASSIGN_ID)
    private Long navId;

    @NotNull(message = "{valid.cms.site.required}")
    private Long siteId;

    @NotBlank(message = "{valid.cms.nav.key.required}")
    @Size(max = 64, message = "{valid.cms.nav.key.max}")
    private String navKey;

    @NotBlank(message = "{valid.cms.nav.name.required}")
    @Size(max = 100, message = "{valid.cms.nav.name.max}")
    private String navName;

    /** top / footer / mobile */
    @NotBlank(message = "{valid.cms.nav.position}")
    private String position;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;

    /** 导航项树；详情接口回填，不是表字段 */
    @TableField(exist = false)
    private List<CmsNavItem> items = new ArrayList<>();
}
