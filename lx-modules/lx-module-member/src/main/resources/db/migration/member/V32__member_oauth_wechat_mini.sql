-- =====================================================================
-- 迁移 — 会员第三方登录补充微信小程序（V32）
-- =====================================================================
-- 背景
--   V31 已覆盖微信开放平台 / 公众号，但小程序没有网页授权入口：
--   客户端先 wx.login()，再把 code 交给 POST /api/app/auth/oauth/mini-login。
--   本脚本只补一条服务商种子，默认停用；门户不会展示按钮。
-- =====================================================================

INSERT INTO mem_oauth_provider
  (tenant_id, provider, display_name, status, sort, create_time)
SELECT 0, 'wechat_mini', '微信小程序', '1', 25, NOW(3)
WHERE NOT EXISTS (
  SELECT 1 FROM mem_oauth_provider p
  WHERE p.tenant_id = 0 AND p.provider = 'wechat_mini'
);
