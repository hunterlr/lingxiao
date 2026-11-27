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
          <strong>{{ profile.levelName || t('account.card.levelFallback') }}</strong>
          <span>{{ t('account.card.level') }}</span>
        </li>
        <li>
          <strong>{{ formatCount(profile.points) }}</strong>
          <span>{{ t('account.card.points') }}</span>
        </li>
        <li>
          <strong>{{ formatCount(profile.growth) }}</strong>
          <span>{{ t('account.card.growth') }}</span>
        </li>
      </ul>

      <dl class="profile-meta">
        <div>
          <dt>{{ t('account.card.mobile') }}</dt>
          <dd>{{ profile.mobile || t('account.card.notFilled') }}</dd>
        </div>
        <div>
          <dt>{{ t('account.card.email') }}</dt>
          <dd>{{ profile.email || t('account.card.notFilled') }}</dd>
        </div>
        <div>
          <dt>{{ t('account.card.sex') }}</dt>
          <dd>{{ sexLabel }}</dd>
        </div>
        <div>
          <dt>{{ t('account.card.registerTime') }}</dt>
          <dd>{{ formatDate(profile.registerTime) || '—' }}</dd>
        </div>
        <div>
          <dt>{{ t('account.card.lastLogin') }}</dt>
          <dd>{{ formatDate(profile.loginTime) || '—' }}</dd>
        </div>
      </dl>

      <div class="profile-actions">
        <button type="button" class="button ghost" :disabled="loggingOut" @click="onLogout">
          {{ t('account.card.logout') }}
        </button>
      </div>
    </aside>

    <section v-else class="account-panel account-guard">
      <h1 class="panel-title">{{ t('account.guard.title') }}</h1>
      <p v-if="sessionError" class="form-alert error" role="alert">{{ sessionError }}</p>
      <p v-else class="panel-description">{{ t('account.guard.desc') }}</p>
      <div class="form-actions">
        <NuxtLink class="button" to="/login?redirect=/account">{{ t('account.guard.goLogin') }}</NuxtLink>
        <NuxtLink class="button ghost" to="/register">{{ t('account.guard.register') }}</NuxtLink>
      </div>
    </section>

    <div v-if="profile" class="account-panels">
      <section class="account-panel">
        <h2 class="panel-title">
          {{ t('account.favorites.title') }}
          <span v-if="favoritesTotal" class="panel-count">{{ t('account.favorites.count', { count: favoritesTotal }) }}</span>
        </h2>
        <p class="panel-description">{{ t('account.favorites.desc') }}</p>

        <p v-if="favoritesError" class="form-alert error" role="alert">{{ favoritesError }}</p>
        <p v-else-if="favoritesLoading" class="form-hint">{{ t('account.favorites.loading') }}</p>
        <p v-else-if="!favorites.length" class="favorites-empty">{{ t('account.favorites.empty') }}</p>
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
                  {{ t('account.favorites.meta', { category: row.summary.categoryName || t('account.favorites.article'), date: formatDate(row.item.createTime) }) }}
                </span>
              </span>
            </NuxtLink>
            <span v-else class="favorite-missing">{{ t('account.favorites.missing') }}</span>
            <button
              type="button"
              class="favorite-remove"
              :disabled="favoritesRemovingId === String(row.item.articleId)"
              @click="removeFavorite(row.item.articleId)"
            >
              {{ t('account.favorites.remove') }}
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
            {{ t('account.favorites.prev') }}
          </button>
          <span>{{ favoritesPage }} / {{ favoritePageCount }}</span>
          <button
            type="button"
            class="button ghost small"
            :disabled="favoritesPage >= favoritePageCount || favoritesLoading"
            @click="loadFavorites(favoritesPage + 1)"
          >
            {{ t('account.favorites.next') }}
          </button>
        </div>
      </section>

      <section class="account-panel">
        <h2 class="panel-title">{{ t('account.profile.title') }}</h2>
        <p class="panel-description">{{ t('account.profile.desc') }}</p>

        <p v-if="profileError" class="form-alert error" role="alert">{{ profileError }}</p>
        <p v-if="profileSuccess" class="form-alert info" role="status">{{ profileSuccess }}</p>

        <form @submit.prevent="onSaveProfile">
          <div class="form-field">
            <label for="profile-nickname">{{ t('account.profile.nickname') }}</label>
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
            <label for="profile-avatar">{{ t('account.profile.avatar') }}</label>
            <div class="avatar-field">
              <span class="avatar-preview">
                <img v-if="previewAvatar" :src="previewAvatar" :alt="t('account.profile.avatarPreview')" referrerpolicy="no-referrer" />
                <span v-else aria-hidden="true">{{ initial }}</span>
              </span>
              <div class="avatar-input">
                <input
                  id="profile-avatar"
                  v-model="profileForm.avatar"
                  name="avatar"
                  type="text"
                  maxlength="255"
                  :placeholder="t('account.profile.avatarPlaceholder')"
                />
                <div class="avatar-upload">
                  <input
                    ref="avatarInput"
                    type="file"
                    accept="image/*"
                    class="avatar-file"
                    @change="onAvatarPicked"
                  />
                  <button
                    type="button"
                    class="button ghost"
                    :disabled="avatarUploading"
                    @click="avatarInput?.click()"
                  >
                    {{ t('account.profile.upload') }}
                  </button>
                  <span class="form-hint">{{ t('account.profile.uploadHint') }}</span>
                </div>
                <p class="form-hint">{{ t('account.profile.avatarHint') }}</p>
              </div>
            </div>
          </div>

          <div class="form-field">
            <label for="profile-sex">{{ t('account.card.sex') }}</label>
            <select id="profile-sex" v-model="profileForm.sex" name="sex">
              <option value="0">{{ t('account.profile.male') }}</option>
              <option value="1">{{ t('account.profile.female') }}</option>
              <option value="2">{{ t('account.profile.secret') }}</option>
            </select>
          </div>

          <div class="form-actions">
            <button type="submit" class="button" :disabled="savingProfile">
              {{ t('account.profile.save') }}
            </button>
          </div>
        </form>
      </section>

      <section class="account-panel">
        <h2 class="panel-title">{{ t('account.security.title') }}</h2>
        <p class="panel-description">{{ t('account.security.desc1') }} {{ t('account.security.desc2') }}</p>

        <div class="contact-block">
          <h3 class="contact-title">{{ t('account.card.mobile') }}</h3>
          <p class="form-hint">{{ t('account.security.mobileCurrent', { value: profile.mobile || t('account.security.notBound') }) }}</p>
          <p v-if="contactError.mobile" class="form-alert error" role="alert">{{ contactError.mobile }}</p>
          <p v-if="contactSuccess.mobile" class="form-alert info" role="status">{{ contactSuccess.mobile }}</p>

          <form @submit.prevent="onChangeContact('mobile')">
            <div class="form-field">
              <label for="contact-mobile">{{ t('account.security.newMobile') }}</label>
              <input
                id="contact-mobile"
                v-model="contactForms.mobile.value"
                name="mobile"
                type="tel"
                autocomplete="tel"
                maxlength="32"
                :placeholder="t('account.security.newMobilePlaceholder')"
                required
              />
            </div>
            <div v-if="passwordSet" class="form-field">
              <label for="contact-mobile-password">{{ t('account.security.currentPassword') }}</label>
              <input
                id="contact-mobile-password"
                v-model="contactForms.mobile.password"
                name="password"
                type="password"
                autocomplete="current-password"
                maxlength="64"
                :placeholder="t('account.security.currentPasswordPlaceholder')"
                required
              />
            </div>
            <div class="contact-code-row">
              <div class="form-field">
                <label for="contact-mobile-code">{{ t('account.security.code') }}</label>
                <input
                  id="contact-mobile-code"
                  v-model="contactForms.mobile.code"
                  name="code"
                  type="text"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  maxlength="16"
                  :placeholder="t('account.security.mobileCodePlaceholder')"
                  required
                />
              </div>
              <button
                type="button"
                class="button ghost small"
                :disabled="!!contactLoading.mobile"
                @click="onSendContactCode('mobile')"
              >
                {{ t('account.security.sendCode') }}
              </button>
            </div>
            <div class="form-actions">
              <button type="submit" class="button" :disabled="!!contactLoading.mobile">
                {{ t('account.security.confirmMobile') }}
              </button>
            </div>
          </form>
        </div>

        <div class="contact-block">
          <h3 class="contact-title">{{ t('account.card.email') }}</h3>
          <p class="form-hint">{{ t('account.security.emailCurrent', { value: profile.email || t('account.security.notBound') }) }}</p>
          <p v-if="contactError.email" class="form-alert error" role="alert">{{ contactError.email }}</p>
          <p v-if="contactSuccess.email" class="form-alert info" role="status">{{ contactSuccess.email }}</p>

          <form @submit.prevent="onChangeContact('email')">
            <div class="form-field">
              <label for="contact-email">{{ t('account.security.newEmail') }}</label>
              <input
                id="contact-email"
                v-model="contactForms.email.value"
                name="email"
                type="email"
                autocomplete="email"
                maxlength="128"
                :placeholder="t('account.security.newEmailPlaceholder')"
                required
              />
            </div>
            <div v-if="passwordSet" class="form-field">
              <label for="contact-email-password">{{ t('account.security.currentPassword') }}</label>
              <input
                id="contact-email-password"
                v-model="contactForms.email.password"
                name="password"
                type="password"
                autocomplete="current-password"
                maxlength="64"
                :placeholder="t('account.security.currentPasswordPlaceholder')"
                required
              />
            </div>
            <div class="contact-code-row">
              <div class="form-field">
                <label for="contact-email-code">{{ t('account.security.code') }}</label>
                <input
                  id="contact-email-code"
                  v-model="contactForms.email.code"
                  name="code"
                  type="text"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  maxlength="16"
                  :placeholder="t('account.security.emailCodePlaceholder')"
                  required
                />
              </div>
              <button
                type="button"
                class="button ghost small"
                :disabled="!!contactLoading.email"
                @click="onSendContactCode('email')"
              >
                {{ t('account.security.sendCodeEmail') }}
              </button>
            </div>
            <div class="form-actions">
              <button type="submit" class="button" :disabled="!!contactLoading.email">
                {{ t('account.security.confirmEmail') }}
              </button>
            </div>
          </form>
        </div>
      </section>

      <section class="account-panel">
        <h2 class="panel-title">{{ t('account.sessions.title') }}</h2>
        <p class="panel-description">
          {{ t('account.sessions.desc') }}
        </p>

        <p v-if="sessionsError" class="form-alert error" role="alert">{{ sessionsError }}</p>

        <ul v-if="sessions.length" class="session-list">
          <li v-for="item in sessions" :key="item.sessionId" class="session-item">
            <div class="session-main">
              <strong>
                {{ item.device || t('account.sessions.unknownDevice') }}
                <span v-if="item.currentSession" class="session-tag">{{ t('account.sessions.current') }}</span>
              </strong>
              <span class="form-hint">
                {{ t('account.sessions.meta', { ip: item.loginIp || t('account.sessions.unknownIp'), time: formatDate(item.loginTime) || t('account.sessions.unknownTime') }) }}
              </span>
            </div>
            <button
              v-if="!item.currentSession"
              type="button"
              class="button ghost"
              :disabled="kickingId === item.sessionId"
              @click="onKickSession(item)"
            >
              {{ t('account.sessions.signOut') }}
            </button>
          </li>
        </ul>
        <p v-else-if="!sessionsLoaded" class="form-hint">{{ t('account.sessions.loading') }}</p>
        <p v-else class="form-hint">{{ t('account.sessions.onlyThis') }}</p>

        <div v-if="sessions.some((item) => !item.currentSession)" class="form-actions">
          <button type="button" class="button ghost" :disabled="kickingOthers" @click="onKickOthers">
            {{ t('account.sessions.signOutOthers') }}
          </button>
        </div>
      </section>

      <section class="account-panel">
        <h2 class="panel-title">{{ t('account.bindings.title') }}</h2>
        <p class="panel-description">
          {{ t('account.bindings.desc') }}
        </p>

        <p v-if="oauthNotice" class="form-alert info" role="status">{{ oauthNotice }}</p>
        <p v-if="oauthBindError" class="form-alert error" role="alert">{{ oauthBindError }}</p>
        <p v-if="bindingsError" class="form-alert error" role="alert">{{ bindingsError }}</p>
        <p v-else-if="bindingsLoading" class="form-hint">{{ t('account.bindings.loading') }}</p>
        <p v-else-if="!bindings.length" class="form-hint">{{ t('account.bindings.empty') }}</p>

        <ul v-else class="oauth-bindings">
          <li v-for="item in bindings" :key="item.provider" class="oauth-binding">
            <div class="oauth-binding-main">
              <strong>{{ item.displayName }}</strong>
              <span class="oauth-binding-meta">
                {{ item.nickname || t('account.bindings.bound') }}
                <template v-if="item.lastLoginTime"> · {{ t('account.bindings.lastLogin', { date: formatDate(item.lastLoginTime) }) }}</template>
              </span>
            </div>
            <button
              type="button"
              class="oauth-unbind"
              :disabled="unbindingProvider === item.provider"
              @click="onUnbind(item)"
            >
              {{ t('account.bindings.unbind') }}
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
          <h2 class="panel-title">{{ t('account.password.title') }}</h2>
          <p class="panel-description">{{ t('account.password.desc') }}</p>

          <p v-if="passwordError" class="form-alert error" role="alert">{{ passwordError }}</p>
          <p v-if="passwordSuccess" class="form-alert info" role="status">{{ passwordSuccess }}</p>

          <form @submit.prevent="onChangePassword">
            <div class="form-field">
              <label for="password-old">{{ t('account.password.current') }}</label>
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
                <label for="password-new">{{ t('account.password.new') }}</label>
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
                <label for="password-confirm">{{ t('account.password.confirm') }}</label>
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
                {{ t('account.password.submit') }}
              </button>
              <div class="form-links">
                <NuxtLink to="/forgot-password">{{ t('account.password.forgot') }}</NuxtLink>
              </div>
            </div>
          </form>
        </template>

        <template v-else>
          <h2 class="panel-title">{{ t('account.setPassword.title') }}</h2>
          <p class="panel-description">
            {{ t('account.setPassword.desc1') }}
            {{ t('account.setPassword.desc2') }}
          </p>

          <p v-if="passwordError" class="form-alert error" role="alert">{{ passwordError }}</p>
          <p v-if="passwordSuccess" class="form-alert info" role="status">{{ passwordSuccess }}</p>

          <form @submit.prevent="onSetPassword">
            <div class="form-row">
              <div class="form-field">
                <label for="password-new">{{ t('account.password.new') }}</label>
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
                <label for="password-confirm">{{ t('account.password.confirm') }}</label>
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
                {{ t('account.setPassword.submit') }}
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
import { MemberApiError, memberErrorMessage } from '~/composables/useMemberAuth'

