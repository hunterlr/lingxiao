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
 * CMS 导航项（cms_nav_item）。
 *
 * <p>目标类型：category（栏目）/ page（单页）/ link（外链或自定义相对地址）。
 * 不建跨表外键，目标是否存在、是否同站点由服务层校验。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cms_nav_item")
public class CmsNavItem extends BaseEntity {

    @TableId(value = "item_id", type = IdType.ASSIGN_ID)
    private Long itemId;

    @NotNull(message = "{valid.cms.nav.required}")
    private Long navId;

    private Long siteId;

    /** 0 表示根节点 */
    private Long parentId;

    @NotBlank(message = "{valid.cms.nav.item.name.required}")
    @Size(max = 100, message = "{valid.cms.nav.item.name.max}")
    private String itemName;

    /** category / page / link */
    @NotBlank(message = "{valid.cms.nav.item.target.type}")
    private String targetType;

    private Long targetId;

    @Size(max = 500, message = "{valid.cms.nav.item.url.max}")
    private String url;

    @Size(max = 64, message = "{valid.cms.icon.max}")
    private String icon;

    private Integer sort;

    /** 0 显示 / 1 隐藏 */
    private String visible;

    /** 0 启用 / 1 停用 */
    private String status;

    @Size(max = 500, message = "{valid.remark.max}")
    private String remark;

    @TableField(exist = false)
    private List<CmsNavItem> children = new ArrayList<>();
}
