-- =====================================================================
-- 迁移 — 门户多语言设置（V45）
-- =====================================================================
-- 范围
--   门户语言寻址方式与默认语言（后台「多语言 → 门户设置」可改）
--
-- 参数（sys_config.config_id 非自增，按顺序占用）：
--   35 portal.i18n.mode              path / query / cookie
--   36 portal.i18n.defaultLang       语言代码，默认 zh-CN
--   37 portal.i18n.defaultLangInPath 默认语言是否也带前缀
--   38 portal.i18n.langs             门户对外语言，逗号分隔
--
-- 说明
--   - 这些参数只影响门户前端如何生成 / 识别语言 URL，不影响后台接口；
--   - 门户通过 /api/portal/i18n/config 读取（5 秒缓存），改完无需重启。
-- =====================================================================

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 35, '门户语言寻址方式', 'portal.i18n.mode', 'path', 'Y',
       'select', 'path:query:cookie', NOW(3), '0', 0,
       'path=/en/article/1（推荐，SEO 最好）；query=/article/1?lang=en；cookie=URL 不变，只看 Cookie。切换会改变全站链接'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 35);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 36, '门户默认语言', 'portal.i18n.defaultLang', 'zh-CN', 'Y',
       'select', 'zh-CN:en-US', NOW(3), '0', 0,
       '默认语言不带 URL 前缀；做英文站时改成 en-US，此时 / 是英文、/zh-cn/ 是中文'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 36);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 37, '默认语言带前缀', 'portal.i18n.defaultLangInPath', 'false', 'Y',
       'switch', 'true:false', NOW(3), '0', 0,
       '关闭时默认语言在 /；打开后默认语言也带前缀（如 /zh-cn/），切换语言时所有地址都变，慎用'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 37);

INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type,
                        control_type, config_options, create_time, del_flag, version, remark)
SELECT 38, '门户对外语言', 'portal.i18n.langs', 'zh-CN,en-US', 'Y',
       'text', NULL, NOW(3), '0', 0,
       '门户允许切换的语言，逗号分隔；不在列表里的语言地址返回 404'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_id = 38);
