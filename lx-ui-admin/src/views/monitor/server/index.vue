<template>
  <div class="page-container">
    <div class="page-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <h3 class="section-title" style="margin: 0">{{ t('page.server.running') }}</h3>
          <span v-if="data" class="text-secondary">{{ t('page.server.dataAsOf', { time: data.sampleTime }) }}</span>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="load">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <div class="metric-card">
              <div class="metric-head">
                <span>{{ t('page.server.cpuUsage') }}</span>
                <span class="text-secondary">{{ cpuText }}</span>
              </div>
              <el-progress
                :percentage="percentOf(cpuPercent)"
                :color="barColor"
                :stroke-width="12"
                :format="(p) => `${p}%`"
              />
              <div class="metric-foot text-secondary">
                {{ t('page.server.coresProcessLoad', { cores, process: valueOrUnavailable(data?.cpu?.processUsage), load: loadAverageText }) }}
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="metric-card">
              <div class="metric-head">
                <span>{{ t('page.server.physicalMemory') }}</span>
                <span class="text-secondary">{{ bytes(data?.memory?.used) }} / {{ bytes(data?.memory?.total) }}</span>
              </div>
              <el-progress
                :percentage="percentOf(data?.memory?.usage)"
                :color="barColor"
                :stroke-width="12"
                :format="(p) => `${p}%`"
              />
              <div class="metric-foot text-secondary">
                {{ t('page.server.swap', { used: bytes(data?.memory?.swapUsed), total: bytes(data?.memory?.swapTotal) }) }}
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="metric-card">
              <div class="metric-head">
                <span>{{ t('page.server.jvmHeap') }}</span>
                <span class="text-secondary">
                  {{ bytes(data?.jvm?.heapUsed) }} / {{ bytes(data?.jvm?.heapMax) }}
                </span>
              </div>
              <el-progress
                :percentage="percentOf(data?.jvm?.heapUsage)"
                :color="barColor"
                :stroke-width="12"
                :format="(p) => `${p}%`"
              />
              <div class="metric-foot text-secondary">
                {{ t('page.server.nonHeapThread', { nonHeap: bytes(data?.jvm?.nonHeapUsed), threads: data?.jvm?.threadCount ?? '-' }) }}
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>

    <div class="page-card">
      <h3 class="section-title">{{ t('page.server.systemInfo') }}</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('page.server.os')">{{ data?.system?.osName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.osVersion')">{{ data?.system?.osVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.arch')">{{ data?.system?.osArch || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.hostname')">{{ data?.system?.hostName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.logicalCores')">{{ data?.system?.availableProcessors ?? '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.workDir')" :span="1">
          <span class="text-mono">{{ data?.system?.userDir || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card">
      <h3 class="section-title">{{ t('page.server.jvmInfo') }}</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('page.server.vm')">{{ data?.jvm?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.osVersion')">{{ data?.jvm?.version || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.vendor')">{{ data?.jvm?.vendor || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.javaVersion')">{{ data?.jvm?.javaVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.startTime')">{{ formatTime(data?.jvm?.startTime) }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.uptime')">{{ uptime(data?.jvm?.uptime) }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.heapMax')">{{ bytes(data?.jvm?.heapMax) }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.loadedClasses')">{{ data?.jvm?.loadedClassCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('page.server.startArgs')" :span="2">
          <span class="text-mono">{{ data?.jvm?.inputArguments || t('page.server.none') }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Java Home" :span="2">
          <span class="text-mono">{{ data?.jvm?.javaHome || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card">
      <h3 class="section-title">{{ t('page.server.disks') }}</h3>
      <el-table :data="data?.disks || []" border stripe>
        <el-table-column prop="path" :label="t('page.server.mountPoint')" width="120" />
        <el-table-column prop="type" :label="t('page.server.fileSystem')" width="120" />
        <el-table-column :label="t('page.server.totalCapacity')" width="120" align="right">
          <template #default="{ row }">{{ bytes(row.total) }}</template>
        </el-table-column>
        <el-table-column :label="t('page.server.used')" width="120" align="right">
          <template #default="{ row }">{{ bytes(row.used) }}</template>
        </el-table-column>
        <el-table-column :label="t('page.server.available')" width="120" align="right">
          <template #default="{ row }">{{ bytes(row.usable) }}</template>
        </el-table-column>
        <el-table-column :label="t('page.server.usage')" min-width="220">
          <template #default="{ row }">
            <el-progress
              :percentage="percentOf(row.usage)"
              :color="barColor"
              :stroke-width="10"
              :format="(p) => `${p}%`"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getServerInfo } from '@/api/monitor'
import { useI18n } from 'vue-i18n'

/**
 * 服务监控（/monitor/server）。
 *
 * <p>所有指标来自 JDK MXBean 与 FileStore，刷新即实时采样。
 * CPU 某些环境取不到时后端返回 -1，这里显示“不可用”，不伪装成 0。</p>
 *
 * <p>数字格式化全部在前端做：后端只返回字节数与百分数，避免把
 * “GB / MB”这种展示口径固化到接口里。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const data = ref(null)

const cpuPercent = computed(() => Number(data.value?.cpu?.systemUsage ?? -1))
const cpuText = computed(() =>
  cpuPercent.value < 0 ? t('common.unavailable') : `${percentOf(cpuPercent.value)}%`
)
const cores = computed(() => data.value?.cpu?.cores ?? '-')
const loadAverageText = computed(() => {
  const value = Number(data.value?.cpu?.loadAverage)
  return value < 0 ? t('common.unavailable') : value.toFixed(2)
})

async function load() {
  loading.value = true
  try {
    data.value = await getServerInfo()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function percentOf(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0) return 0
  return Math.min(Math.round(number * 100) / 100, 100)
}

function valueOrUnavailable(value) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number.toFixed(2) : t('common.unavailable')
}

function barColor(percentage) {
  if (percentage >= 85) return '#ef4444'
  if (percentage >= 60) return '#f59e0b'
  return '#2f6fed'
}

const UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']

function bytes(value) {
  let number = Number(value)
  if (!Number.isFinite(number) || number <= 0) return '0 B'
  let index = 0
  while (number >= 1024 && index < UNITS.length - 1) {
    number /= 1024
    index++
  }
  return `${index === 0 ? number : number.toFixed(1)} ${UNITS[index]}`
}

function uptime(seconds) {
  let value = Number(seconds)
  if (!Number.isFinite(value) || value <= 0) return '-'
  const days = Math.floor(value / 86400)
  value %= 86400
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  const parts = []
  if (days) parts.push(t('page.server.days', { days }))
  if (hours) parts.push(t('page.server.hours', { hours }))
  parts.push(t('page.server.minutes', { minutes }))
  return parts.join(' ')
}

/** 后端返回毫秒时间戳（Long 会序列化成字符串），这里转本地时间。 */
function formatTime(epochMilli) {
  const value = Number(epochMilli)
  if (!Number.isFinite(value) || value <= 0) return '-'
  const date = new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<style scoped>
.metric-card {
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  padding: 14px 16px;
  background: var(--lx-panel-muted);
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  font-weight: 600;
}

.metric-foot {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 767px) {
  .metric-card {
    margin-bottom: 12px;
  }
}
</style>
