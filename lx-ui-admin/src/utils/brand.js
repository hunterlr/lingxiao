import { useAppStore } from '@/store/app'
import { useLocaleStore } from '@/store/locale'

/**
 * 品牌名 / 版本号的读取入口。
 *
 * <p>唯一真源在后端 {@code application.yml} 的 {@code lx.app.*}，
 * 前端启动时通过 {@code /api/admin/app/profile} 拉到 Pinia 里，
 * 组件只调这里的函数，不再写死“凌霄”“v1.0”。</p>
 *
 * <p>接口没返回时（后端旧版本 / 请求失败）返回空串，调用方用语言包兜底；
 * 兜底只为不闪空白，不是第二个可维护的真源。</p>
 */

/** 当前语言下的应用名；中英文缺失时互相兜底。 */
export function appName() {
  const { profile } = useAppStore()
  const { isEnglish } = useLocaleStore()
  const zh = (profile.name || '').trim()
  const en = (profile.nameEn || '').trim()
  return isEnglish ? en || zh : zh || en
}

/** 展示用版本号，如 1.0.0；没有配置时返回空串。 */
export function appVersion() {
  return (useAppStore().profile.version || '').trim()
}
