<script setup lang="ts">
const route = useRoute()
const api = usePortalApi()
const { profile, isLoggedIn, ensureProfile, unreadCount, refreshUnread } = useMemberAuth()
const { t, lang, loadPack, setLang, languages } = usePortalI18n()

const { data: site } = await useAsyncData('portal-site', api.fetchSite)
const { data: categories } = await useAsyncData('portal-categories', api.fetchCategories)
// 导航是可选配置：没有导航或 CMS 关闭时回退到一级栏目
const { data: nav } = await useAsyncData('portal-nav-main', () => api.fetchNav('main').catch(() => null))
// 有 Token 才请求会员资料；失败不会阻断内容页
await ensureProfile()
// 已登录时顺带取未读数，角标首屏就有；未登录不发请求
if (isLoggedIn.value) {
  await refreshUnread()
}
// 文案：先拉后台已发布语言包，失败回落内置字典
await loadPack()
// 客户端登录 / 退出后同步角标，避免要刷新整页才出现
onMounted(() => {
  if (isLoggedIn.value) refreshUnread()
})
watch(isLoggedIn, (value) => {
  if (value) refreshUnread()
  else unreadCount.value = 0
})

const siteName = computed(() => site.value?.siteName || '凌霄门户')
const theme = computed(() => site.value?.theme || 'default')
const logo = computed(() => mediaUrl(site.value?.logo))
const navItems = computed(() => {
  const items = nav.value?.items || []
  if (items.length) {
    return items.map((item) => ({
      key: `nav-${item.itemId}`,
      label: item.itemName,
      url: item.url
    }))
  }
  return rootCategories(categories.value || []).slice(0, 6).map((item) => ({
    key: `category-${item.categoryId}`,
    label: item.categoryName,
    url: `/category/${item.categoryKey}`
  }))
})
const year = new Date().getFullYear()

const memberName = computed(() => profile.value?.nickname || profile.value?.username || t('layout.member.account'))
const memberAvatar = computed(() => mediaUrl(profile.value?.avatar, 'thumb'))
const memberInitial = computed(() => (memberName.value || 'M').slice(0, 1))

const keyword = ref(route.path === '/search' ? String(route.query.keyword || '') : '')
watch(
  () => route.query.keyword,
  (value) => {
    if (route.path === '/search') keyword.value = String(value || '')
  }
)

function submitSearch() {
  const text = keyword.value.trim()
  if (!text) return
  return navigateTo({ path: '/search', query: { keyword: text } })
}

useHead(() => ({
  titleTemplate: (titleChunk) =>
    titleChunk ? `${titleChunk} · ${siteName.value}` : site.value?.seoTitle || siteName.value,
  htmlAttrs: { 'data-theme': theme.value, lang: lang.value }
}))

useSeoMeta({
  description: () => site.value?.seoDescription || '',
  keywords: () => site.value?.seoKeywords || '',
  ogSiteName: () => siteName.value,
  ogLocale: () => (lang.value === 'en-US' ? 'en_US' : 'zh_CN')
})
</script>

<template>
  <div class="portal-shell" :data-theme="theme">
    <a class="skip-link" href="#main">{{ t('layout.skip') }}</a>

    <header class="site-header">
      <div class="container header-inner">
        <NuxtLink to="/" class="brand" :aria-label="siteName">
          <img v-if="logo" :src="logo" :alt="siteName" class="brand-logo" />
          <span v-else class="brand-mark" aria-hidden="true">凌</span>
          <span class="brand-text">
            <strong>{{ siteName }}</strong>
            <span class="brand-sub">LINGXIAO PORTAL</span>
          </span>
        </NuxtLink>

        <nav class="site-nav" :aria-label="t('layout.nav.label')">
          <NuxtLink to="/" class="nav-link" :class="{ 'is-active': route.path === '/' }">{{ t('common.home') }}</NuxtLink>
          <NuxtLink
            v-for="item in navItems"
            :key="item.key"
            :to="item.url"
            class="nav-link"
            :class="{ 'is-active': route.path === item.url || route.path.startsWith(`${item.url}/`) }"
          >
            {{ item.label }}
          </NuxtLink>
        </nav>

        <form class="header-search" role="search" @submit.prevent="submitSearch">
          <input
            v-model="keyword"
            type="search"
            name="keyword"
            :placeholder="t('layout.searchPlaceholder')"
            :aria-label="t('layout.searchPlaceholder')"
          />
          <button type="submit" :aria-label="t('layout.searchSubmit')">{{ t('layout.searchSubmit') }}</button>
        </form>

        <div class="lang-switch" role="group" :aria-label="t('layout.lang.label')">
          <button
            v-for="item in languages"
            :key="item.code"
            type="button"
            class="lang-button"
            :class="{ 'is-active': item.code === lang }"
            @click="setLang(item.code)"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="member-actions">
          <NuxtLink
            v-if="isLoggedIn"
            to="/messages"
            class="member-bell"
            :class="{ 'is-active': route.path === '/messages' }"
            :aria-label="unreadCount ? t('layout.member.unread', { count: unreadCount }) : t('layout.member.messages')"
          >
            <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true" focusable="false">
              <path
                d="M3 6.5A1.5 1.5 0 0 1 4.5 5h15A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-11Zm1.9-.5 7.1 5.4L19.1 6H4.9Z"
                fill="currentColor"
              />
            </svg>
            <span v-if="unreadCount > 0" class="member-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </NuxtLink>
          <NuxtLink v-if="isLoggedIn" to="/account" class="member-chip" :aria-label="`${t('layout.member.account')} · ${memberName}`">
            <img
              v-if="memberAvatar"
              :src="memberAvatar"
              :alt="memberName"
              class="member-avatar"
              referrerpolicy="no-referrer"
            />
            <span v-else class="member-avatar member-avatar-text" aria-hidden="true">{{ memberInitial }}</span>
            <span class="member-name">{{ memberName }}</span>
          </NuxtLink>
          <template v-else>
            <NuxtLink to="/login" class="member-login" :class="{ 'is-active': route.path === '/login' }">{{ t('layout.member.login') }}</NuxtLink>
            <NuxtLink to="/register" class="member-register">{{ t('layout.member.register') }}</NuxtLink>
          </template>
        </div>
      </div>
    </header>

    <main id="main" class="container page-main">
      <slot />
    </main>

    <footer class="site-footer">
      <div class="container footer-inner">
        <div class="footer-brand">
          <strong>{{ siteName }}</strong>
          <p>{{ site?.seoDescription || t('layout.footer.tagline') }}</p>
        </div>
        <nav class="footer-links" :aria-label="t('layout.footer.nav')">
          <NuxtLink to="/">{{ t('common.home') }}</NuxtLink>
          <NuxtLink v-for="item in navItems" :key="item.key" :to="item.url">
            {{ item.label }}
          </NuxtLink>
        </nav>
        <p class="footer-copy">{{ t('layout.footer.copy', { year, site: siteName }) }}</p>
      </div>
    </footer>
  </div>
</template>
