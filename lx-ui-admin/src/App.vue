<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
/**
 * 应用根：只负责路由出口 + Element Plus 语言联动。
 *
 * <p>为什么用 {@code ElConfigProvider} 而不是 {@code app.use(ElementPlus, { locale })}：
 * 后者在启动时把语言写死，切换语言后分页、日期、空状态仍是旧语言。
 * 用 ConfigProvider 把 Element Plus 的 locale 绑定到响应式变量，
 * 语言一换，组件文案立即跟着变。</p>
 *
 * <p>布局（侧边栏 / 顶栏）在 layout/index.vue，登录页、错误页各自独立。</p>
 */
import { computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import i18n from '@/i18n'
import { useAppStore } from '@/store/app'
import { useLocaleStore } from '@/store/locale'
import { appName } from '@/utils/brand'

const router = useRouter()
const localeStore = useLocaleStore()
const appStore = useAppStore()

const elementLocale = computed(() => (localeStore.isEnglish ? en : zhCn))

/** 品牌名优先取后端配置；接口没回来时用语言包兜底。 */
function brand() {
  return appName() || i18n.global.t('layout.brand')
}

function syncTitle() {
  const { t, te } = i18n.global
  const meta = router.currentRoute.value.meta || {}
  const title = meta.titleKey && te(meta.titleKey) ? t(meta.titleKey) : meta.title
  document.title = title ? `${title} · ${brand()}` : brand()
}

/**
 * 切换语言、或品牌配置（异步）到达时同步浏览器标签页标题。
 *
 * <p>路由守卫的 afterEach 只在导航时跑；语言切换、接口返回都不触发导航，
 * 不补这个 watch，标题会一直停在上一种语言 / 旧名称。</p>
 */
watch([() => localeStore.locale, () => appStore.profile], syncTitle, { deep: true })
</script>
