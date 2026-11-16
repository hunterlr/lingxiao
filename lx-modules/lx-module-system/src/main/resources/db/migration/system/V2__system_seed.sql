-- =====================================================================
-- 凌霄 · 系统模块初始化数据（v1.0）
-- =====================================================================
-- 默认账号：admin / admin123
-- 生产部署后第一件事：改密码、删演示公告、检查系统参数。
-- 密码为 BCrypt 哈希，禁止在 SQL 里存明文。
-- =====================================================================

INSERT INTO sys_dept (dept_id, parent_id, dept_name, order_num, leader, phone, email, status, create_time, del_flag, version)
VALUES (1, 0, '凌霄科技', 1, '管理员', '13800000000', 'admin@lingxiao.local', '0', NOW(3), '0', 0);

INSERT INTO sys_dept_closure (ancestor_id, descendant_id, depth) VALUES (1, 1, 0);

INSERT INTO sys_post (post_id, post_code, post_name, sort, status, create_time, del_flag, version)
VALUES (1, 'ceo', '总经理', 1, '0', NOW(3), '0', 0);

INSERT INTO sys_user (user_id, dept_id, username, nickname, password, email, phone, sex, status, create_time, del_flag, version, remark)
VALUES (1, 1, 'admin', '超级管理员',
        '$2a$10$cdvtHgM0Bd4t.3y5UibkPO4cJEZoxqyxASm/B6KkqX7mwga6/avu6',
        'admin@lingxiao.local', '13800000000', '2', '0', NOW(3), '0', 0, '系统内置超级管理员');

INSERT INTO sys_user_post (user_id, post_id) VALUES (1, 1);

INSERT INTO sys_role (role_id, role_key, role_name, sort, data_scope, status, create_time, del_flag, version, remark)
VALUES (1, 'admin', '超级管理员', 1, '1', '0', NOW(3), '0', 0, '拥有全部权限');

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 目录
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(1, 'system', 0, '系统管理', 'M', '/system', NULL, NULL, 'setting', 1, '0', '0', 'system', NOW(3), '0', 0),
(2, 'monitor', 0, '系统监控', 'M', '/monitor', NULL, NULL, 'monitor', 2, '0', '0', 'system', NOW(3), '0', 0),
(3, 'tool', 0, '系统工具', 'M', '/tool', NULL, NULL, 'tool', 3, '0', '0', 'system', NOW(3), '0', 0),
(4, 'profile', 0, '个人中心', 'C', '/profile', 'profile/index', 'platform:profile:view', 'user', 4, '0', '0', 'system', NOW(3), '0', 0);

-- 系统管理
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(5, 'system:user', 1, '用户管理', 'C', '/system/user', 'system/user/index', 'platform:system:user:list', 'user', 1, '0', '0', 'system', NOW(3), '0', 0),
(6, 'system:role', 1, '角色管理', 'C', '/system/role', 'system/role/index', 'platform:system:role:list', 'peoples', 2, '0', '0', 'system', NOW(3), '0', 0),
(7, 'system:menu', 1, '菜单管理', 'C', '/system/menu', 'system/menu/index', 'platform:system:menu:list', 'tree-table', 3, '0', '0', 'system', NOW(3), '0', 0),
(8, 'system:dept', 1, '部门管理', 'C', '/system/dept', 'system/dept/index', 'platform:system:dept:list', 'tree', 4, '0', '0', 'system', NOW(3), '0', 0),
(9, 'system:post', 1, '岗位管理', 'C', '/system/post', 'system/post/index', 'platform:system:post:list', 'post', 5, '0', '0', 'system', NOW(3), '0', 0),
(10, 'system:dict', 1, '字典管理', 'C', '/system/dict', 'system/dict/index', 'platform:system:dict:list', 'dict', 6, '0', '0', 'system', NOW(3), '0', 0),
(11, 'system:config', 1, '参数设置', 'C', '/system/config', 'system/config/index', 'platform:system:config:list', 'edit', 7, '0', '0', 'system', NOW(3), '0', 0),
(12, 'system:notice', 1, '公告管理', 'C', '/system/notice', 'system/notice/index', 'platform:system:notice:list', 'message', 8, '0', '0', 'system', NOW(3), '0', 0);

