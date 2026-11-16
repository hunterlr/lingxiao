import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const source = path.join(__dirname, '02-feature-list.md')
const target = path.join(__dirname, 'feature-list.csv')

const domains = {
  SYS: '系统基座',
  CMS: 'CMS',
  MEM: '会员与前台',
  WF: '工作流',
  SAAS: 'SaaS 多租户',
  OPEN: '开放平台/中台',
  I18N: '多语言',
  DB: '多数据库',
  OPS: '工程与运维',
}

const rows = []
let current = ''

for (const line of fs.readFileSync(source, 'utf8').split(/\r?\n/)) {
  const heading = line.match(/^##\s+\d+\.\s+(.+?)(?:（(SYS|CMS|MEM|WF|SAAS|OPEN|I18N|DB|OPS)）)?\s*$/)
  if (heading) {
    current = heading[2] || current
    continue
  }

  const cells = line.split('|').map((s) => s.trim())
  if (cells.length < 7) continue

  const id = cells[1]
  const match = id.match(/^(SYS|CMS|MEM|WF|SAAS|OPEN|I18N|DB|OPS)-\d{3}$/)
  if (!match) continue

  rows.push([domains[match[1]], id, cells[2], cells[3], cells[4], cells[5], cells[6]])
}

const esc = (v) => `"${String(v).replace(/"/g, '""')}"`
const csv = ['域,编号,功能,说明,优先级,阶段,端', ...rows.map((r) => r.map(esc).join(','))].join('\r\n')

fs.writeFileSync(target, '\uFEFF' + csv, 'utf8')
console.log(`generated ${target}: ${rows.length} rows`)
