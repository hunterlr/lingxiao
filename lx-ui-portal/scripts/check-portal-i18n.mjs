import fs from 'node:fs'
import path from 'node:path'

/**
 * 门户文案自检。
 *
 * <p>三件事：</p>
 * <ol>
 *   <li>中英文键集合必须完全一致（少一条翻译就失败）；</li>
 *   <li>`t('xxx')` 引用的键必须在中文表里存在（防拼错）；</li>
 *   <li>模板里不许残留硬编码中文（注释、CSS、品牌名除外）。</li>
 * </ol>
 *
 * <p>会员中心相关页面是第一刀之外的范围，先列入 `PENDING`；
 * 它们改完后从列表里删掉即可，脚本会开始检查。</p>
 */

const ROOT = process.cwd()
const SCAN = ['pages', 'layouts', 'components', 'error.vue']
/** 会员中心已于 2026-11-27（刀 A2）完成键化；保留空集合便于以后临时豁免 */
const PENDING = new Set([])
/** 品牌名允许保留中文（页脚署名与 Logo 占位字） */
const ALLOW_WORDS = ['凌霄', '凌']

function walk(target, out = []) {
  const full = path.join(ROOT, target)
  if (!fs.existsSync(full)) return out
  const stat = fs.statSync(full)
  if (stat.isDirectory()) {
    for (const name of fs.readdirSync(full)) {
      if (name === 'node_modules' || name === '.output' || name === 'dist') continue
      walk(path.join(target, name), out)
    }
  } else if (target.endsWith('.vue')) {
    out.push(target.split(path.sep).join('/'))
  }
  return out
}

/** 从 TS 语言包里读顶层键（`  'key': 'value'`），不执行文件。 */
function readKeys(file) {
  const text = fs.readFileSync(path.join(ROOT, file), 'utf8')
  const keys = new Map()
  for (const match of text.matchAll(/^\s{2}'([^']+)':\s*'([^']*)'/gm)) {
    keys.set(match[1], match[2])
  }
  return keys
}

function stripNonCopy(source) {
  return source
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
    .replace(/^\s*\*.*$/gm, '')
}

const zh = readKeys('i18n/zh-CN.ts')
const en = readKeys('i18n/en-US.ts')
const problems = []
const warnings = []

for (const key of zh.keys()) {
  if (!en.has(key)) problems.push(`en-US 缺键: ${key}`)
}
for (const key of en.keys()) {
  if (!zh.has(key)) problems.push(`zh-CN 缺键: ${key}`)
}

const files = SCAN.flatMap((target) => walk(target))
const used = new Set()

for (const file of files) {
  if (PENDING.has(file)) continue
  const raw = fs.readFileSync(path.join(ROOT, file), 'utf8')
  const text = stripNonCopy(raw)
  const lines = text.split('\n')
  lines.forEach((line, index) => {
    const found = line.match(/[\u4e00-\u9fa5]+/g) || []
    const leftovers = found.filter((word) => !ALLOW_WORDS.some((allowed) => word.includes(allowed)))
    if (leftovers.length) {
      problems.push(`${file}:${index + 1} 残留中文文案 → ${leftovers.join(' / ')}`)
    }
  })
  for (const match of raw.matchAll(/\bt\(\s*'([^']+)'/g)) {
    used.add(match[1])
    if (!zh.has(match[1])) problems.push(`${file} 使用了不存在的键: ${match[1]}`)
  }
}

// 动态拼接的键（如 t(`auth.oauth.${code}`)）扫描不到，按前缀放行
const DYNAMIC_PREFIXES = ['auth.oauth.']
for (const key of zh.keys()) {
  if (used.has(key)) continue
  if (DYNAMIC_PREFIXES.some((prefix) => key.startsWith(prefix))) continue
  warnings.push(`未使用的键: ${key}`)
}

console.log(`扫描 ${files.length - PENDING.size} 个文件（跳过 ${PENDING.size} 个待改造文件）`)
console.log(`键：zh=${zh.size} en=${en.size}`)
if (warnings.length) {
  console.log(`\n提示（不影响退出码）：\n  ${warnings.join('\n  ')}`)
}
if (problems.length) {
  console.error(`\n发现 ${problems.length} 个问题：\n  ${problems.join('\n  ')}`)
  process.exit(1)
}
console.log('\n门户文案自检通过')