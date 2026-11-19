import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

/**
 * 语言包自检（本地 / CI 都可跑）：
 *   1. zh-CN 与 en-US 的键集合必须完全一致；
 *   2. 源码里 t('xxx') 引用的键必须在 zh-CN 里存在；
 *   3. 打印未被引用的键（动态拼接的键会误报，仅提示）。
 *
 * 用法：node scripts/check-i18n-keys.mjs
 */
const root = path.resolve('src')
const zh = (await import(pathToFileURL(path.join(root, 'i18n/zh-CN.js')).href)).default
const en = (await import(pathToFileURL(path.join(root, 'i18n/en-US.js')).href)).default

const dottedLeaves = new Set()
function flatten(object, prefix = '', result = new Map()) {
  for (const [key, value] of Object.entries(object)) {
    const full = prefix ? `${prefix}.${key}` : key
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      flatten(value, full, result)
    } else {
      // vue-i18n 把点号当路径分隔符；叶子键自身带点会让 t() 永远取不到，
      // 只能静默回退到数据库原文（参数名乱码就是这么来的）。
      if (key.includes('.')) dottedLeaves.add(full)
      result.set(full, value)
    }
  }
  return result
}

const zhKeys = flatten(zh)
const enKeys = flatten(en)

const zhOnly = [...zhKeys.keys()].filter((key) => !enKeys.has(key))
const enOnly = [...enKeys.keys()].filter((key) => !zhKeys.has(key))

console.log(`zh=${zhKeys.size} en=${enKeys.size}`)
console.log('zh-only=' + (zhOnly.join(', ') || 'none'))
console.log('en-only=' + (enOnly.join(', ') || 'none'))
console.log('dotted-leaf=' + ([...dottedLeaves].join(', ') || 'none'))

const files = []
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full)
    else if (/\.(vue|js)$/.test(entry.name) && !full.includes(`${path.sep}i18n${path.sep}`)) files.push(full)
  }
}
walk(root)

const used = new Set()
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8')
  // 只收集完整的键名；t('page.job.' + preset.key) 这类动态拼接不参与“缺失”判断
  for (const match of text.matchAll(/\bt\(\s*'([A-Za-z0-9_.:]+)'\s*[,)]/g)) used.add(match[1])
  for (const match of text.matchAll(/titleKey:\s*'([A-Za-z0-9_.:]+)'/g)) used.add(match[1])
}

const missing = [...used].filter((key) => !zhKeys.has(key)).sort()
console.log(`used=${used.size}`)
console.log('missing=' + (missing.join(', ') || 'none'))

const unused = [...zhKeys.keys()].filter((key) => !used.has(key) && !key.startsWith('error.')).sort()
console.log('unused=' + (unused.join(', ') || 'none'))

if (zhOnly.length || enOnly.length || missing.length || dottedLeaves.size) {
  process.exitCode = 1
}
