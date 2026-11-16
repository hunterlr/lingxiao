package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户-岗位关联（sys_user_post）。
 *
 * <p>联合主键，只做关联，不放业务字段。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_post")
public class SysUserPost {

    private Long userId;
    private Long postId;
}
