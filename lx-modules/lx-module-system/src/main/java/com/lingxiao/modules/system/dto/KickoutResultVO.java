package com.lingxiao.modules.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 强退结果。
 *
 * <p>批量强退不采用“一个失败就整体回滚”：会话可能刚好自然过期，
 * 或者其中一条是当前会话。逐个执行、逐条返回失败原因，前端能明确告诉
 * 运维“踢掉了 2 个，1 个因为是自己没踢”。</p>
 */
@Data
public class KickoutResultVO {

    private int successCount;

    private int failureCount;

    /** 失败原因，例如“admin: 不能强退当前登录会话” */
    private List<String> failures = new ArrayList<>();

    public void addFailure(String reason) {
        failures.add(reason);
        failureCount = failures.size();
    }
}
