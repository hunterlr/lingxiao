<template>
  <div class="account-page">
    <aside v-if="profile" class="profile-card">
      <div class="profile-head">
        <img
          v-if="avatarUrl"
          :src="avatarUrl"
          :alt="displayName"
          class="profile-avatar"
        />
        <span v-else class="profile-avatar profile-avatar-text" aria-hidden="true">{{ initial }}</span>
        <div>
          <div class="profile-name">{{ displayName }}</div>
          <div class="profile-username">@{{ profile.username }}</div>
        </div>
      </div>

      <ul class="profile-stats">
        <li>
          <strong>{{ profile.levelName || '普通会员' }}</strong>
          <span>等级</span>
        </li>
        <li>
          <strong>{{ formatCount(profile.points) }}</strong>
          <span>积分</span>
        </li>
        <li>
          <strong>{{ formatCount(profile.growth) }}</strong>
          <span>成长值</span>
        </li>
      </ul>

      <dl class="profile-meta">
        <div>
          <dt>手机号</dt>
          <dd>{{ profile.mobile || '未填写' }}</dd>
        </div>
        <div>
          <dt>邮箱</dt>
          <dd>{{ profile.email || '未填写' }}</dd>
        </div>
        <div>
          <dt>性别</dt>
          <dd>{{ sexLabel }}</dd>
        </div>
        <div>
          <dt>注册时间</dt>
          <dd>{{ formatDate(profile.registerTime) || '—' }}</dd>
        </div>
        <div>
          <dt>最近登录</dt>
          <dd>{{ formatDate(profile.loginTime) || '—' }}</dd>
        </div>
      </dl>

      <div class="profile-actions">
        <button type="button" class="button ghost" :disabled="loggingOut" @click="onLogout">
          {{ loggingOut ? '退出中…' : '退出登录' }}
        </button>
      </div>
    </aside>

    <section v-else class="account-panel account-guard">
      <h1 class="panel-title">需要登录</h1>
      <p v-if="sessionError" class="form-alert error" role="alert">{{ sessionError }}</p>
      <p v-else class="panel-description">当前没有有效的会员会话，登录后才能查看个人中心。</p>
      <div class="form-actions">
        <NuxtLink class="button" to="/login?redirect=/account">去登录</NuxtLink>
        <NuxtLink class="button ghost" to="/register">注册新账号</NuxtLink>
      </div>
    </section>

    <div v-if="profile" class="account-panels">
      <section class="account-panel">
        <h2 class="panel-title">
          我的收藏
          <span v-if="favoritesTotal" class="panel-count">{{ favoritesTotal }} 篇</span>
        </h2>
        <p class="panel-description">收藏只对当前账号可见，文章下线后会显示为不可访问。</p>

        <p v-if="favoritesError" class="form-alert error" role="alert">{{ favoritesError }}</p>
        <p v-else-if="favoritesLoading" class="form-hint">正在加载收藏…</p>
        <p v-else-if="!favorites.length" class="favorites-empty">还没有收藏，去文章页点“收藏”试试。</p>
        <ul v-else class="favorites-list">
          <li v-for="row in favorites" :key="String(row.item.articleId)" class="favorite-item">
            <NuxtLink v-if="row.summary" :to="`/article/${row.item.articleId}`" class="favorite-link">
              <img
                v-if="favoriteCover(row.summary)"
                :src="favoriteCover(row.summary) || ''"
                alt=""
                class="favorite-cover"
              />
              <span class="favorite-body">
                <strong>{{ row.summary.title }}</strong>
                <span class="favorite-meta">
                  {{ row.summary.categoryName || '文章' }} · 收藏于 {{ formatDate(row.item.createTime) }}
                </span>
              </span>
            </NuxtLink>
            <span v-else class="favorite-missing">内容已下线或不可见</span>
            <button
              type="button"
              class="favorite-remove"
              :disabled="favoritesRemovingId === String(row.item.articleId)"
              @click="removeFavorite(row.item.articleId)"
            >
              {{ favoritesRemovingId === String(row.item.articleId) ? '处理中…' : '取消收藏' }}
            </button>
          </li>
        </ul>
        <div v-if="favoritePageCount > 1" class="favorites-pager">
          <button
            type="button"
            class="button ghost small"
            :disabled="favoritesPage <= 1 || favoritesLoading"
            @click="loadFavorites(favoritesPage - 1)"
          >
            上一页
          </button>
          <span>{{ favoritesPage }} / {{ favoritePageCount }}</span>
          <button
            type="button"
            class="button ghost small"
            :disabled="favoritesPage >= favoritePageCount || favoritesLoading"
            @click="loadFavorites(favoritesPage + 1)"
          >
            下一页
          </button>
        </div>
      </section>

      <section class="account-panel">
        <h2 class="panel-title">基本资料</h2>
        <p class="panel-description">昵称、头像与性别可自助修改；手机号 / 邮箱更换需要验证，暂未开放。</p>

        <p v-if="profileError" class="form-alert error" role="alert">{{ profileError }}</p>
        <p v-if="profileSuccess" class="form-alert info" role="status">{{ profileSuccess }}</p>

        <form @submit.prevent="onSaveProfile">
          <div class="form-field">
            <label for="profile-nickname">昵称</label>
            <input
              id="profile-nickname"
              v-model="profileForm.nickname"
              name="nickname"
              type="text"
              maxlength="64"
              required
            />
          </div>

          <div class="form-field">
            <label for="profile-avatar">头像</label>
            <div class="avatar-field">
              <span class="avatar-preview">
                <img v-if="previewAvatar" :src="previewAvatar" alt="头像预览" />
                <span v-else aria-hidden="true">{{ initial }}</span>
              </span>
              <div class="avatar-input">
                <input
                  id="profile-avatar"
                  v-model="profileForm.avatar"
                  name="avatar"
                  type="text"
                  maxlength="255"
                  placeholder="图片地址或文件 ID，留空则使用文字头像"
                />
                <p class="form-hint">支持 `https://` 图片地址、后台文件 ID，或 `/api/file/{id}` 路径。</p>
              </div>
            </div>
          </div>

          <div class="form-field">
            <label for="profile-sex">性别</label>
            <select id="profile-sex" v-model="profileForm.sex" name="sex">
              <option value="0">男</option>
              <option value="1">女</option>
              <option value="2">不公开</option>
            </select>
          </div>

          <div class="form-actions">
            <button type="submit" class="button" :disabled="savingProfile">
              {{ savingProfile ? '保存中…' : '保存资料' }}
            </button>
          </div>
        </form>
      </section>

      <section class="account-panel">
        <h2 class="panel-title">修改密码</h2>
        <p class="panel-description">修改成功后该账号的全部旧会话会立即失效，需要使用新密码重新登录。</p>

        <p v-if="passwordError" class="form-alert error" role="alert">{{ passwordError }}</p>

        <form @submit.prevent="onChangePassword">
          <div class="form-field">
            <label for="password-old">原密码</label>
            <input
              id="password-old"
              v-model="passwordForm.oldPassword"
              name="oldPassword"
              type="password"
              autocomplete="current-password"
              maxlength="64"
              required
            />
          </div>
          <div class="form-row">
            <div class="form-field">
              <label for="password-new">新密码</label>
              <input
                id="password-new"
                v-model="passwordForm.newPassword"
                name="newPassword"
                type="password"
                autocomplete="new-password"
                minlength="6"
                maxlength="64"
                required
              />
            </div>
            <div class="form-field">
              <label for="password-confirm">确认新密码</label>
              <input
                id="password-confirm"
                v-model="passwordForm.confirmPassword"
                name="confirmPassword"
                type="password"
                autocomplete="new-password"
                minlength="6"
                maxlength="64"
                required
              />
            </div>
          </div>
          <div class="form-actions">
            <button type="submit" class="button" :disabled="savingPassword">
              {{ savingPassword ? '提交中…' : '修改密码' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { MemberFavoriteItem, MemberProfile } from '~/types/member'
import type { PortalArticleSummary } from '~/types/portal'
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

const {
  profile,
  sessionError,
  ensureProfile,
  updateProfile,
  changePassword,
  logout,
  fetchFavorites,
  setFavorite
} = useMemberAuth()
const portalApi = usePortalApi()

await ensureProfile(true)

const profileForm = reactive({ nickname: '', avatar: '', sex: '2' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savingProfile = ref(false)
const savingPassword = ref(false)
const loggingOut = ref(false)
const profileError = ref('')
const profileSuccess = ref('')
const passwordError = ref('')

interface FavoriteRow {
  item: MemberFavoriteItem
  summary?: PortalArticleSummary
}

const FAVORITE_PAGE_SIZE = 6
const favorites = ref<FavoriteRow[]>([])
const favoritesTotal = ref(0)
const favoritesPage = ref(1)
const favoritesLoading = ref(false)
const favoritesRemovingId = ref('')
const favoritesError = ref('')

const displayName = computed(() => profile.value?.nickname || profile.value?.username || '会员')
const avatarUrl = computed(() => mediaUrl(profile.value?.avatar, 'thumb'))
const previewAvatar = computed(() => mediaUrl(profileForm.avatar, 'thumb'))
const initial = computed(() => (displayName.value || '会').slice(0, 1))
const favoritePageCount = computed(() =>
  Math.max(1, Math.ceil(favoritesTotal.value / FAVORITE_PAGE_SIZE))
)
const sexLabel = computed(() => {
  const value = profile.value?.sex
  if (value === '0') return '男'
  if (value === '1') return '女'
  return '不公开'
})

function favoriteCover(summary?: PortalArticleSummary): string | null {
  return summary ? mediaUrl(summary.cover, 'thumb') : null
}

/**
 * 收藏列表分两步取：会员接口只给文章 ID，再调 CMS 公开批量摘要补标题 / 封面。
 * 批量摘要接口不会累加阅读量，下线文章会被省略，因此这里允许出现“不可见”条目。
 */
async function loadFavorites(page = 1) {
  if (!profile.value) return
  favoritesLoading.value = true
  favoritesError.value = ''
  try {
    const result = await fetchFavorites(page, FAVORITE_PAGE_SIZE)
    const items = result.records || []
    const summaries = items.length
      ? await portalApi.fetchArticleBatch(items.map((item) => item.articleId))
      : []
    const summaryMap = new Map(summaries.map((item) => [String(item.articleId), item]))
    favorites.value = items.map((item) => ({
      item,
      summary: summaryMap.get(String(item.articleId))
    }))
    favoritesTotal.value = Number(result.total) || 0
    favoritesPage.value = page
  } catch (error) {
    favorites.value = []
    favoritesTotal.value = 0
    favoritesError.value = memberErrorMessage(error, '收藏加载失败，请稍后再试')
  } finally {
    favoritesLoading.value = false
  }
}

function resetFavorites() {
  favorites.value = []
  favoritesTotal.value = 0
  favoritesPage.value = 1
  favoritesError.value = ''
  favoritesRemovingId.value = ''
}

async function removeFavorite(articleId: string | number) {
  if (favoritesRemovingId.value) return
  const key = String(articleId)
  favoritesRemovingId.value = key
  favoritesError.value = ''
  try {
    await setFavorite(key, false)
    // 删除当前页最后一条时退回上一页，避免停在空页
    const target = favorites.value.length === 1 && favoritesPage.value > 1
      ? favoritesPage.value - 1
      : favoritesPage.value
    await loadFavorites(target)
  } catch (error) {
    favoritesError.value = memberErrorMessage(error, '取消收藏失败，请稍后再试')
  } finally {
    favoritesRemovingId.value = ''
  }
}

onMounted(() => {
  if (profile.value) loadFavorites(1)
})
watch(profile, (value) => {
  if (!value) resetFavorites()
})

/** 资料返回后回填表单；保存成功返回同一份资料时也会刷新。 */
function fillProfileForm(value: MemberProfile | null) {
  profileForm.nickname = value?.nickname || value?.username || ''
  profileForm.avatar = value?.avatar || ''
  profileForm.sex = value?.sex || '2'
}

fillProfileForm(profile.value)
watch(profile, fillProfileForm)

async function onSaveProfile() {
  profileError.value = ''
  profileSuccess.value = ''
  const nickname = profileForm.nickname.trim()
  if (!nickname) {
    profileError.value = '昵称不能为空'
    return
  }

  savingProfile.value = true
  try {
    await updateProfile({
      nickname,
      avatar: profileForm.avatar.trim(),
      sex: profileForm.sex
    })
    profileSuccess.value = '资料已保存'
  } catch (error) {
    profileError.value = memberErrorMessage(error, '保存失败，请稍后重试')
  } finally {
    savingProfile.value = false
  }
}

async function onChangePassword() {
  passwordError.value = ''
  if (passwordForm.newPassword.length < 6) {
    passwordError.value = '新密码至少 6 位'
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  if (passwordForm.oldPassword === passwordForm.newPassword) {
    passwordError.value = '新密码不能与原密码相同'
    return
  }

  savingPassword.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    await navigateTo('/login?changed=1', { replace: true })
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1002) {
      passwordError.value = '登录状态已失效，请重新登录后再修改'
    } else {
      passwordError.value = memberErrorMessage(error, '修改失败，请稍后重试')
    }
  } finally {
    savingPassword.value = false
  }
}

async function onLogout() {
  loggingOut.value = true
  try {
    await logout()
    await navigateTo('/', { replace: true })
  } finally {
    loggingOut.value = false
  }
}

useSeoMeta({ title: '个人中心', robots: 'noindex' })
</script>
