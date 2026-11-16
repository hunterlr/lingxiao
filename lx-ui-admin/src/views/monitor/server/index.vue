<template>
  <div class="page-container">
    <div class="page-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <h3 class="section-title" style="margin: 0">运行状态</h3>
          <span v-if="data" class="text-secondary">数据截至 {{ data.sampleTime }}</span>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <div class="metric-card">
              <div class="metric-head">
                <span>CPU 使用率</span>
                <span class="text-secondary">{{ cpuText }}</span>
              </div>
              <el-progress
                :percentage="percentOf(cpuPercent)"
                :color="barColor"
                :stroke-width="12"
                :format="(p) => `${p}%`"
              />
              <div class="metric-foot text-secondary">
                {{ cores }} 核 · 进程 {{ valueOrUnavailable(data?.cpu?.processUsage) }}% ·
                负载 {{ loadAverageText }}
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="metric-card">
              <div class="metric-head">
                <span>物理内存</span>
                <span class="text-secondary">{{ bytes(data?.memory?.used) }} / {{ bytes(data?.memory?.total) }}</span>
              </div>
              <el-progress
                :percentage="percentOf(data?.memory?.usage)"
                :color="barColor"
                :stroke-width="12"
                :format="(p) => `${p}%`"
              />
              <div class="metric-foot text-secondary">
                交换区 {{ bytes(data?.memory?.swapUsed) }} / {{ bytes(data?.memory?.swapTotal) }}
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="metric-card">
              <div class="metric-head">
                <span>JVM 堆内存</span>
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
                非堆 {{ bytes(data?.jvm?.nonHeapUsed) }} · 线程 {{ data?.jvm?.threadCount ?? '-' }}
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>

    <div class="page-card">
      <h3 class="section-title">系统信息</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作系统">{{ data?.system?.osName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ data?.system?.osVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="架构">{{ data?.system?.osArch || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主机名">{{ data?.system?.hostName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="逻辑核数">{{ data?.system?.availableProcessors ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="运行目录" :span="1">
          <span class="text-mono">{{ data?.system?.userDir || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card">
      <h3 class="section-title">JVM 信息</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="虚拟机">{{ data?.jvm?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ data?.jvm?.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="厂商">{{ data?.jvm?.vendor || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Java 版本">{{ data?.jvm?.javaVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="启动时间">{{ formatTime(data?.jvm?.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="已运行">{{ uptime(data?.jvm?.uptime) }}</el-descriptions-item>
        <el-descriptions-item label="堆上限">{{ bytes(data?.jvm?.heapMax) }}</el-descriptions-item>
        <el-descriptions-item label="已加载类">{{ data?.jvm?.loadedClassCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="启动参数" :span="2">
          <span class="text-mono">{{ data?.jvm?.inputArguments || '（无）' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Java Home" :span="2">
          <span class="text-mono">{{ data?.jvm?.javaHome || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card">
      <h3 class="section-title">磁盘</h3>
      <el-table :data="data?.disks || []" border stripe>
        <el-table-column prop="path" label="挂载点" width="120" />
        <el-table-column prop="type" label="文件系统" width="120" />
        <el-table-column label="总容量" width="120" align="right">
          <template #default="{ row }">{{ bytes(row.total) }}</template>
        </el-table-column>
        <el-table-column label="已用" width="120" align="right">
          <template #default="{ row }">{{ bytes(row.used) }}</template>
        </el-table-column>
        <el-table-column label="可用" width="120" align="right">
          <template #default="{ row }">{{ bytes(row.usable) }}</template>
        </el-table-column>
        <el-table-column label="使用率" min-width="220">
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

/**
 * 服务监控（/monitor/server）。
 *
 * <p>所有指标来自 JDK MXBean 与 FileStore，刷新即实时采样。
 * CPU 某些环境取不到时后端返回 -1，这里显示“不可用”，不伪装成 0。</p>
 *
 * <p>数字格式化全部在前端做：后端只返回字节数与百分数，避免把
 * “GB / MB”这种展示口径固化到接口里。</p>
 */
const loading = ref(false)
const data = ref(null)

const cpuPercent = computed(() => Number(data.value?.cpu?.systemUsage ?? -1))
const cpuText = computed(() =>
  cpuPercent.value < 0 ? '不可用' : `${percentOf(cpuPercent.value)}%`
)
const cores = computed(() => data.value?.cpu?.cores ?? '-')
const loadAverageText = computed(() => {
  const value = Number(data.value?.cpu?.loadAverage)
  return value < 0 ? '不可用' : value.toFixed(2)
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
  return Number.isFinite(number) && number >= 0 ? number.toFixed(2) : '不可用'
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
  if (days) parts.push(`${days} 天`)
  if (hours) parts.push(`${hours} 小时`)
  parts.push(`${minutes} 分`)
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
