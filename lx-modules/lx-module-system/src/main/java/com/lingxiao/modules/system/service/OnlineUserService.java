package com.lingxiao.modules.system.service;

import cn.dev33.satoken.session.SaSession;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.domain.PageResult;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.common.security.util.StpPlatformUtil;
import com.lingxiao.modules.system.dto.KickoutResultVO;
import com.lingxiao.modules.system.dto.OnlineUserVO;
import com.lingxiao.modules.system.entity.SysDept;
import com.lingxiao.modules.system.entity.SysUser;
import com.lingxiao.modules.system.mapper.SysDeptMapper;
import com.lingxiao.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线用户服务（W4 系统监控）。
 *
 * <p><b>数据来源：</b>直接枚举平台安全域的 Sa-Token 会话，不额外建“在线表”。
 * 原因很简单：会话的真实状态只有 Sa-Token 知道（过期、续期、被踢），
 * 另建一张表必然出现“表里还在线、实际已失效”的双写不一致。</p>
 *
 * <p><b>分页策略：</b>先枚举并过滤出真实会话，再在内存分页。
 * 会话数量有限（几百级别），这样总数 / 页码永远准确；
 * 如果先分页再按用户名过滤，会出现“第 1 页 10 条，过滤后 2 条”的错觉。</p>
 *
 * <p><b>安全约束：</b></p>
 * <ul>
 *   <li>列表返回脱敏 tokenId，真实 Token 不出服务端；强退时按脱敏值反查；</li>
 *   <li>禁止强退当前会话，防止运维把自己踢下线；</li>
 *   <li>非超级管理员不能强退 {@code user_id=1}，防止低权限运维锁死内置账号。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService {

    /** 单次请求最多处理的会话数，防止极端情况下搜索全部 Key 拖垮接口 */
    private static final int MAX_TOKENS = 5000;

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    /**
     * 分页查询在线用户。
     *
     * @param username 登录账号，模糊匹配（不区分大小写）
     * @param ip       登录 IP，模糊匹配
     */
    public PageResult<OnlineUserVO> page(long pageNum, long pageSize, String username, String ip) {
        long currentPage = Math.max(pageNum, 1);
        long currentSize = Math.min(Math.max(pageSize, 1), 100);

        List<OnlineUserVO> all = new ArrayList<>();
        for (TokenSnapshot snapshot : listSnapshots()) {
            all.add(toVO(snapshot));
        }
        // 先补用户资料再过滤：用户名 / 昵称在账号 Session 里，Token-Session 只存
        // 登录 IP / 浏览器；不先补齐会让“按账号筛选”把所有行都过滤掉。
        fillUserInfo(all);

        List<OnlineUserVO> matched = new ArrayList<>();
        for (OnlineUserVO vo : all) {
            if (matches(vo, username, ip)) {
                matched.add(vo);
            }
        }
        // 新登录的排前面；createTime 理论上不会为空，为空时放最后
        matched.sort(Comparator.comparing(OnlineUserVO::getLoginTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        long total = matched.size();
        int from = (int) Math.min((currentPage - 1) * currentSize, total);
        int to = (int) Math.min(from + currentSize, total);

        PageResult<OnlineUserVO> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNum(currentPage);
        result.setPageSize(currentSize);
        result.setRecords(new ArrayList<>(matched.subList(from, to)));
        return result;
    }

    /**
     * 强制下线（支持批量）。
     *
     * <p>逐条执行、逐条返回失败原因；不因为其中一条失效就整批失败。
     * 真实 Token 只在服务端出现，日志与请求体都只有脱敏值。</p>
     */
    public KickoutResultVO kickout(List<String> tokenIds) {
        List<String> requested = tokenIds == null ? Collections.emptyList() : tokenIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (requested.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请选择要强退的会话");
        }

        List<TokenSnapshot> snapshots = listSnapshots();
        Map<String, TokenSnapshot> byMask = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (TokenSnapshot snapshot : snapshots) {
            String mask = maskToken(snapshot.value);
            // 极端情况下首 4 + 末 4 相同：宁可拒绝，也不能踢错人
            if (byMask.putIfAbsent(mask, snapshot) != null) {
                ambiguous.add(mask);
            }
        }

        String currentValue = StpPlatformUtil.getTokenValue();
        String currentMask = currentValue == null ? null : maskToken(currentValue);
        Long operatorId = LoginHelper.getUserId();
        boolean operatorIsSuper = CommonConstants.SUPER_ADMIN_ID.equals(operatorId);

        KickoutResultVO result = new KickoutResultVO();
        for (String tokenId : requested) {
            if (tokenId.equals(currentMask)) {
                result.addFailure("不能强退当前登录会话");
                continue;
            }
            if (ambiguous.contains(tokenId)) {
                result.addFailure("会话标识不唯一，请刷新后重试");
                continue;
            }
            TokenSnapshot target = byMask.get(tokenId);
            if (target == null) {
                result.addFailure("会话已失效: " + tokenId);
                continue;
            }
            if (!operatorIsSuper && CommonConstants.SUPER_ADMIN_ID.equals(target.userId)) {
                result.addFailure("不能强退内置超级管理员的会话");
                continue;
            }
            StpPlatformUtil.logoutByTokenValue(target.value);
            result.setSuccessCount(result.getSuccessCount() + 1);
            log.info("强制下线: operator={}, targetUserId={}, token={}",
                    LoginHelper.getUsername(), target.userId, tokenId);
        }
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 枚举当前所有有效会话。
     *
     * <p>{@code searchTokenValue("", 0, -1)} 会返回带前缀的完整 Key。
     * 同一前缀下还有 Token-Session、last-active 等派生 Key，
     * 它们的特征是去掉前缀后仍带 {@code :}，这里直接排除，
     * 再逐个确认 Session 还在，避免把“残留 Key”当成在线用户。</p>
     */
    private List<TokenSnapshot> listSnapshots() {
        List<String> keys = StpPlatformUtil.searchTokenValue("", 0, -1);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        String prefix = StpPlatformUtil.getTokenKeyPrefix();
        List<TokenSnapshot> snapshots = new ArrayList<>();
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String tokenValue = key.startsWith(prefix) ? key.substring(prefix.length()) : key;
            if (tokenValue.isEmpty() || tokenValue.indexOf(':') >= 0) {
                continue;
            }
            SaSession session = StpPlatformUtil.getTokenSession(tokenValue);
            if (session == null) {
                continue;
            }
            Long userId = toLong(session.getLoginId());
            if (userId == null) {
                userId = toLong(StpPlatformUtil.getLoginIdByToken(tokenValue));
            }
            // -1 及以下的负数 / -4 / -5 是 Sa-Token 的内部失效标记，不是真实用户
            if (userId == null || userId <= 0) {
                continue;
            }
            snapshots.add(new TokenSnapshot(tokenValue, userId, session));
            if (snapshots.size() >= MAX_TOKENS) {
                log.warn("在线用户枚举达到上限 {}，剩余会话未展示", MAX_TOKENS);
                break;
            }
        }
        return snapshots;
    }

    private OnlineUserVO toVO(TokenSnapshot snapshot) {
        SaSession session = snapshot.session;
        OnlineUserVO vo = new OnlineUserVO();
        vo.setTokenId(maskToken(snapshot.value));
        vo.setUserId(snapshot.userId);
        vo.setUsername(LoginHelper.readString(session, LoginHelper.SESSION_USERNAME));
        vo.setNickname(LoginHelper.readString(session, LoginHelper.SESSION_NICKNAME));
        vo.setLoginIp(LoginHelper.readString(session, LoginHelper.SESSION_LOGIN_IP));
        vo.setBrowser(LoginHelper.readString(session, LoginHelper.SESSION_USER_AGENT));

        LocalDateTime loginTime = toDateTime(session.getCreateTime());
        vo.setLoginTime(loginTime);
        long lastActive = StpPlatformUtil.getTokenLastActiveTime(snapshot.value);
        vo.setLastActiveTime(lastActive > 0 ? toDateTime(lastActive) : loginTime);

        long timeout = StpPlatformUtil.getTokenTimeout(snapshot.value);
        vo.setTimeoutSeconds(Math.max(timeout, 0));

        boolean current = snapshot.value.equals(StpPlatformUtil.getTokenValue());
        vo.setCurrent(current);
        if (current) {
            vo.setKickable(false);
            vo.setKickableReason("当前登录会话");
        } else if (!CommonConstants.SUPER_ADMIN_ID.equals(LoginHelper.getUserId())
                && CommonConstants.SUPER_ADMIN_ID.equals(snapshot.userId)) {
            vo.setKickable(false);
            vo.setKickableReason("内置超级管理员");
        } else {
            vo.setKickable(true);
        }
        return vo;
    }

    private boolean matches(OnlineUserVO vo, String username, String ip) {
        if (StringUtils.hasText(username)) {
            String target = vo.getUsername() == null ? "" : vo.getUsername().toLowerCase(Locale.ROOT);
            if (!target.contains(username.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (StringUtils.hasText(ip)) {
            String target = vo.getLoginIp() == null ? "" : vo.getLoginIp();
            if (!target.contains(ip.trim())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 补用户名 / 昵称 / 部门名。
     *
     * <p>Session 里已有用户名和昵称，这里查库主要为了拿部门名；
     * 顺带用库里的值兜底“Session 缺字段”的历史会话。</p>
     */
    private void fillUserInfo(List<OnlineUserVO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Set<Long> userIds = rows.stream().map(OnlineUserVO::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, user -> user, (a, b) -> a));

        Set<Long> deptIds = userMap.values().stream().map(SysUser::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = deptIds.isEmpty()
                ? Collections.emptyMap()
                : deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getDeptId, SysDept::getDeptName, (a, b) -> a));

        for (OnlineUserVO row : rows) {
            SysUser user = userMap.get(row.getUserId());
            if (user == null) {
                continue;
            }
            if (!StringUtils.hasText(row.getUsername())) {
                row.setUsername(user.getUsername());
            }
            if (!StringUtils.hasText(row.getNickname())) {
                row.setNickname(user.getNickname());
            }
            row.setDeptName(deptNameMap.get(user.getDeptId()));
        }
    }

    /**
     * Token 脱敏：首 4 + 末 4，中间固定 4 个星号。
     *
     * <p>不是加密，只是“让响应里不出现可直接使用的凭证”。
     * 强退时用同一个函数把真实 Token 转成脱敏值做匹配，逻辑对称。</p>
     */
    private String maskToken(String tokenValue) {
        if (tokenValue == null || tokenValue.length() <= 8) {
            return "****";
        }
        return tokenValue.substring(0, 4) + "****"
                + tokenValue.substring(tokenValue.length() - 4);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime toDateTime(long epochMilli) {
        if (epochMilli <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    /** 一个真实会话的最小快照，避免列表与强退各枚举一遍时读多次缓存 */
    private static final class TokenSnapshot {
        private final String value;
        private final Long userId;
        private final SaSession session;

        private TokenSnapshot(String value, Long userId, SaSession session) {
            this.value = value;
            this.userId = userId;
            this.session = session;
        }
    }
}