-- 系统监控
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(13, 'monitor:online', 2, '在线用户', 'C', '/monitor/online', 'monitor/online/index', 'platform:monitor:online:list', 'online', 1, '0', '0', 'system', NOW(3), '0', 0),
(14, 'monitor:login-log', 2, '登录日志', 'C', '/monitor/login-log', 'monitor/loginlog/index', 'platform:monitor:loginlog:list', 'logininfor', 2, '0', '0', 'system', NOW(3), '0', 0),
(15, 'monitor:oper-log', 2, '操作日志', 'C', '/monitor/oper-log', 'monitor/operlog/index', 'platform:monitor:operlog:list', 'form', 3, '0', '0', 'system', NOW(3), '0', 0),
(16, 'monitor:server', 2, '服务监控', 'C', '/monitor/server', 'monitor/server/index', 'platform:monitor:server:list', 'server', 4, '0', '0', 'system', NOW(3), '0', 0),
(17, 'monitor:cache', 2, '缓存监控', 'C', '/monitor/cache', 'monitor/cache/index', 'platform:monitor:cache:list', 'redis', 5, '0', '0', 'system', NOW(3), '0', 0);

-- 系统工具
INSERT INTO sys_menu (menu_id, menu_key, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, visible, status, module_code, create_time, del_flag, version)
VALUES
(18, 'tool:file', 3, '文件中心', 'C', '/tool/file', 'tool/file/index', 'platform:file:list', 'upload', 1, '0', '0', 'file', NOW(3), '0', 0),
(19, 'tool:file-library', 3, '文件库', 'C', '/tool/file-library', 'tool/file/library', 'platform:file:library:list', 'picture', 2, '0', '0', 'file', NOW(3), '0', 0),
(20, 'tool:image', 3, '图片处理', 'C', '/tool/image', 'tool/file/image', 'platform:file:image:list', 'crop', 3, '0', '0', 'file', NOW(3), '0', 0),
(21, 'tool:job', 3, '定时任务', 'C', '/tool/job', 'tool/job/index', 'platform:monitor:job:list', 'time-range', 4, '0', '0', 'system', NOW(3), '0', 0),
(22, 'tool:gen', 3, '代码生成', 'C', '/tool/gen', 'tool/gen/index', 'platform:tool:gen:list', 'code', 5, '0', '0', 'system', NOW(3), '0', 0),
(23, 'tool:api-doc', 3, '接口文档', 'C', '/tool/api-doc', 'tool/apidoc/index', 'platform:tool:apidoc:list', 'documentation', 6, '0', '0', 'system', NOW(3), '0', 0);

-- 超级管理员拥有全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu;

-- 字典
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, del_flag, version, remark)
VALUES
(1, '系统开关', 'sys_common_status', '0', NOW(3), '0', 0, '0正常 1停用'),
(2, '用户性别', 'sys_user_sex', '0', NOW(3), '0', 0, '0男 1女 2未知'),
(3, '是否', 'sys_yes_no', '0', NOW(3), '0', 0, 'Y是 N否');

INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_time, del_flag, version)
VALUES
(1, 1, '正常', '0', 'sys_common_status', NULL, 'primary', 'Y', '0', NOW(3), '0', 0),
(2, 2, '停用', '1', 'sys_common_status', NULL, 'danger', 'N', '0', NOW(3), '0', 0),
(3, 1, '男', '0', 'sys_user_sex', NULL, NULL, 'Y', '0', NOW(3), '0', 0),
(4, 2, '女', '1', 'sys_user_sex', NULL, NULL, 'N', '0', NOW(3), '0', 0),
(5, 3, '未知', '2', 'sys_user_sex', NULL, 'info', 'N', '0', NOW(3), '0', 0),
(6, 1, '是', 'Y', 'sys_yes_no', NULL, 'primary', 'Y', '0', NOW(3), '0', 0),
(7, 2, '否', 'N', 'sys_yes_no', NULL, 'danger', 'N', '0', NOW(3), '0', 0);

-- 系统参数
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, del_flag, version, remark)
VALUES
(1, '登录验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', NOW(3), '0', 0, 'true 开启 / false 关闭'),
(2, '日志保留天数', 'sys.log.keepDays', '180', 'Y', NOW(3), '0', 0, '超过天数的日志由定时任务清理'),
(3, '单文件上传上限', 'sys.file.uploadMaxMb', '10', 'Y', NOW(3), '0', 0, '单位 MB');

-- 演示公告
INSERT INTO sys_notice (notice_id, notice_title, notice_type, notice_content, status, create_time, del_flag, version, remark)
VALUES (1, '凌霄后台基座已启动', '2',
        '<p>欢迎使用凌霄后台基座。默认账号 <b>admin</b>，首次登录后请立即修改密码。</p>',
        '0', NOW(3), '0', 0, '初始化演示数据');

-- 演示定时任务（默认暂停，避免误执行）
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_time, del_flag, version, remark)
VALUES (1, '清理过期日志', 'SYSTEM', 'logCleanTask.clean', '0 0 2 * * ?', '3', '1', '1', NOW(3), '0', 0, '演示任务，默认暂停');