const route = useRoute()
const {
  profile,
  sessionError,
  ensureProfile,
  updateProfile,
  changePassword,
  setPassword,
  sendContactCode,
  changeContact,
  logout,
  fetchFavorites,
  setFavorite,
  fetchOAuthBindings,
  unbindOAuth,
  uploadAvatar,
  fetchSessions,
  kickSession,
  kickOtherSessions
} = useMemberAuth()
const portalApi = usePortalApi()
const { t } = usePortalI18n()

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

/* ==================== 头像上传 ==================== */

const avatarUploading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)

async function onAvatarPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  profileError.value = ''
  profileSuccess.value = ''
  avatarUploading.value = true
  try {
    const updated = await uploadAvatar(file)
    profileForm.avatar = updated.avatar || ''
    profileSuccess.value = t('account.msg.avatarUpdated')
  } catch (error) {
    profileError.value = memberErrorMessage(error, t('account.msg.avatarFailed'))
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}

/* ==================== 登录设备 ==================== */

const sessions = ref<MemberDeviceSession[]>([])
const sessionsLoading = ref(false)
/** 首次加载完成前不显示“只有当前设备”，避免 SSR 先闪一句错误文案 */
const sessionsLoaded = ref(false)
const sessionsError = ref('')
const kickingId = ref('')
const kickingOthers = ref(false)

