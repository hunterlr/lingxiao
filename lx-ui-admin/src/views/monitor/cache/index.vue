<template>
  <div class="page-container">
    <div class="page-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <h3 class="section-title" style="margin: 0">{{ t('page.cache.title') }}</h3>
          <el-tag :type="data?.redisEnabled ? 'success' : 'info'" effect="plain">
            {{ data?.redisEnabled ? t('page.cache.redisEnabled') : t('page.cache.localCache') }}
          </el-tag>
          <span v-if="data" class="text-secondary">{{ t('page.cache.captchaStore', { mode: data.storeMode }) }}</span>
        </div>
        <div class="toolbar-right">
          <span v-if="data" class="text-secondary">{{ t('page.server.dataAsOf', { time: data.sampleTime }) }}</span>
          <el-button :icon="Refresh" @click="load">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <el-alert
          v-if="data?.note"
          :title="data.note"
          :type="data.redisEnabled ? 'success' : 'info'"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        />
        <el-alert
          v-for="(warning, index) in data?.warnings || []"
          :key="index"
          :title="warning"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        />

        <!-- 启用 Redis：展示 INFO 摘要 -->
        <template v-if="data?.redisEnabled && data?.redis">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="6">
              <div class="metric-card">
                <div class="metric-head">
                  <span>{{ t('page.cache.memoryUsage') }}</span>
                  <span class="text-secondary">
                    {{ data.redis.usedMemoryHuman || bytes(data.redis.usedMemory) }}
                  </span>
                </div>
                <el-progress
                  :percentage="clampPercent(data.redis.memoryUsage)"
                  :color="barColor"
                  :stroke-width="12"
                  :format="(p) => `${p}%`"
                />
                <div class="metric-foot text-secondary">
                  {{ t('page.cache.maxMemory', { value: data.redis.maxMemoryHuman || t('page.cache.notSet') }) }}
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="6">
              <div class="metric-card">
                <div class="metric-head">
                  <span>{{ t('page.cache.hitRate') }}</span>
                  <span class="text-secondary">{{ clampPercent(data.redis.hitRate) }}%</span>
                </div>
                <el-progress
                  :percentage="clampPercent(data.redis.hitRate)"
                  :color="barColor"
                  :stroke-width="12"
                  :format="(p) => `${p}%`"
                />
                <div class="metric-foot text-secondary">
                  {{ t('page.cache.hitMiss', { hit: data.redis.keyspaceHits, miss: data.redis.keyspaceMisses }) }}
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="6">
              <div class="metric-card">
                <div class="metric-head">
                  <span>{{ t('page.cache.clients') }}</span>
                  <span class="text-secondary">{{ data.redis.connectedClients }}</span>
                </div>
                <div class="metric-big">{{ data.redis.connectedClients }}</div>
                <div class="metric-foot text-secondary">
                  {{ t('page.cache.totalConnections', { total: data.redis.totalConnectionsReceived, rejected: data.redis.rejectedConnections }) }}
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="6">
              <div class="metric-card">
                <div class="metric-head">
                  <span>{{ t('page.cache.uptime') }}</span>
                  <span class="text-secondary">{{ data.redis.uptimeHuman }}</span>
                </div>
                <div class="metric-big">{{ data.redis.uptimeHuman }}</div>
                <div class="metric-foot text-secondary">
                  {{ t('page.cache.totalCommands', { total: data.redis.totalCommandsProcessed }) }}
                </div>
              </div>
            </el-col>
          </el-row>

          <el-descriptions :column="3" border style="margin-top: 16px">
            <el-descriptions-item :label="t('page.cache.redisVersion')">{{ data.redis.version || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.mode')">{{ data.redis.mode || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.port')">{{ data.redis.port || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.server.os')" :span="2">{{ data.redis.os || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.expiringEvicted')">
              {{ data.redis.expiredKeys }} / {{ data.redis.evictedKeys }}
            </el-descriptions-item>
          </el-descriptions>

          <h3 class="section-title" style="margin-top: 20px">{{ t('page.cache.keyspaces') }}</h3>
          <el-table :data="data.redis.keyspaces" border stripe>
            <el-table-column prop="db" :label="t('page.cache.db')" width="100" />
            <el-table-column prop="keys" :label="t('page.cache.keyCount')" width="140" align="right" />
            <el-table-column prop="expires" :label="t('page.cache.withExpiry')" width="160" align="right" />
            <el-table-column prop="avgTtl" :label="t('page.cache.avgTtl')" align="right" />
          </el-table>

          <h3 class="section-title" style="margin-top: 20px">
            {{ t('page.cache.commandStats') }}
            <span class="text-secondary" style="font-size: 12px; font-weight: 400">
              {{ t('page.cache.top20') }}
            </span>
          </h3>
          <el-table :data="(data.redis.commandStats || []).slice(0, 20)" border stripe>
            <el-table-column prop="command" :label="t('page.cache.command')" width="140" />
            <el-table-column prop="calls" :label="t('page.cache.calls')" width="160" align="right" />
            <el-table-column prop="usec" :label="t('page.cache.totalUsec')" width="180" align="right" />
            <el-table-column :label="t('page.cache.avgUsec')" align="right">
              <template #default="{ row }">{{ Number(row.usecPerCall || 0).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 未启用 Redis：展示本地 Caffeine 统计 -->
        <template v-else>
          <el-descriptions :column="3" border>
            <el-descriptions-item :label="t('page.cache.localImpl')">{{ local.mode || data?.storeMode || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.currentEntries')">{{ local.size ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.maxSize')">{{ local.maxSize ?? '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.hits')">{{ local.hitCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.misses')">{{ local.missCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.hitRate')">{{ ratioPercent(local.hitRate) }}%</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.evictions')">{{ local.evictionCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.expireSeconds')">{{ local.expireSeconds ?? '-' }}</el-descriptions-item>
            <el-descriptions-item :label="t('page.cache.loadCount')">{{ local.loadCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>

          <el-alert
            :title="t('page.cache.singleNodeWarning')"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 16px"
          />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getCacheInfo } from '@/api/monitor'
import { useI18n } from 'vue-i18n'

/**
 * 缓存监控（/monitor/cache）。
 *
 * <p>页面只有两种形态，由后端 {@code redisEnabled} 决定：</p>
 * <ul>
 *   <li>未启用 Redis：展示本机 Caffeine 的条目数 / 命中率 / 淘汰数，并提示单机限制；</li>
 *   <li>已启用 Redis：展示内存、命中率、连接数、运行时长、逻辑库与命令统计。</li>
 * </ul>
 *
 * <p>后端读取 Redis 失败时会把原因放进 {@code warnings} 并降级返回，
 * 页面照常渲染，不会出现整页错误。</p>
 */
const { t } = useI18n()

const loading = ref(false)
const data = ref(null)

const local = computed(() => data.value?.captcha || {})

async function load() {
  loading.value = true
  try {
    data.value = await getCacheInfo()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function clampPercent(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0) return 0
  return Math.min(Math.round(number * 100) / 100, 100)
}

/** Caffeine 的 hitRate 是 0~1，页面统一按百分比展示。 */
function ratioPercent(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0) return '0.00'
  return (Math.min(number, 1) * 100).toFixed(2)
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
</script>

<style scoped>
.metric-card {
  border: 1px solid var(--lx-border);
  border-radius: var(--lx-radius);
  padding: 14px 16px;
  background: var(--lx-panel-muted);
  min-height: 118px;
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  font-weight: 600;
}

.metric-big {
  font-size: 24px;
  font-weight: 600;
  color: var(--lx-text);
  line-height: 1.3;
  word-break: break-all;
}

.metric-foot {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 767px) {
  .metric-card {
    margin-bottom: 12px;
  }
}
</style>
