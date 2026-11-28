<template>
  <el-drawer v-model="visible" :title="t('page.workflow.detailTitle')" size="520px" append-to-body destroy-on-close>
    <div v-loading="loading" class="wf-detail">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item :label="t('page.workflow.title')">{{ detail.title || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.workflow.processName')">{{ detail.processName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.workflow.initiator')">{{ detail.initiatorName || detail.initiator || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.status')">
          <el-tag :type="statusType(detail.status)" size="small" effect="plain">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('page.workflow.startTime')">{{ detail.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.workflow.endTime')">{{ detail.endTime || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" :label="t('page.workflow.remark')">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>

      <div class="wf-timeline-title">{{ t('page.workflow.timeline') }}</div>
      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in detail.timeline"
          :key="index"
          :timestamp="item.time || ''"
          placement="top"
          :type="actionType(item.action)"
        >
          <div class="wf-node">
            <span class="wf-node-name">{{ item.nodeName }}</span>
            <el-tag size="small" :type="actionType(item.action)" effect="plain">{{ actionText(item.action) }}</el-tag>
            <span v-if="item.assigneeName" class="text-secondary">{{ item.assigneeName }}</span>
          </div>
          <div v-if="item.comment" class="wf-comment">{{ item.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getInstance } from '@/api/workflow'

/**
 * 流程详情抽屉：发起信息 + 时间线。
 *
 * <p>待办、已办、我发起的三个页面共用；时间线由后端按时间排好，
 * 前端只负责把 action 映射成文案与颜色。</p>
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  processInstanceId: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const loading = ref(false)
const detail = ref({})

watch(
  () => [props.modelValue, props.processInstanceId],
  async ([open, id]) => {
    if (!open || !id) return
    loading.value = true
    try {
      detail.value = (await getInstance(id)) || {}
    } finally {
      loading.value = false
    }
  }
)

const statusText = (status) => {
  const map = {
    running: t('page.workflow.statusRunning'),
    completed: t('page.workflow.statusCompleted'),
    rejected: t('page.workflow.statusRejected'),
    canceled: t('page.workflow.statusCanceled')
  }
  return map[status] || status || '-'
}

const statusType = (status) => {
  const map = { running: 'primary', completed: 'success', rejected: 'danger', canceled: 'info' }
  return map[status] || 'info'
}

const actionText = (action) => {
  const map = {
    发起: t('page.workflow.actionStart'),
    同意: t('page.workflow.actionApprove'),
    驳回: t('page.workflow.actionReject'),
    撤回: t('page.workflow.actionWithdraw'),
    待办理: t('page.workflow.actionPending')
  }
  return map[action] || action
}

const actionType = (action) => {
  const map = { 发起: 'info', 同意: 'success', 驳回: 'danger', 撤回: 'warning', 待办理: 'primary' }
  return map[action] || 'info'
}
</script>

<style scoped>
.wf-detail {
  min-height: 200px;
}

.wf-timeline-title {
  margin: 20px 0 12px;
  font-weight: 600;
}

.wf-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wf-node-name {
  font-weight: 500;
}

.wf-comment {
  margin-top: 6px;
  padding: 6px 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>