async function loadSessions() {
  sessionsLoading.value = true
  sessionsError.value = ''
  try {
    sessions.value = await fetchSessions()
  } catch (error) {
    sessionsError.value = memberErrorMessage(error, t('account.msg.sessionsFailed'))
  } finally {
    sessionsLoading.value = false
    sessionsLoaded.value = true
  }
}

async function onKickSession(item: MemberDeviceSession) {
  kickingId.value = item.sessionId
  sessionsError.value = ''
  try {
    await kickSession(item.sessionId)
    sessions.value = sessions.value.filter((row) => row.sessionId !== item.sessionId)
  } catch (error) {
    sessionsError.value = memberErrorMessage(error, t('account.msg.signOutFailed'))
  } finally {
    kickingId.value = ''
  }
}

async function onKickOthers() {
  kickingOthers.value = true
  sessionsError.value = ''
  try {
    await kickOtherSessions()
    sessions.value = sessions.value.filter((row) => row.currentSession)
  } catch (error) {
    sessionsError.value = memberErrorMessage(error, t('account.msg.actionFailed'))
  } finally {
    kickingOthers.value = false
  }
}

/** 手机号 / 邮箱换绑表单；密码只在发送验证码时校验，确认换绑不需要再带。 */
const contactForms = reactive({
  mobile: { value: '', code: '', password: '' },
  email: { value: '', code: '', password: '' }
})
const contactLoading = reactive({ mobile: '', email: '' })
const contactError = reactive({ mobile: '', email: '' })
const contactSuccess = reactive({ mobile: '', email: '' })

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
    return matched ? t('account.msg.bindOk', { name: matched.displayName }) : t('account.msg.bindOkFallback')
  }
  const code = route.query.oauthError
  return typeof code === 'string' && code ? oauthErrorText(code) : ''
})
/** 登录后自动绑定失败（凭证过期 / 账号不一致），提示用户在这里手动重试。 */
const oauthBindError = computed(() =>
  typeof route.query.oauthBindError === 'string' && route.query.oauthBindError
    ? t('account.msg.bindRetry')
    : ''
)

