/**
 * 多语言模块验收脚本（v1.4 / P2.5 第二刀）。
 *
 * 启用态（默认，后端 8080，需 --lx.captcha.enabled=false）：
 *   node lx-modules/lx-module-i18n/scripts/verify-i18n.mjs
 *
 * 关闭态（后端 8081，需 --lx.module.i18n.enabled=false 且使用全新库）：
 *   node lx-modules/lx-module-i18n/scripts/verify-i18n.mjs --off
 *
 * 环境变量：
 *   I18N_BASE        启用态 API 前缀，默认 http://127.0.0.1:8080/api
 *   I18N_OFF_BASE    关闭态 API 前缀，默认 http://127.0.0.1:8081/api
 *   I18N_OFF_DB      关闭态连接的库名，默认 lingxiao_verify
 *   MYSQL_BIN        mysql 客户端路径（仅关闭态检查表用）
 *
 * 脚本可重复执行：demo 键固定复用，日语语言每次先清理再创建。
 */
import { execFileSync } from 'node:child_process'

const args = new Set(process.argv.slice(2))
const OFF = args.has('--off')
const BASE = OFF
  ? (process.env.I18N_OFF_BASE || 'http://127.0.0.1:8081/api')
  : (process.env.I18N_BASE || 'http://127.0.0.1:8080/api')

const MYSQL = process.env.MYSQL_BIN || 'D:\\phpstudy_pro\\Extensions\\MySQL5.7.26\\bin\\mysql.exe'
const OFF_DB = process.env.I18N_OFF_DB || 'lingxiao_verify'

let pass = 0
let fail = 0
let token = ''

const results = []

function ok(cond, name, detail) {
  if (cond) {
    pass += 1
    results.push({ pass: true, name })
    console.log('  PASS  ' + name)
  } else {
    fail += 1
    results.push({ pass: false, name, detail })
    console.log('  FAIL  ' + name + (detail === undefined ? '' : ' :: ' + JSON.stringify(detail).slice(0, 500)))
  }
}

async function request(method, path, { body, headers = {}, form } = {}) {
  const init = { method, headers: { ...headers } }
  if (form) {
    init.body = form
  } else if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  const res = await fetch(BASE + path, init)
  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch (e) {
    json = null
  }
  return { status: res.status, text, json, headers: res.headers }
}

function authed(extra = {}) {
  return { ...(token ? { 'lx-platform-token': token } : {}), ...extra }
}

async function api(method, path, options = {}) {
  return request(method, path, { ...options, headers: authed(options.headers) })
}

function messageKeyOf(res) {
  return res.json && res.json.messageKey
}

function loginHeaders(extra = {}) {
  return { 'Content-Type': 'application/json', ...extra }
}

// =====================================================================
// 关闭态：接口 404、菜单不下发、迁移不扫描
// =====================================================================
async function verifyOff() {
  console.log(`\n多语言关闭态验收：${BASE}\n`)

  const login = await request('POST', '/admin/auth/login', {
    body: { username: 'admin', password: 'admin123' },
    headers: loginHeaders()
  })
  ok(login.json && login.json.code === 0, '平台后台仍可登录', login.json)
  token = login.json?.data?.tokenValue

  const blank = await request('POST', '/admin/auth/login', {
    body: { username: '', password: '' },
    headers: loginHeaders({ 'X-Lang': 'en-US' })
  })
  ok(blank.json && blank.json.code !== 0 && /Username must not be empty/.test(blank.json.msg || ''),
    '关闭模块后参数校验仍走资源包（英文）', blank.json)

  const endpoints = [
    ['GET', '/admin/i18n/language/list'],
    ['GET', '/admin/i18n/key/list?pageNum=1&pageSize=10'],
    ['GET', '/admin/i18n/coverage?languageCode=en-US'],
    ['GET', '/admin/i18n/pack/current'],
    ['GET', '/portal/i18n/languages'],
    ['GET', '/portal/i18n/en-US/demo']
  ]
  for (const [method, path] of endpoints) {
    const res = await api(method, path)
    ok(res.status === 404, `接口 404 ${method} ${path}`, { status: res.status, body: res.text.slice(0, 120) })
  }

  const menus = await api('GET', '/admin/auth/menus')
  const flat = []
  const walk = (list) => (list || []).forEach((m) => { flat.push(m); walk(m.children) })
  walk(menus.json?.data)
  ok(menus.json?.code === 0 && flat.every((m) => m.moduleCode !== 'i18n'),
    '多语言菜单不下发', flat.filter((m) => m.moduleCode === 'i18n'))

  const tables = sql(`SHOW TABLES FROM \`${OFF_DB}\` LIKE 'i18n\\_%';`)
  ok(tables === '', '全新库未创建 i18n_* 表', tables)

  const migrated = sql(`SELECT COUNT(*) FROM \`${OFF_DB}\`.flyway_schema_history `
    + `WHERE success = 1 AND CAST(version AS UNSIGNED) BETWEEN 39 AND 42;`)
  ok(migrated === '0', 'Flyway 未执行 V39~V42', migrated)

  const maxVersion = sql(`SELECT MAX(CAST(version AS UNSIGNED)) FROM \`${OFF_DB}\`.flyway_schema_history WHERE success = 1;`)
  ok(Number(maxVersion) >= 18, '基座 / 其它模块迁移照常执行', maxVersion)
}

