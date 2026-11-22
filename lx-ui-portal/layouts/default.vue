<script setup lang="ts">
const route = useRoute()
const { fetchSite, fetchCategories } = usePortalApi()

const { data: site } = await useAsyncData('portal-site', fetchSite)
const { data: categories } = await useAsyncData('portal-categories', fetchCategories)

const siteName = computed(() => site.value?.siteName || '凌霄门户')
const logo = computed(() => mediaUrl(site.value?.logo))
const navItems = computed(() => rootCategories(categories.value || []).slice(0, 6))
const year = new Date().getFullYear()

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

useHead({
  titleTemplate: (titleChunk) =>
    titleChunk ? `${titleChunk} · ${siteName.value}` : site.value?.seoTitle || siteName.value
})

useSeoMeta({
  description: () => site.value?.seoDescription || '',
  keywords: () => site.value?.seoKeywords || '',
  ogSiteName: () => siteName.value,
  ogLocale: 'zh_CN'
})
</script>

<template>
  <div class="portal-shell">
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
            :key="item.categoryId"
            :to="`/category/${item.categoryKey}`"
            class="nav-link"
            :class="{ 'is-active': route.path === `/category/${item.categoryKey}` }"
          >
            {{ item.categoryName }}
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
          <NuxtLink v-for="item in navItems" :key="item.categoryId" :to="`/category/${item.categoryKey}`">
            {{ item.categoryName }}
          </NuxtLink>
        </nav>
        <p class="footer-copy">© {{ year }} {{ siteName }} · Powered by 凌霄 LingXiao</p>
      </div>
    </footer>
  </div>
</template>