const displayName = computed(() => profile.value?.nickname || profile.value?.username || t('account.msg.memberFallback'))
const avatarUrl = computed(() => mediaUrl(profile.value?.avatar, 'thumb'))
const previewAvatar = computed(() => mediaUrl(profileForm.avatar, 'thumb'))
const initial = computed(() => (displayName.value || t('account.msg.initialFallback')).slice(0, 1))
const favoritePageCount = computed(() =>
  Math.max(1, Math.ceil(favoritesTotal.value / FAVORITE_PAGE_SIZE))
)
const sexLabel = computed(() => {
  const value = profile.value?.sex
  if (value === '0') return t('account.profile.male')
  if (value === '1') return t('account.profile.female')
  return t('account.profile.secret')
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
    favoritesError.value = memberErrorMessage(error, t('account.msg.favoritesFailed'))
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
    favoritesError.value = memberErrorMessage(error, t('account.msg.unfavoriteFailed'))
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
    bindingsError.value = memberErrorMessage(error, t('account.msg.bindingsFailed'))
  } finally {
    bindingsLoading.value = false
  }
}

async function onUnbind(item: MemberOAuthBinding) {
  if (unbindingProvider.value) return
  if (!window.confirm(t('account.msg.unbindConfirm', { name: item.displayName }))) return
  unbindingProvider.value = item.provider
  bindingsError.value = ''
  try {
    await unbindOAuth(item.provider)
    await loadBindings()
  } catch (error) {
    bindingsError.value = memberErrorMessage(error, t('account.msg.unbindFailed'))
  } finally {
    unbindingProvider.value = ''
  }
}

