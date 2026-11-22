<script setup lang="ts">
interface PageItem {
  key: string
  page?: number
  gap?: boolean
}

const props = defineProps<{
  page: number
  pageSize: number
  total: number | string
  basePath: string
  extraQuery?: Record<string, string | number | undefined | null>
}>()

const totalCount = computed(() => Math.max(0, Number(props.total) || 0))
const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / Math.max(1, props.pageSize))))
const current = computed(() => Math.min(Math.max(1, props.page), totalPages.value))

const items = computed<PageItem[]>(() => {
  const last = totalPages.value
  const pages = new Set<number>([1, last, current.value])
  for (let offset = 1; offset <= 2; offset += 1) {
    pages.add(current.value - offset)
    pages.add(current.value + offset)
  }
  const sorted = [...pages].filter((page) => page >= 1 && page <= last).sort((a, b) => a - b)
  const result: PageItem[] = []
  let previous = 0
  for (const page of sorted) {
    if (previous && page - previous > 1) {
      result.push({ key: `gap-${page}`, gap: true })
    }
    result.push({ key: `page-${page}`, page })
    previous = page
  }
  return result
})

function linkFor(page: number) {
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(props.extraQuery || {})) {
    if (value !== undefined && value !== null && value !== '') query[key] = String(value)
  }
  if (page > 1) query.page = String(page)
  return { path: props.basePath, query }
}
</script>

<template>
  <nav v-if="totalPages > 1" class="pager" aria-label="分页">
    <NuxtLink v-if="current > 1" class="pager-link" :to="linkFor(current - 1)" rel="prev">上一页</NuxtLink>
    <span v-else class="pager-link is-disabled">上一页</span>

    <template v-for="item in items" :key="item.key">
      <span v-if="item.gap" class="pager-gap" aria-hidden="true">…</span>
      <span v-else-if="item.page === current" class="pager-link is-current" aria-current="page">
        {{ item.page }}
      </span>
      <NuxtLink v-else class="pager-link" :to="linkFor(item.page!)">{{ item.page }}</NuxtLink>
    </template>

    <NuxtLink v-if="current < totalPages" class="pager-link" :to="linkFor(current + 1)" rel="next">下一页</NuxtLink>
    <span v-else class="pager-link is-disabled">下一页</span>
  </nav>
</template>
