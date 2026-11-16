import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

/**
 * 把 UEditor Plus 的静态资源同步到 `public/ueditor-plus`。
 *
 * <p>UEditor 不是 ESM 组件：它运行时要按 URL 去取 `themes/`、`lang/`、`dialogs/`
 * 等目录，所以必须有一份可以直接被浏览器访问的静态文件。npm 包里的
 * `dist-min/` 正好是这个结构，这里在 `dev` / `build` 前复制一次即可。</p>
 *
 * <p>没有直接提交进仓库：资源有上百个文件，版本跟 npm 包走。
 * `public/ueditor-plus/` 已加入 .gitignore，由本脚本生成。</p>
 */

const pkgPath = path.resolve('node_modules/ueditor-plus-main/package.json')
if (!existsSync(pkgPath)) {
  console.error('[ueditor] 未找到 ueditor-plus-main，请先执行 npm install')
  process.exit(1)
}

const pkg = JSON.parse(readFileSync(pkgPath, 'utf8'))
const source = path.resolve('node_modules/ueditor-plus-main/dist-min')
const target = path.resolve('public/ueditor-plus')
const marker = path.join(target, '.version')

if (!existsSync(source)) {
  console.error('[ueditor] 未找到 dist-min 目录，ueditor-plus-main 包结构可能已变化')
  process.exit(1)
}

if (existsSync(marker) && readFileSync(marker, 'utf8').trim() === pkg.version) {
  console.log(`[ueditor] 静态资源已是最新版本 ${pkg.version}，跳过复制`)
  process.exit(0)
}

mkdirSync(path.dirname(target), { recursive: true })
rmSync(target, { recursive: true, force: true })
cpSync(source, target, { recursive: true })
writeFileSync(marker, pkg.version, 'utf8')
console.log(`[ueditor] 已同步 UEditor Plus ${pkg.version} 到 public/ueditor-plus`)
