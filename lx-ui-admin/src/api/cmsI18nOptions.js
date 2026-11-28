/**
 * 内容翻译可选语言。
 *
 * <p>与门户「门户设置」里的语言列表保持同一套代码：后台只提供内置两种，
 * 新增语言需要同时改门户路由前缀（见 docs/31 §10.5）。</p>
 */
export function getContentI18nLanguages() {
  return [
    { code: 'zh-CN', nativeName: '简体中文', isDefault: true },
    { code: 'en-US', nativeName: 'English', isDefault: false }
  ]
}