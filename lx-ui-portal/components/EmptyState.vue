<template>
  <div class="empty-state">
    <span class="empty-mark" aria-hidden="true">◇</span>
    <p class="empty-title"><slot name="title">暂无内容</slot></p>
    <p v-if="$slots.description" class="empty-description">
      <slot name="description" />
    </p>
    <slot />
  </div>
</template>
