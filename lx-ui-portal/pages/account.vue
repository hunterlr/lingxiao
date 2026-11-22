<template>
  <div class="account-page">
    <aside v-if="profile" class="profile-card">
      <div class="profile-head">
        <img
          v-if="avatarUrl"
          :src="avatarUrl"
          :alt="displayName"
          class="profile-avatar"
          referrerpolicy="no-referrer"
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
                <img v-if="previewAvatar" :src="previewAvatar" alt="头像预览" referrerpolicy="no-referrer" />
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
        <h2 class="panel-title">第三方账号</h2>
        <p class="panel-description">
          绑定后可直接用第三方账号登录。解绑前请确认仍记得账号密码，否则可能无法再次登录。
        </p>

        <p v-if="oauthNotice" class="form-alert info" role="status">{{ oauthNotice }}</p>
        <p v-if="oauthBindError" class="form-alert error" role="alert">{{ oauthBindError }}</p>
        <p v-if="bindingsError" class="form-alert error" role="alert">{{ bindingsError }}</p>
        <p v-else-if="bindingsLoading" class="form-hint">正在加载绑定信息…</p>
        <p v-else-if="!bindings.length" class="form-hint">还没有绑定第三方账号。</p>

        <ul v-else class="oauth-bindings">
          <li v-for="item in bindings" :key="item.provider" class="oauth-binding">
            <div class="oauth-binding-main">
              <strong>{{ item.displayName }}</strong>
              <span class="oauth-binding-meta">
                {{ item.nickname || '已绑定' }}
                <template v-if="item.lastLoginTime"> · 最近登录 {{ formatDate(item.lastLoginTime) }}</template>
              </span>
            </div>
            <button
              type="button"
              class="oauth-unbind"
              :disabled="unbindingProvider === item.provider"
              @click="onUnbind(item)"
            >
              {{ unbindingProvider === item.provider ? '解绑中…' : '解绑' }}
            </button>
          </li>
        </ul>

        <MemberOAuthButtons
          mode="bind"
          :bound-providers="boundProviders"
          @error="bindingsError = $event"
        />
      </section>

      <section class="account-panel">
        <template v-if="passwordSet">
          <h2 class="panel-title">修改密码</h2>
          <p class="panel-description">修改成功后该账号的全部旧会话会立即失效，需要使用新密码重新登录。</p>

          <p v-if="passwordError" class="form-alert error" role="alert">{{ passwordError }}</p>
          <p v-if="passwordSuccess" class="form-alert info" role="status">{{ passwordSuccess }}</p>

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
        </template>

        <template v-else>
          <h2 class="panel-title">设置登录密码</h2>
          <p class="panel-description">
            当前账号通过第三方登录创建，还没有设置密码。设置后可以同时用账号 + 密码登录，
            解绑第三方账号时也不会把自己锁在门外。
          </p>

          <p v-if="passwordError" class="form-alert error" role="alert">{{ passwordError }}</p>
          <p v-if="passwordSuccess" class="form-alert info" role="status">{{ passwordSuccess }}</p>

          <form @submit.prevent="onSetPassword">
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
                {{ savingPassword ? '提交中…' : '设置密码' }}
              </button>
            </div>
          </form>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { MemberFavoriteItem, MemberOAuthBinding, MemberProfile } from '~/types/member'
import type { PortalArticleSummary } from '~/types/portal'
import { MemberApiError, memberErrorMessage, oauthErrorMessage } from '~/composables/useMemberAuth'

const route = useRoute()
const {
  profile,
  sessionError,
  ensureProfile,
  updateProfile,
  changePassword,
  setPassword,
  logout,
  fetchFavorites,
  setFavorite,
  fetchOAuthBindings,
  unbindOAuth
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
const passwordSuccess = ref('')
/** 只有第三方建号（password_set=0）才显示“设置密码”；老账号默认视为已设置。 */
const passwordSet = computed(() => profile.value?.passwordSet !== '0')

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

const bindings = ref<MemberOAuthBinding[]>([])
const bindingsLoading = ref(false)
const bindingsError = ref('')
const unbindingProvider = ref('')
const boundProviders = computed(() => bindings.value.map((item) => item.provider))
const oauthNotice = computed(() => {
  const bound = route.query.oauthBound
  if (typeof bound === 'string' && bound) {
    const matched = bindings.value.find((item) => item.provider === bound)
    return matched ? `已绑定 ${matched.displayName}` : '第三方账号绑定成功'
  }
  const code = route.query.oauthError
  return typeof code === 'string' && code ? oauthErrorMessage(code) : ''
})
/** 登录后自动绑定失败（凭证过期 / 账号不一致），提示用户在这里手动重试。 */
const oauthBindError = computed(() =>
  typeof route.query.oauthBindError === 'string' && route.query.oauthBindError
    ? '第三方账号自动绑定失败或凭证已过期，请在下方点击对应平台重新绑定。'
    : ''
)

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

/** 第三方绑定列表；失败只影响这一块，不影响收藏与资料。 */
async function loadBindings() {
  if (!profile.value) return
  bindingsLoading.value = true
  bindingsError.value = ''
  try {
    bindings.value = (await fetchOAuthBindings()) || []
  } catch (error) {
    bindings.value = []
    bindingsError.value = memberErrorMessage(error, '绑定信息加载失败，请稍后再试')
  } finally {
    bindingsLoading.value = false
  }
}

async function onUnbind(item: MemberOAuthBinding) {
  if (unbindingProvider.value) return
  if (!window.confirm(`确认解绑 ${item.displayName} 吗？解绑后将不能用它登录。`)) return
  unbindingProvider.value = item.provider
  bindingsError.value = ''
  try {
    await unbindOAuth(item.provider)
    await loadBindings()
  } catch (error) {
    bindingsError.value = memberErrorMessage(error, '解绑失败，请稍后再试')
  } finally {
    unbindingProvider.value = ''
  }
}

onMounted(() => {
  if (profile.value) {
    loadFavorites(1)
    loadBindings()
  }
})
watch(profile, (value) => {
  if (!value) {
    resetFavorites()
    bindings.value = []
    bindingsError.value = ''
  }
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

/**
 * 第三方建号首次设置密码。
 *
 * <p>不注销当前会话：设置成功后继续留在个人中心，页面自动切换成“修改密码”。</p>
 */
async function onSetPassword() {
  passwordError.value = ''
  passwordSuccess.value = ''
  if (passwordForm.newPassword.length < 6) {
    passwordError.value = '新密码至少 6 位'
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }

  savingPassword.value = true
  try {
    await setPassword({ newPassword: passwordForm.newPassword })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordSuccess.value = '登录密码已设置，现在可以用账号 + 密码登录。'
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1002) {
      passwordError.value = '登录状态已失效，请重新登录后再设置'
    } else {
      passwordError.value = memberErrorMessage(error, '设置失败，请稍后重试')
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
