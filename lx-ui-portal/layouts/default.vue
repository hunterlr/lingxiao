<script setup lang="ts">
const route = useRoute()
const api = usePortalApi()
const { profile, isLoggedIn, ensureProfile } = useMemberAuth()

const { data: site } = await useAsyncData('portal-site', api.fetchSite)
const { data: categories } = await useAsyncData('portal-categories', api.fetchCategories)
// 导航是可选配置：没有导航或 CMS 关闭时回退到一级栏目
const { data: nav } = await useAsyncData('portal-nav-main', () => api.fetchNav('main').catch(() => null))
// 有 Token 才请求会员资料；失败不会阻断内容页
await ensureProfile()

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

const memberName = computed(() => profile.value?.nickname || profile.value?.username || '个人中心')
const memberAvatar = computed(() => mediaUrl(profile.value?.avatar, 'thumb'))
const memberInitial = computed(() => (memberName.value || '会').slice(0, 1))

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
  htmlAttrs: { 'data-theme': theme.value }
}))

useSeoMeta({
  description: () => site.value?.seoDescription || '',
  keywords: () => site.value?.seoKeywords || '',
  ogSiteName: () => siteName.value,
  ogLocale: 'zh_CN'
})
</script>

<template>
  <div class="portal-shell" :data-theme="theme">
    <a class="skip-link" href="#main">跳到主要内容</a>

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

        <nav class="site-nav" aria-label="主导航">
          <NuxtLink to="/" class="nav-link" :class="{ 'is-active': route.path === '/' }">首页</NuxtLink>
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
            placeholder="搜索文章"
            aria-label="搜索文章"
          />
          <button type="submit" aria-label="提交搜索">搜索</button>
        </form>

        <div class="member-actions">
          <NuxtLink v-if="isLoggedIn" to="/account" class="member-chip" :aria-label="`个人中心：${memberName}`">
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
            <NuxtLink to="/login" class="member-login" :class="{ 'is-active': route.path === '/login' }">登录</NuxtLink>
            <NuxtLink to="/register" class="member-register">注册</NuxtLink>
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
          <p>{{ site?.seoDescription || '内容由凌霄 CMS 驱动，门户只读取已发布内容。' }}</p>
        </div>
        <nav class="footer-links" aria-label="页脚导航">
          <NuxtLink to="/">首页</NuxtLink>
          <NuxtLink v-for="item in navItems" :key="item.key" :to="item.url">
            {{ item.label }}
          </NuxtLink>
        </nav>
        <p class="footer-copy">© {{ year }} {{ siteName }} · Powered by 凌霄 LingXiao</p>
      </div>
    </footer>
  </div>
</template>
