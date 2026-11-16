package com.lingxiao.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志（sys_login_log）。
 *
 * <p>只追加不修改；失败也要记，便于排查暴力破解与账号锁定。</p>
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

    @TableId(value = "info_id", type = IdType.ASSIGN_ID)
    private Long infoId;

    private String username;

    /** 客户端 IP；代理场景由 Nginx 覆盖 X-Forwarded-For */
    private String ip;

    private String browser;
    private String os;

    /** 0 成功 / 1 失败 */
    private String status;

    /** 失败原因；成功可为空 */
    private String msg;

    private LocalDateTime loginTime;
}
