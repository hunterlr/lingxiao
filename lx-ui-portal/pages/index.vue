<script setup lang="ts">
const api = usePortalApi()
const config = useRuntimeConfig()
const { t } = usePortalI18n()

const { data: site } = await useAsyncData('portal-site', api.fetchSite)
const { data: categories } = await useAsyncData('portal-categories', api.fetchCategories)
const { data: latest } = await useAsyncData('portal-home-latest', () =>
  api.fetchArticles({ pageNum: 1, pageSize: 7 })
)
// 轮播与首页区块是可选的：后台没配就不渲染
const { data: banners } = await useAsyncData('portal-home-banners', () =>
  api.fetchBanners('home').catch(() => [])
)
const { data: homeBlock } = await useAsyncData('portal-home-block', () =>
  api.fetchBlock('home-hero').catch(() => null)
)

const records = computed(() => latest.value?.records || [])
const bannerList = computed(() => banners.value || [])
const hero = computed(() => records.value[0] || null)
const rest = computed(() => records.value.slice(1))
const heroCover = computed(() => mediaUrl(hero.value?.cover, 'medium'))
const heroInitial = computed(() => hero.value?.title?.trim().slice(0, 1) || 'A')
const chips = computed(() => rootCategories(categories.value || []))
const siteUrl = String(config.public.siteUrl).replace(/\/$/, '')

// 首页标题交给 layout 的 titleTemplate 兜底（站点 seoTitle），避免「站点名 · 站点名」重复。
useSeoMeta({
  description: () => site.value?.seoDescription || '',
  keywords: () => site.value?.seoKeywords || '',
  ogUrl: siteUrl,
  ogType: 'website'
})

useHead({
  link: [{ rel: 'canonical', href: siteUrl }]
})
</script>

<template>
  <div class="home">
    <section v-if="bannerList.length" class="banner-strip" :aria-label="t('home.banners')">
      <component
        :is="banner.linkUrl ? 'a' : 'div'"
        v-for="banner in bannerList"
        :key="banner.bannerId"
        :href="banner.linkUrl || undefined"
        class="banner-card"
      >
        <img v-if="banner.image" :src="mediaUrl(banner.image, 'medium')" :alt="banner.title || t('home.bannerAlt')" />
        <span v-else class="banner-fallback">{{ banner.title || t('home.bannerAlt') }}</span>
        <span v-if="banner.title" class="banner-title">{{ banner.title }}</span>
      </component>
    </section>

    <section v-if="homeBlock && homeBlock.content" class="home-block" v-html="homeBlock.content"></section>

    <section v-if="hero" class="hero">
      <div class="hero-copy">
        <p class="eyebrow">{{ t('home.latest') }}</p>
        <h1 class="hero-title">
          <NuxtLink :to="`/article/${hero.articleId}`">{{ hero.title }}</NuxtLink>
        </h1>
        <p v-if="hero.summary" class="hero-summary">{{ hero.summary }}</p>
        <p class="hero-meta">
          <NuxtLink
            v-if="hero.categoryKey"
            :to="`/category/${hero.categoryKey}`"
            class="meta-link"
          >
            {{ hero.categoryName }}
          </NuxtLink>
          <time v-if="hero.publishTime" :datetime="hero.publishTime">{{ formatDate(hero.publishTime) }}</time>
          <span v-if="hero.viewCount != null">{{ t('common.readCount', { count: formatCount(hero.viewCount) }) }}</span>
        </p>
        <NuxtLink class="button" :to="`/article/${hero.articleId}`">{{ t('home.readMore') }}</NuxtLink>
      </div>

      <NuxtLink class="hero-media" :to="`/article/${hero.articleId}`" :aria-label="hero.title">
        <img v-if="heroCover" :src="heroCover" :alt="hero.title" />
        <span v-else class="cover-placeholder" aria-hidden="true">{{ heroInitial }}</span>
      </NuxtLink>
    </section>

    <section v-if="chips.length" class="category-strip" :aria-label="t('home.categoryNav')">
      <NuxtLink
        v-for="item in chips"
        :key="item.categoryId"
        :to="`/category/${item.categoryKey}`"
        class="category-chip"
      >
        <span class="chip-name">{{ item.categoryName }}</span>
        <span class="chip-arrow" aria-hidden="true">→</span>
      </NuxtLink>
    </section>

    <section v-if="rest.length" class="section">
      <div class="section-head">
        <h2 class="section-title">{{ t('home.latestPosts') }}</h2>
        <p class="section-sub">{{ t('home.sortedByPublish') }}</p>
      </div>
      <div class="article-grid">
        <ArticleCard v-for="article in rest" :key="article.articleId" :article="article" />
      </div>
    </section>

    <EmptyState v-if="!records.length">
      <template #title>{{ t('home.empty.title') }}</template>
      <template #description>{{ t('home.empty.desc') }}</template>
    </EmptyState>
  </div>
</template>

<style scoped>
.banner-strip {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(280px, 1fr);
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
  margin-bottom: 24px;
}

.banner-card {
  position: relative;
  display: block;
  min-height: 160px;
  border-radius: 14px;
  overflow: hidden;
  background: var(--lx-surface, #f2f4f8);
  color: inherit;
  text-decoration: none;
}

.banner-card img {
  width: 100%;
  height: 180px;
  object-fit: cover;
  display: block;
}

.banner-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 180px;
  font-weight: 600;
}

.banner-title {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 14px;
  color: #fff;
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, 0.6));
}

.home-block {
  margin-bottom: 24px;
}
</style>
