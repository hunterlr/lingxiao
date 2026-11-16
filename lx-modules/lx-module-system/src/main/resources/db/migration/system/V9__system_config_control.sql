-- =====================================================================
-- 凌霄 — 参数控件类型与选项（v1.0 第十轮）
-- =====================================================================
-- 背景：
--   sys_config 原来只有 config_type（Y 内置 / N 普通），前端无法判断
--   某个参数该用文本框、数字框、开关还是下拉，只能一律渲染成 input。
--   本轮把“怎么编辑”变成数据的一部分，前端按 control_type 渲染。
--
-- 新增两列：
--   control_type   控件类型：text / number / switch / select
--   config_options 控件选项，格式约定（后端 SysConfigService 负责校验）：
--     select  value:label,value:label   值:显示名，逗号 / 换行分隔
--     number  min:max                   整数范围，可只写一侧，如 1:3650
--     switch  onValue:offValue          默认 true:false
--     text    忽略
--
-- 约定：
--   1. 控件类型与选项是“编辑体验”，不是安全边界；后端仍会校验值是否合法；
--   2. 内置参数（config_type='Y'）的 control_type / config_options 同样锁定，
--      只允许改值，避免把开关参数改成文本框导致值不可解析；
--   3. 新增参数默认 text，与旧数据保持一致。
-- =====================================================================

ALTER TABLE sys_config
    ADD COLUMN control_type varchar(20) NOT NULL DEFAULT 'text'
        COMMENT '控件类型 text/number/switch/select' AFTER config_value,
    ADD COLUMN config_options varchar(1000) DEFAULT NULL
        COMMENT '控件选项：select=值:显示名,number=最小值:最大值,switch=开:关' AFTER control_type;

-- 1) 登录验证码：布尔开关
UPDATE sys_config
SET control_type = 'switch',
    config_options = 'true:false',
    remark = '开关参数：true 开启验证码，false 关闭验证码'
WHERE config_key = 'sys.account.captchaEnabled';

-- 2) 日志保留天数：正整数，1~3650 天
UPDATE sys_config
SET control_type = 'number',
    config_options = '1:3650',
    remark = '超过天数的操作日志 / 登录日志由定时任务清理（1~3650 天）'
WHERE config_key = 'sys.log.keepDays';

-- 3) 单文件上传上限：正整数，受容器 multipart 上限约束
UPDATE sys_config
SET control_type = 'number',
    config_options = '1:20',
    remark = '单位 MB；受容器 multipart 上限 20MB 约束，库里只能调小'
WHERE config_key = 'sys.file.uploadMaxMb';

-- 4) 富文本编辑器：两选一下拉，显示名给运营看
UPDATE sys_config
SET control_type = 'select',
    config_options = 'wangeditor-next:wangEditor Next,ueditor-plus:UEditor Plus',
    remark = '平台默认富文本编辑器；用户可在编辑器内临时切换'
WHERE config_key = 'sys.editor.type';