function sql(query) {
  const output = execFileSync(MYSQL, [
    '--host=127.0.0.1',
    '--port=3307',
    '--user=lingxiao',
    '--password=lingxiao123',
    '--default-character-set=utf8mb4',
    '-N',
    '-e',
    query
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  return output.trim()
}

// =====================================================================
// 启用态：语言 / 键 / 译文 / 发布 / 回滚 / CSV / 覆盖率 / 缺失
// =====================================================================
const DEMO_KEY = 'demo.i18n.selftest'
const DEMO_NS = 'demo'
const DEMO_DEFAULT = 'Self test {0}'
const EN_V1 = 'Self test {0} v1'
const EN_V2 = 'Self test {0} v2'
const ZH_V1 = '自检 {0} v1'
const NOTFOUND_KEY = 'i18n.language.notfound'

async function login() {
  const res = await request('POST', '/admin/auth/login', {
    body: { username: 'admin', password: 'admin123' },
    headers: loginHeaders()
  })
  ok(res.json && res.json.code === 0 && res.json.data?.tokenValue, '管理员登录', res.json)
  token = res.json?.data?.tokenValue
}

function flatten(list, out = []) {
  for (const item of list || []) {
    out.push(item)
    flatten(item.children, out)
  }
  return out
}

async function findLanguage(code) {
  const res = await api('GET', '/admin/i18n/language/list')
  return (res.json?.data || []).find((item) => item.languageCode === code)
}

async function findKey(keyCode) {
  const res = await api('GET', `/admin/i18n/key/list?pageNum=1&pageSize=20&keyword=${encodeURIComponent(keyCode)}`)
  return (res.json?.data?.records || []).find((item) => item.keyCode === keyCode)
}

async function saveValues(items) {
  return api('POST', '/admin/i18n/value/batch', { body: { items } })
}

async function publish(languageCode, namespace) {
  return api('POST', '/admin/i18n/pack/publish', { body: { languageCode, namespace } })
}

async function rollback(packId) {
  return api('POST', '/admin/i18n/pack/rollback', { body: { packId } })
}

async function portalPack(lang, ns, headers = {}) {
  return request('GET', `/portal/i18n/${encodeURIComponent(lang)}/${encodeURIComponent(ns)}`, { headers })
}

async function unknownLanguageMsg(langHeaders) {
  return request('GET', '/portal/i18n/zz-ZZ/error', { headers: langHeaders })
}

async function verifyOn() {
  console.log(`\n多语言启用态验收：${BASE}\n`)
  await login()

  // ---------- 菜单 / 权限 ----------
  const menus = await api('GET', '/admin/auth/menus')
  const flat = flatten(menus.json?.data)
  const paths = flat.map((m) => m.path)
  ok(menus.json?.code === 0, '菜单接口正常', menus.json?.code)
  ok(paths.includes('/i18n/language'), '菜单包含语言管理', paths)
  ok(paths.includes('/i18n/translation'), '菜单包含翻译维护', paths)
  ok(paths.includes('/i18n/pack'), '菜单包含语言包', paths)
  ok(flat.filter((m) => m.moduleCode === 'i18n').length >= 4, '多语言菜单按模块下发')

  const noToken = await request('GET', '/admin/i18n/key/list')
  ok(noToken.status !== 404 && (noToken.json?.code ?? 0) !== 0, '后台接口未登录被拒绝', {
    status: noToken.status, body: noToken.text.slice(0, 120)
  })

  // ---------- 语言管理 ----------
  const zh = await findLanguage('zh-CN')
  const en = await findLanguage('en-US')
  ok(zh?.isDefault === 1, '默认语言为 zh-CN', zh)
  ok(en?.status === '0', 'en-US 默认启用', en)

  const defaultLock = await api('PUT', `/admin/i18n/language/${zh.languageId}/status?status=1`)
  ok(defaultLock.json?.code !== 0 && messageKeyOf(defaultLock) === 'i18n.language.default.locked',
    '默认语言不可停用', defaultLock.json)

  const inUse = await api('DELETE', `/admin/i18n/language/${en.languageId}`)
  ok(inUse.json?.code !== 0 && messageKeyOf(inUse) === 'i18n.language.inuse',
    '有译文的语言不可删除', inUse.json)

  let ja = await findLanguage('ja-JP')
  if (ja) {
    await api('DELETE', `/admin/i18n/language/${ja.languageId}`)
    ja = await findLanguage('ja-JP')
  }
  const createLang = await api('POST', '/admin/i18n/language', {
    body: {
      languageCode: 'ja-JP', languageName: '日本語', nativeName: '日本語',
      locale: 'ja_JP', direction: 'ltr', status: '0', sort: 9
    }
  })
  ok(createLang.json?.code === 0, '新增语言 ja-JP', createLang.json)
  const jaId = createLang.json?.data
  ja = await findLanguage('ja-JP')
  ok(ja && ja.languageName === '日本語', '语言列表出现 ja-JP')

  const editLang = await api('PUT', `/admin/i18n/language/${jaId}`, {
    body: {
      languageCode: 'ja-JP', languageName: '日本語（更新）', nativeName: '日本語',
      locale: 'ja_JP', direction: 'ltr', status: '0', sort: 9
    }
  })
  ok(editLang.json?.code === 0, '编辑语言', editLang.json)
  ok((await findLanguage('ja-JP'))?.languageName === '日本語（更新）', '语言编辑生效')

  const disable = await api('PUT', `/admin/i18n/language/${jaId}/status?status=1`)
  ok(disable.json?.code === 0, '停用语言', disable.json)
  const options = await api('GET', '/admin/i18n/language/options')
  ok(!(options.json?.data || []).some((item) => item.languageCode === 'ja-JP'), '停用语言不出现在下拉')

  const enable = await api('PUT', `/admin/i18n/language/${jaId}/status?status=0`)
  ok(enable.json?.code === 0, '重新启用语言', enable.json)

  const deleteLang = await api('DELETE', `/admin/i18n/language/${jaId}`)
  ok(deleteLang.json?.code === 0 && !(await findLanguage('ja-JP')), '删除语言', deleteLang.json)

  // ---------- 翻译键 ----------
  let demoKey = await findKey(DEMO_KEY)
  if (demoKey) {
    const update = await api('PUT', `/admin/i18n/key/${demoKey.keyId}`, {
      body: { namespace: DEMO_NS, keyCode: DEMO_KEY, defaultValue: DEMO_DEFAULT, moduleCode: DEMO_NS, status: '0', remark: 'verify script' }
    })
    ok(update.json?.code === 0, '复用并重置自检键', update.json)
    demoKey = await findKey(DEMO_KEY)
  } else {
    const create = await api('POST', '/admin/i18n/key', {
      body: { namespace: DEMO_NS, keyCode: DEMO_KEY, defaultValue: DEMO_DEFAULT, moduleCode: DEMO_NS, status: '0', remark: 'verify script' }
    })
    ok(create.json?.code === 0, '新增自检键', create.json)
    demoKey = await findKey(DEMO_KEY)
  }
  ok(demoKey && demoKey.defaultValue === DEMO_DEFAULT, '翻译键可查询', demoKey)

  const mismatch = await saveValues([{ keyId: demoKey.keyId, languageCode: 'en-US', valueText: 'Mismatch {1}' }])
  ok(mismatch.json?.code !== 0 && messageKeyOf(mismatch) === 'i18n.value.placeholder.mismatch',
    '占位符不一致拒绝保存', mismatch.json)

  const saveDraft = await saveValues([
    { keyId: demoKey.keyId, languageCode: 'en-US', valueText: 'Self test {0} draft' },
    { keyId: demoKey.keyId, languageCode: 'zh-CN', valueText: ZH_V1 }
  ])
  ok(saveDraft.json?.code === 0, '批量保存草稿', saveDraft.json)

  const draftBeforePublish = await portalPack('en-US', DEMO_NS)
  ok((draftBeforePublish.json?.data?.messages || {})[DEMO_KEY] !== 'Self test {0} draft',
    '未发布草稿不进入门户语言包', draftBeforePublish.json?.data?.messages?.[DEMO_KEY])

  const preparePublish = await saveValues([{ keyId: demoKey.keyId, languageCode: 'en-US', valueText: EN_V1 }])
  ok(preparePublish.json?.code === 0, '准备发布内容 v1', preparePublish.json)

  // ---------- 发布 en-US ----------
  const publish1 = await publish('en-US', DEMO_NS)
  ok(publish1.json?.code === 0 && publish1.json.data.length === 1, '发布 en-US / demo', publish1.json)
  const pack1 = publish1.json?.data?.[0]
  ok(pack1 && pack1.version >= 1 && pack1.checksum?.length === 64, '发布返回版本与校验和', pack1)

  const live1 = await portalPack('en-US', DEMO_NS)
  ok(live1.json?.code === 0 && live1.json.data.messages[DEMO_KEY] === EN_V1,
    '发布后门户读到 v1', live1.json?.data?.messages?.[DEMO_KEY])
  ok(String(live1.json?.data?.version) === String(pack1.version), '门户版本号与发布一致',
    { portal: live1.json?.data?.version, pack: pack1.version })

  const versionApi = await api('GET', `/portal/i18n/version?lang=en-US&namespace=${DEMO_NS}`)
  ok(versionApi.json?.code === 0 && String(versionApi.json.data.version) === String(pack1.version),
    '门户版本接口', versionApi.json?.data)

  const etag = live1.headers.get('etag')
  ok(!!etag, '语言包返回 ETag', etag)
  const notModified = await portalPack('en-US', DEMO_NS, { 'If-None-Match': etag })
  ok(notModified.status === 304, 'ETag 命中返回 304', notModified.status)

  // ---------- 发布 zh-CN ----------
  const publishZh = await publish('zh-CN', DEMO_NS)
  ok(publishZh.json?.code === 0, '发布 zh-CN / demo', publishZh.json)
  const liveZh = await portalPack('zh-CN', DEMO_NS)
  ok(liveZh.json?.data?.messages?.[DEMO_KEY] === ZH_V1, '中文语言包内容正确',
    liveZh.json?.data?.messages?.[DEMO_KEY])

  // ---------- 草稿不影响线上 ----------
  const saveV2 = await saveValues([{ keyId: demoKey.keyId, languageCode: 'en-US', valueText: EN_V2 }])
  ok(saveV2.json?.code === 0, '保存 v2 草稿', saveV2.json)
  const stillV1 = await portalPack('en-US', DEMO_NS)
  ok(stillV1.json?.data?.messages?.[DEMO_KEY] === EN_V1, '草稿不影响线上（仍为 v1）',
    stillV1.json?.data?.messages?.[DEMO_KEY])

  const publish2 = await publish('en-US', DEMO_NS)
  ok(publish2.json?.code === 0, '再次发布 v2', publish2.json)
  const pack2 = publish2.json?.data?.[0]
  ok(pack2 && pack2.version === pack1.version + 1, '版本号递增', { v1: pack1.version, v2: pack2?.version })
  const live2 = await portalPack('en-US', DEMO_NS)
  ok(live2.json?.data?.messages?.[DEMO_KEY] === EN_V2, '发布后门户读到 v2',
    live2.json?.data?.messages?.[DEMO_KEY])

  // ---------- 回滚 ----------
  const rollbackRes = await rollback(pack1.packId)
  ok(rollbackRes.json?.code === 0 && String(rollbackRes.json.data.version) === String(pack1.version),
    '回滚到 v1', rollbackRes.json)
  const liveAfterRollback = await portalPack('en-US', DEMO_NS)
  ok(liveAfterRollback.json?.data?.messages?.[DEMO_KEY] === EN_V1, '回滚恢复译文 v1',
    liveAfterRollback.json?.data?.messages?.[DEMO_KEY])

  const current = await api('GET', `/admin/i18n/pack/current?languageCode=en-US&namespace=${DEMO_NS}`)
  const currentDemo = (current.json?.data || []).find((item) => item.namespace === DEMO_NS)
  ok(currentDemo && String(currentDemo.version) === String(pack1.version) && currentDemo.status === '1',
    '当前语言包指向回滚版本', currentDemo)

  // ---------- CSV 导出 / 导入 ----------
  const exportRes = await api('GET', `/admin/i18n/export?namespace=${DEMO_NS}&languages=en-US&languages=zh-CN`)
  ok(exportRes.status === 200 && /text\/csv/.test(exportRes.headers.get('content-type') || ''),
    'CSV 导出响应头', exportRes.headers.get('content-type'))
  const rawExport = await fetch(BASE + `/admin/i18n/export?namespace=${DEMO_NS}&languages=en-US&languages=zh-CN`, {
    headers: authed()
  })
  const rawBytes = new Uint8Array(await rawExport.arrayBuffer())
  ok(rawBytes[0] === 0xEF && rawBytes[1] === 0xBB && rawBytes[2] === 0xBF,
    'CSV 带 UTF-8 BOM', Array.from(rawBytes.slice(0, 3)))
  ok(exportRes.text.includes(DEMO_KEY) && exportRes.text.includes(EN_V1), 'CSV 包含自检译文')

  const csvEdited = exportRes.text.replace(EN_V1, 'Self test {0} csv')
  const form = new FormData()
  form.append('file', new Blob([csvEdited], { type: 'text/csv' }), 'i18n-demo.csv')
  const importRes = await api('POST', '/admin/i18n/import', { form })
  ok(importRes.json?.code === 0 && importRes.json.data.success === 1 && importRes.json.data.failed === 0,
    'CSV 导入成功', importRes.json?.data)

  const afterImport = await findKey(DEMO_KEY)
  ok(afterImport?.values?.['en-US'] === 'Self test {0} csv', 'CSV 导入写入草稿',
    afterImport?.values)

  const packAfterDraftImport = await portalPack('en-US', DEMO_NS)
  ok(packAfterDraftImport.json?.data?.messages?.[DEMO_KEY] === EN_V1, 'CSV 导入草稿也不影响线上',
    packAfterDraftImport.json?.data?.messages?.[DEMO_KEY])

  const restoreDraft = await saveValues([{ keyId: demoKey.keyId, languageCode: 'en-US', valueText: EN_V1 }])
  ok(restoreDraft.json?.code === 0, '恢复工作副本为 v1', restoreDraft.json)

  // ---------- 数据库译文优先于资源包（X-Lang 生效） ----------
  const notfound = await findKey(NOTFOUND_KEY)
  ok(notfound, '找到运行时消息键 i18n.language.notfound', notfound)
  const enOriginal = notfound.values['en-US']
  const zhOriginal = notfound.values['zh-CN']
  ok(enOriginal === 'Language not found' && zhOriginal === '语言不存在', '记录原始文案',
    { enOriginal, zhOriginal })

  const custom1 = 'SELFTEST language missing'
  const custom2 = 'SELFTEST language missing v2'
  const saveCustom1 = await saveValues([{ keyId: notfound.keyId, languageCode: 'en-US', valueText: custom1 }])
  ok(saveCustom1.json?.code === 0, '保存运行时消息草稿', saveCustom1.json)

  const draftMsg = await unknownLanguageMsg({ 'X-Lang': 'en-US' })
  ok(draftMsg.json?.msg === enOriginal, '运行时消息草稿不影响线上',
    draftMsg.json?.msg)

  const publishMsg1 = await publish('en-US', 'i18n')
  ok(publishMsg1.json?.code === 0 && publishMsg1.json.data.length === 1, '发布 i18n 命名空间',
    publishMsg1.json)
  const msgPack1 = publishMsg1.json?.data?.[0]

  const dbMsg1 = await unknownLanguageMsg({ 'X-Lang': 'en-US' })
  ok(dbMsg1.json?.msg === custom1, '数据库已发布译文优先于 properties', dbMsg1.json?.msg)
  const zhMsg = await unknownLanguageMsg({ 'X-Lang': 'zh-CN' })
  ok(zhMsg.json?.msg === zhOriginal, '未修改语言仍读原译文', zhMsg.json?.msg)

  const saveCustom2 = await saveValues([{ keyId: notfound.keyId, languageCode: 'en-US', valueText: custom2 }])
  ok(saveCustom2.json?.code === 0, '保存运行时消息 v2 草稿', saveCustom2.json)
  const stillCustom1 = await unknownLanguageMsg({ 'X-Lang': 'en-US' })
  ok(stillCustom1.json?.msg === custom1, '再次保存草稿仍不生效',
    stillCustom1.json?.msg)

  const publishMsg2 = await publish('en-US', 'i18n')
  ok(publishMsg2.json?.code === 0, '再次发布 i18n 命名空间', publishMsg2.json)
  const msgPack2 = publishMsg2.json?.data?.[0]
  const dbMsg2 = await unknownLanguageMsg({ 'X-Lang': 'en-US' })
  ok(dbMsg2.json?.msg === custom2, '再次发布后读到 v2', dbMsg2.json?.msg)

  const rollbackMsg = await rollback(msgPack1.packId)
  ok(rollbackMsg.json?.code === 0, '回滚 i18n 语言包', rollbackMsg.json)
  const msgAfterRollback = await unknownLanguageMsg({ 'X-Lang': 'en-US' })
  ok(msgAfterRollback.json?.msg === custom1, '回滚后运行时消息恢复', msgAfterRollback.json?.msg)

  // 恢复原始文案并发布，避免验收脚本污染线上
  await saveValues([{ keyId: notfound.keyId, languageCode: 'en-US', valueText: enOriginal }])
  const restorePublish = await publish('en-US', 'i18n')
  ok(restorePublish.json?.code === 0, '恢复原始运行时文案', restorePublish.json)
  const restored = await unknownLanguageMsg({ 'X-Lang': 'en-US' })
  ok(restored.json?.msg === enOriginal, '恢复后英文文案正确', restored.json?.msg)

  // 三种取语言方式
  const byHeader = await unknownLanguageMsg({ 'X-Lang': 'en-US' })
  const byAccept = await unknownLanguageMsg({ 'Accept-Language': 'en-US,en;q=0.9' })
  const byParamEn = await request('GET', '/portal/i18n/zz-ZZ/error?lang=en-US')
  ok(byHeader.json?.msg === enOriginal, 'X-Lang 解析生效', byHeader.json?.msg)
  ok(byParamEn.json?.msg === enOriginal, '?lang= 解析生效', byParamEn.json?.msg)
  ok(byAccept.json?.msg === enOriginal, 'Accept-Language 解析生效', byAccept.json?.msg)
  const byHeaderZh = await unknownLanguageMsg({ 'X-Lang': 'zh-CN' })
  ok(byHeaderZh.json?.msg === zhOriginal, 'X-Lang 中文解析生效', byHeaderZh.json?.msg)

  // ---------- 覆盖率 / 缺失 ----------
  const coverage = await api('GET', '/admin/i18n/coverage?languageCode=en-US&namespace=demo')
  const demoCoverage = (coverage.json?.data || []).find((row) => row.namespace === DEMO_NS)
  ok(coverage.json?.code === 0 && demoCoverage && demoCoverage.totalKeys >= 1
    && demoCoverage.translated >= 1 && demoCoverage.coverage === 100,
  '覆盖率统计', demoCoverage)

  const missing = await api('GET', '/admin/i18n/missing?pageNum=1&pageSize=10')
  ok(missing.json?.code === 0 && typeof missing.json.data.total !== 'undefined', '缺失记录分页',
    missing.json?.data)

  // ---------- 键删除约束 ----------
  const deleteInUse = await api('DELETE', `/admin/i18n/key/${demoKey.keyId}`)
  ok(deleteInUse.json?.code !== 0 && messageKeyOf(deleteInUse) === 'i18n.key.inuse',
    '有译文的键不可删除', deleteInUse.json)

  const tempKeyCode = `demo.i18n.temp.${Date.now()}`
  const tempCreate = await api('POST', '/admin/i18n/key', {
    body: { namespace: DEMO_NS, keyCode: tempKeyCode, defaultValue: 'temp', moduleCode: DEMO_NS, status: '0' }
  })
  const tempKeyId = tempCreate.json?.data
  const tempDelete = await api('DELETE', `/admin/i18n/key/${tempKeyId}`)
  ok(tempCreate.json?.code === 0 && tempDelete.json?.code === 0, '无译文的键可删除', {
    create: tempCreate.json, remove: tempDelete.json
  })

  // 清理：有译文的 demo 键只停用，不物理删除（保留历史语言包可回滚）
  const disableKey = await api('PUT', `/admin/i18n/key/${demoKey.keyId}`, {
    body: { namespace: DEMO_NS, keyCode: DEMO_KEY, defaultValue: DEMO_DEFAULT, moduleCode: DEMO_NS, status: '1', remark: 'verify script (disabled)' }
  })
  ok(disableKey.json?.code === 0, '验收键停用保留', disableKey.json)
}

// =====================================================================
try {
  if (OFF) {
    await verifyOff()
  } else {
    await verifyOn()
  }
} catch (e) {
  fail += 1
  console.error('\n脚本异常：', e)
}

console.log(`\n结果：${pass} 通过 / ${fail} 失败`)
process.exit(fail ? 1 : 0)
