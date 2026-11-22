package com.lingxiao.modules.system.service;

import cn.dev33.satoken.SaManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lingxiao.admin.api.dto.LoginRequest;
import com.lingxiao.admin.api.dto.LoginResponse;
import com.lingxiao.admin.api.dto.MenuVO;
import com.lingxiao.admin.api.dto.PasswordChangeRequest;
import com.lingxiao.admin.api.dto.ProfileUpdateRequest;
import com.lingxiao.admin.api.dto.UserInfoResponse;
import com.lingxiao.common.core.config.ConfigProvider;
import com.lingxiao.common.core.constant.CommonConstants;
import com.lingxiao.common.core.enums.ErrorCode;
import com.lingxiao.common.core.exception.ServiceException;
import com.lingxiao.common.core.module.ModuleProvider;
import com.lingxiao.common.security.util.LoginHelper;
import com.lingxiao.common.security.util.StpPlatformUtil;
import com.lingxiao.common.web.util.ServletUtils;
import com.lingxiao.modules.system.dto.MenuPermRow;
import com.lingxiao.modules.system.entity.SysLoginLog;
import com.lingxiao.modules.system.entity.SysMenu;
import com.lingxiao.modules.system.entity.SysUser;
import com.lingxiao.modules.system.mapper.SysLoginLogMapper;
import com.lingxiao.modules.system.mapper.SysMenuMapper;
import com.lingxiao.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台认证服务。
 *
 * <p>职责边界：登录 / 登出 / 当前用户 / 菜单 / 本人资料与改密，不做用户 CRUD。
 * 用户管理后续放 {@code SysUserService}，避免一个类越写越大。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final CaptchaService captchaService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionPolicy sessionPolicy;
    private final ConfigProvider configProvider;

    /**
     * 模块开关。用 {@link ObjectProvider} 而不是直接注入：
     * 单独启动系统模块（没有 lx-framework）时拿不到实现，此时按“全部启用”处理。
     */
    private final ObjectProvider<ModuleProvider> moduleProvider;

    /** 连续失败几次后锁定；参数缺失或非法时按 5 次 */
    private static final int DEFAULT_MAX_RETRY = 5;
    /** 锁定时长（分钟）；参数缺失或非法时按 10 分钟 */
    private static final int DEFAULT_LOCK_MINUTES = 10;

    /**
     * 登录：验证码 → 锁定判断 → 查用户 → 校验密码 → 写登录日志 → 踢旧会话 → 发 Token。
     *
     * <p>失败计数落 {@code sys_user.login_fail_count / lock_time}，不依赖 Redis：
     * 单机、重启、后续多实例都读同一份状态。</p>
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 验证码先校验，失败直接返回，避免拿验证码接口做用户名枚举
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        LocalDateTime now = LocalDateTime.now();

        // 2. 锁定期间直接拒绝，不再比对密码，避免锁定期内继续撞库
        if (user != null && user.getLockTime() != null && user.getLockTime().isAfter(now)) {
            long remaining = Math.max(1, Duration.between(now, user.getLockTime()).toMinutes() + 1);
            recordLoginLog(request.getUsername(), CommonConstants.STATUS_DISABLED,
                    "账号锁定中，剩余 " + remaining + " 分钟");
            // 登录日志是给运维看的中文，不翻译；返回给用户的提示走语言包
            throw new ServiceException(ErrorCode.ACCOUNT_LOCKED, "error.2004.locked", remaining);
        }

        // 3. 密码错误：累计失败次数，达到阈值则锁定
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            if (user != null && registerFailure(user, now)) {
                int lockMinutes = lockMinutes();
                recordLoginLog(request.getUsername(), CommonConstants.STATUS_DISABLED,
                        "密码错误次数过多，账号锁定 " + lockMinutes + " 分钟");
                throw new ServiceException(ErrorCode.ACCOUNT_LOCKED, "error.2004.failures", lockMinutes);
            }
            recordLoginLog(request.getUsername(), CommonConstants.STATUS_DISABLED, "用户名或密码错误");
            throw new ServiceException(ErrorCode.LOGIN_FAILED);
        }
        if (!CommonConstants.STATUS_NORMAL.equals(user.getStatus())) {
            recordLoginLog(request.getUsername(), CommonConstants.STATUS_DISABLED, "账号已停用");
            throw new ServiceException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. 记录登录信息，并清零失败计数 / 锁定时间
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUserId, user.getUserId())
                .set(SysUser::getLoginIp, ServletUtils.getClientIp())
                .set(SysUser::getLoginTime, now)
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockTime, null));

        // 5. 单会话模式下先踢掉该账号的旧 Token，再发新 Token。
        //    参数是运行期读取的，改完下一次登录立即生效，不需要重启。
        if (!sessionPolicy.isMultiDeviceLoginEnabled()) {
            StpPlatformUtil.logoutByUserId(user.getUserId());
            log.info("单会话模式：账号 {} 新登录，已注销旧会话", user.getUsername());
        }

        // 6. 平台域发 Token，并把展示信息写入 Session
        StpPlatformUtil.login(user.getUserId());
        LoginHelper.setLoginUser(user.getUserId(), user.getUsername(), user.getNickname());
        // IP / 浏览器写到 Token-Session：同一账号多端登录时各会话要各记各的
        LoginHelper.setLoginContext(ServletUtils.getClientIp(),
                truncate(ServletUtils.getUserAgent(), 200));

        recordLoginLog(user.getUsername(), CommonConstants.STATUS_NORMAL, "登录成功");

        String tokenName = SaManager.getConfig().getTokenName();
        return new LoginResponse(tokenName, StpPlatformUtil.getTokenValue(), StpPlatformUtil.getTokenTimeout());
    }

    /**
     * 记录一次密码错误。
     *
     * <p>锁定到期后再失败时从 0 重新计数，避免“上次锁定前剩 1 次”被继承。</p>
     *
     * @return {@code true} 表示本次失败触发了锁定
     */
    private boolean registerFailure(SysUser user, LocalDateTime now) {
        int failCount = user.getLoginFailCount() == null ? 0 : user.getLoginFailCount();
        if (user.getLockTime() != null && !user.getLockTime().isAfter(now)) {
            failCount = 0;
        }
        int next = failCount + 1;
        if (next >= maxRetryCount()) {
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getUserId, user.getUserId())
                    .set(SysUser::getLoginFailCount, 0)
                    .set(SysUser::getLockTime, now.plusMinutes(lockMinutes())));
            log.warn("账号因连续登录失败被锁定: userId={}, username={}", user.getUserId(), user.getUsername());
            return true;
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUserId, user.getUserId())
                .set(SysUser::getLoginFailCount, next)
                .set(SysUser::getLockTime, null));
        return false;
    }

    /** 参数化锁定阈值，限制在 1~100，防止误配置成 0 导致“一错就锁”。 */
    private int maxRetryCount() {
        int value = configProvider.getIntValue("sys.account.maxRetryCount", DEFAULT_MAX_RETRY);
        return Math.min(100, Math.max(1, value));
    }

    /** 参数化锁定时长，限制在 1~1440 分钟，防止误配置成 0 或超长。 */
    private int lockMinutes() {
        int value = configProvider.getIntValue("sys.account.lockMinutes", DEFAULT_LOCK_MINUTES);
        return Math.min(1440, Math.max(1, value));
    }

    public void logout() {
        StpPlatformUtil.logout();
    }

    /**
     * 当前用户信息：基础资料 + 角色 + 权限码。
     */
    public UserInfoResponse getInfo() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }

        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRoles(menuMapper.selectRoleKeysByUserId(userId));
        response.setPermissions(resolvePermissions(userId));
        return response;
    }

    /**
     * 个人中心 - 修改资料。
     *
     * <p>只改昵称 / 邮箱 / 手机号；用户名、状态、角色不在自助范围内。
     * 昵称写回 Session，顶部头像区不用重新登录就能看到新名字。</p>
     */
    public UserInfoResponse updateProfile(ProfileUpdateRequest request) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }

        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (nickname.isEmpty()) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.nickname");
        }

        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUserId, userId)
                .set(SysUser::getNickname, nickname)
                .set(SysUser::getAvatar, normalizeAvatar(request.getAvatar()))
                .set(SysUser::getEmail, trimToNull(request.getEmail()))
                .set(SysUser::getPhone, trimToNull(request.getPhone()))
                .set(SysUser::getUpdateBy, userId));

        // Session 里存的是登录时的快照，不刷新会导致“改了昵称顶部还是旧的”
        LoginHelper.setLoginUser(userId, user.getUsername(), nickname);
        log.info("用户修改个人资料: userId={}, username={}", userId, user.getUsername());
        return getInfo();
    }

    /**
     * 个人中心 - 修改密码。
     *
     * <p>顺序很重要：先校验原密码，再落新密码，最后踢掉该账号全部会话。
     * 改密后旧 Token 立即失效；即使某个终端还拿着旧 Token，也无法继续访问。
     * 审计日志由 Controller 的 {@code @Log(saveRequestData = false)} 记录，
     * 明文密码既不落库也不进日志。</p>
     */
    public void changePassword(PasswordChangeRequest request) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new ServiceException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw ServiceException.keyed(ErrorCode.BAD_REQUEST, "error.1001.samePassword");
        }
        passwordPolicy.validate(request.getNewPassword());

        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getUserId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(request.getNewPassword()))
                .set(SysUser::getUpdateBy, userId));

        // 账号级注销：当前端与其它端一起失效，避免旧密码会话继续存活
        StpPlatformUtil.logoutByUserId(userId);
        log.info("用户自助修改密码: userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 当前用户可见菜单树。
     *
     * <p>超级管理员直接看全部启用菜单；普通用户按角色授权过滤。</p>
     */
    public List<MenuVO> getMenus() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }

        List<SysMenu> menus;
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .in(SysMenu::getMenuType, "M", "C")
                    .eq(SysMenu::getStatus, CommonConstants.STATUS_NORMAL)
                    .orderByAsc(SysMenu::getParentId)
                    .orderByAsc(SysMenu::getOrderNum));
        } else {
            menus = menuMapper.selectMenusByUserId(userId);
        }
        return pruneEmptyDirectories(buildTree(filterByModule(menus)));
    }

    /** 关闭的模块不下发菜单；目录（M）即使属于基座，也会在子节点被过滤后清掉。 */
    private List<SysMenu> filterByModule(List<SysMenu> menus) {
        List<SysMenu> visible = new ArrayList<>(menus.size());
        for (SysMenu menu : menus) {
            if (isModuleEnabled(menu.getModuleCode())) {
                visible.add(menu);
            }
        }
        return visible;
    }

    private boolean isModuleEnabled(String moduleCode) {
        ModuleProvider provider = moduleProvider.getIfAvailable();
        // 没有模块注册表（单独启动系统模块）时不做过滤，避免误伤全部菜单
        return provider == null || provider.isEnabled(moduleCode);
    }

    /**
     * 去掉没有子节点的目录。
     *
     * <p>场景：{@code 系统工具} 目录属于 system，里面的“定时任务 / 代码生成”属于
     * 可选模块。模块关闭后子节点被过滤，只剩一个点不开的空目录，必须一起隐藏。</p>
     */
    private List<MenuVO> pruneEmptyDirectories(List<MenuVO> nodes) {
        Iterator<MenuVO> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            MenuVO node = iterator.next();
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                pruneEmptyDirectories(node.getChildren());
            }
            if ("M".equals(node.getType()) && (node.getChildren() == null || node.getChildren().isEmpty())) {
                iterator.remove();
            }
        }
        return nodes;
    }

    private List<String> resolvePermissions(Long userId) {
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            return Collections.singletonList("*:*:*");
        }
        List<MenuPermRow> rows = menuMapper.selectPermsByUserId(userId);
        List<String> perms = new ArrayList<>(rows.size());
        for (MenuPermRow row : rows) {
            if (row.getPerms() != null && isModuleEnabled(row.getModuleCode())) {
                perms.add(row.getPerms());
            }
        }
        return perms;
    }

    /**
     * 列表转树。
     *
     * <p>只处理两层以上都适用的通用逻辑：先建节点，再挂到父节点；
     * 父节点不在结果集里的（例如角色只授权了子菜单）作为根节点返回，
     * 避免“授权了却看不到”。</p>
     */
    private List<MenuVO> buildTree(List<SysMenu> menus) {
        Map<Long, MenuVO> nodeMap = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            nodeMap.put(menu.getMenuId(), toVO(menu));
        }

        List<MenuVO> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            MenuVO node = nodeMap.get(menu.getMenuId());
            MenuVO parent = nodeMap.get(menu.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private MenuVO toVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getMenuId());
        vo.setParentId(menu.getParentId());
        vo.setMenuKey(menu.getMenuKey());
        vo.setName(menu.getMenuName());
        vo.setType(menu.getMenuType());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setPerms(menu.getPerms());
        vo.setIcon(menu.getIcon());
        vo.setOrderNum(menu.getOrderNum());
        vo.setVisible(menu.getVisible());
        vo.setModuleCode(menu.getModuleCode());
        return vo;
    }

    /**
     * 写登录日志。
     *
     * <p>日志失败不能影响登录主流程：这里捕获异常只打 warn。
     * 后续会改成异步事件，进一步降低对登录耗时的影响。</p>
     */
    private void recordLoginLog(String username, String status, String msg) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUsername(username);
            loginLog.setIp(ServletUtils.getClientIp());
            loginLog.setBrowser(truncate(ServletUtils.getUserAgent(), 200));
            loginLog.setOs("");
            loginLog.setStatus(status);
            loginLog.setMsg(msg);
            loginLog.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.warn("写登录日志失败: username={}, msg={}", username, e.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 空串按“清空”处理，避免库里出现 "" 与 NULL 两种空值。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 头像只允许两种形态：文件中心 fileId、http(s) 外链。
     *
     * <p>不接受其它协议（{@code javascript:} / {@code data:} 等），避免把
     * 用户输入直接塞进 {@code <img src>} 时留下注入面；第三方登录回写的
     * 头像虽然来自可信平台，也统一走这里。</p>
     */
    private String normalizeAvatar(String avatar) {
        String value = trimToNull(avatar);
        if (value == null) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        // 文件中心 fileId 是纯数字；其它内容一律拒绝并回空，不让脏值入库
        return value.matches("\\d{1,20}") ? value : null;
    }
}
