<script setup lang="ts">
const api = usePortalApi()
const config = useRuntimeConfig()

const { data: site } = await useAsyncData('portal-site', api.fetchSite)
const { data: categories } = await useAsyncData('portal-categories', api.fetchCategories)
const { data: latest } = await useAsyncData('portal-home-latest', () =>
  api.fetchArticles({ pageNum: 1, pageSize: 7 })
)

const records = computed(() => latest.value?.records || [])
const hero = computed(() => records.value[0] || null)
const rest = computed(() => records.value.slice(1))
const heroCover = computed(() => mediaUrl(hero.value?.cover, 'medium'))
const heroInitial = computed(() => hero.value?.title?.trim().slice(0, 1) || '文')
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
    <section v-if="hero" class="hero">
      <div class="hero-copy">
        <p class="eyebrow">最新发布</p>
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
          <span v-if="hero.viewCount != null">阅读 {{ formatCount(hero.viewCount) }}</span>
        </p>
        <NuxtLink class="button" :to="`/article/${hero.articleId}`">阅读全文</NuxtLink>
      </div>

      <NuxtLink class="hero-media" :to="`/article/${hero.articleId}`" :aria-label="hero.title">
        <img v-if="heroCover" :src="heroCover" :alt="hero.title" />
        <span v-else class="cover-placeholder" aria-hidden="true">{{ heroInitial }}</span>
      </NuxtLink>
    </section>

    <section v-if="chips.length" class="category-strip" aria-label="栏目导航">
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
        <h2 class="section-title">最新文章</h2>
        <p class="section-sub">按发布时间排序</p>
      </div>
      <div class="article-grid">
        <ArticleCard v-for="article in rest" :key="article.articleId" :article="article" />
      </div>
    </section>

    <EmptyState v-if="!records.length">
      <template #title>还没有已发布内容</template>
      <template #description>在后台「CMS → 文章」发布文章后，这里会自动出现。</template>
    </EmptyState>
  </div>
</template>