onMounted(() => {
  if (profile.value) {
    loadFavorites(1)
    loadBindings()
    loadSessions()
  }
})
watch(profile, (value) => {
  if (!value) {
    resetFavorites()
    bindings.value = []
    bindingsError.value = ''
    sessions.value = []
    sessionsError.value = ''
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
    profileError.value = t('account.msg.nicknameRequired')
    return
  }

  savingProfile.value = true
  try {
    await updateProfile({
      nickname,
      avatar: profileForm.avatar.trim(),
      sex: profileForm.sex
    })
    profileSuccess.value = t('account.msg.profileSaved')
  } catch (error) {
    profileError.value = memberErrorMessage(error, t('account.msg.saveFailed'))
  } finally {
    savingProfile.value = false
  }
}

async function onChangePassword() {
  passwordError.value = ''
  if (passwordForm.newPassword.length < 6) {
    passwordError.value = t('account.msg.passwordTooShort')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = t('account.msg.passwordMismatch')
    return
  }
  if (passwordForm.oldPassword === passwordForm.newPassword) {
    passwordError.value = t('account.msg.passwordSame')
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
      passwordError.value = t('account.msg.sessionExpired')
    } else {
      passwordError.value = memberErrorMessage(error, t('account.msg.changeFailed'))
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
    passwordError.value = t('account.msg.passwordTooShort')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = t('account.msg.passwordMismatch')
    return
  }

  savingPassword.value = true
  try {
    await setPassword({ newPassword: passwordForm.newPassword })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordSuccess.value = t('account.msg.passwordSet')
  } catch (error) {
    if (error instanceof MemberApiError && error.code === 1002) {
      passwordError.value = t('account.msg.sessionExpiredSet')
    } else {
      passwordError.value = memberErrorMessage(error, t('account.msg.setFailed'))
    }
  } finally {
    savingPassword.value = false
  }
}

async function onSendContactCode(type: 'mobile' | 'email') {
  const form = contactForms[type]
  contactError[type] = ''
  contactSuccess[type] = ''
  const value = form.value.trim()
  if (!value) {
    contactError[type] = t('account.msg.newContactRequired')
    return
  }
  if (passwordSet.value && !form.password) {
    contactError[type] = t('account.msg.currentPasswordRequired')
    return
  }

  contactLoading[type] = 'code'
  try {
    await sendContactCode({
      type,
      value,
      password: passwordSet.value ? form.password : undefined
    })
    form.value = value
    contactSuccess[type] = t('account.msg.codeSent', { value })
  } catch (error) {
    contactError[type] = memberErrorMessage(error, t('account.msg.codeFailed'))
  } finally {
    contactLoading[type] = ''
  }
}

async function onChangeContact(type: 'mobile' | 'email') {
  const form = contactForms[type]
  contactError[type] = ''
  contactSuccess[type] = ''
  const value = form.value.trim()
  if (!value) {
    contactError[type] = t('account.msg.newContactRequired')
    return
  }
  if (!form.code.trim()) {
    contactError[type] = t('account.msg.codeRequired')
    return
  }

  contactLoading[type] = 'change'
  try {
    await changeContact({ type, value, code: form.code.trim() })
    contactSuccess[type] = t('account.msg.contactChanged')
    form.value = ''
    form.code = ''
    form.password = ''
  } catch (error) {
    contactError[type] = memberErrorMessage(error, t('account.msg.changeContactFailed'))
  } finally {
    contactLoading[type] = ''
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

useSeoMeta({ title: () => t('account.seoTitle'), robots: 'noindex' })
</script>
