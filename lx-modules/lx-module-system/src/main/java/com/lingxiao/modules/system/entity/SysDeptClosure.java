package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门闭包表（sys_dept_closure）。
 *
 * <p>每行表示“祖先 → 后代”的层级关系，{@code depth} 是相差层数：
 * 自己到自己是 0，直接父级是 1。</p>
 *
 * <p>为什么用闭包表：</p>
 * <ul>
 *   <li>查“某部门及所有下级”只需一条 {@code where ancestor_id = ?}，不递归；</li>
 *   <li>不依赖 MySQL 方言 {@code find_in_set}，PostgreSQL / 达梦 / 金仓同样可用；</li>
 *   <li>代价是写入时要维护关联行，但部门变更频率极低。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_dept_closure")
public class SysDeptClosure {

    /** 祖先部门 ID */
    private Long ancestorId;

    /** 后代部门 ID */
    private Long descendantId;

    /** 层级差：0 自己，1 直接父子 */
    private Integer depth;
}
