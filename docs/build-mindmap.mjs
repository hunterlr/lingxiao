import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const source = path.join(__dirname, '03-mindmap.md')
const target = path.join(__dirname, 'mindmap.html')

const md = fs.readFileSync(source, 'utf8')
const lines = md.split(/\r?\n/)

const sections = []
let current = { title: '总览', blocks: [] }
let inMermaid = false
let buffer = []

for (const line of lines) {
  const trimmed = line.trim()

  if (trimmed === '```mermaid') {
    inMermaid = true
    buffer = []
    continue
  }

  if (inMermaid && trimmed === '```') {
    if (buffer.length) current.blocks.push(buffer.join('\n'))
    inMermaid = false
    continue
  }

  if (inMermaid) {
    buffer.push(line)
    continue
  }

  const heading = line.match(/^##\s+(.+)$/)
  if (heading) {
    if (current.blocks.length) sections.push(current)
    current = { title: heading[1].replace(/^\d+\.\s*/, ''), blocks: [] }
  }
}

if (current.blocks.length) sections.push(current)

const esc = (s) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const nav = sections
  .map((s, i) => `<a href="#s${i}">${esc(s.title)}</a>`)
  .join('\n        ')

const cards = sections
  .map(
    (s, i) => `
      <section class="card" id="s${i}">
        <div class="card-head">
          <h2>${esc(s.title)}</h2>
          <button class="zoom" data-target="d${i}" title="放大 / 还原">⤢</button>
        </div>
        ${s.blocks
          .map(
            (b, j) => `<div class="diagram" id="d${i}-${j}">
          <pre class="mermaid">${esc(b)}</pre>
        </div>`
          )
          .join('\n        ')}
      </section>`
  )
  .join('\n')

const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>凌霄平台 · 思维导图与架构图</title>
  <style>
    :root {
      --bg: #0b1220;
      --panel: #111c30;
      --panel-2: #16233b;
      --line: #24344f;
      --text: #e6eefc;
      --muted: #93a4bf;
      --accent: #38bdf8;
      --accent-2: #a78bfa;
    }
    * { box-sizing: border-box; }
    html { scroll-behavior: smooth; }
    body {
      margin: 0;
      background: radial-gradient(1200px 600px at 80% -10%, #1b2b4a 0%, var(--bg) 55%);
      color: var(--text);
      font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", system-ui, sans-serif;
      line-height: 1.6;
    }
    .layout { display: flex; min-height: 100vh; }
    aside {
      width: 280px;
      flex: 0 0 280px;
      position: sticky;
      top: 0;
      height: 100vh;
      overflow: auto;
      padding: 28px 20px;
      border-right: 1px solid var(--line);
      background: rgba(10, 18, 32, .78);
      backdrop-filter: blur(10px);
    }
    .brand { font-size: 18px; font-weight: 700; letter-spacing: .5px; }
    .brand span { color: var(--accent); }
    .sub { color: var(--muted); font-size: 12px; margin: 6px 0 22px; }
    nav { display: flex; flex-direction: column; gap: 2px; }
    nav a {
      color: var(--muted);
      text-decoration: none;
      font-size: 13px;
      padding: 8px 10px;
      border-radius: 8px;
      border-left: 2px solid transparent;
      transition: .18s;
    }
    nav a:hover { color: var(--text); background: var(--panel-2); border-left-color: var(--accent); }
    main { flex: 1; min-width: 0; padding: 34px 40px 80px; }
    .hero { margin-bottom: 28px; }
    .hero h1 { font-size: 26px; margin: 0 0 8px; }
    .hero p { color: var(--muted); margin: 0; font-size: 13px; }
    .badges { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
    .badge {
      font-size: 12px; color: var(--accent);
      border: 1px solid rgba(56, 189, 248, .35);
      background: rgba(56, 189, 248, .08);
      padding: 4px 10px; border-radius: 999px;
    }
    .card {
      background: linear-gradient(180deg, rgba(22, 35, 59, .92), rgba(17, 28, 48, .92));
      border: 1px solid var(--line);
      border-radius: 16px;
      padding: 18px 20px 24px;
      margin-bottom: 22px;
      box-shadow: 0 18px 50px rgba(0, 0, 0, .22);
    }
    .card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .card h2 { font-size: 16px; margin: 0 0 10px; color: #dbeafe; }
    .zoom {
      border: 1px solid var(--line);
      background: var(--panel-2);
      color: var(--muted);
      width: 32px; height: 32px;
      border-radius: 8px; cursor: pointer;
    }
    .zoom:hover { color: var(--text); border-color: var(--accent); }
    .diagram {
      overflow: auto;
      padding: 12px;
      border-radius: 12px;
      background: rgba(8, 14, 26, .55);
      border: 1px solid rgba(36, 52, 79, .65);
    }
    .diagram.expanded .mermaid { transform: scale(1.18); transform-origin: top left; }
    .mermaid { display: flex; justify-content: center; min-width: 520px; }
    .mermaid svg { max-width: 100%; height: auto; }
    footer { color: var(--muted); font-size: 12px; padding-top: 8px; }
    .error {
      color: #fecaca; background: rgba(127, 29, 29, .35);
      border: 1px solid rgba(248, 113, 113, .4);
      padding: 10px 14px; border-radius: 10px; font-size: 13px;
    }
    @media (max-width: 900px) {
      .layout { display: block; }
      aside { position: static; width: auto; height: auto; border-right: 0; border-bottom: 1px solid var(--line); }
      nav { flex-direction: row; flex-wrap: wrap; }
      main { padding: 22px 16px 60px; }
      .mermaid { min-width: 420px; }
    }
    @media print {
      aside, .zoom { display: none; }
      body { background: #fff; color: #111; }
      .card { break-inside: avoid; box-shadow: none; border-color: #ddd; background: #fff; }
      .diagram { background: #fff; }
    }
  </style>
</head>
<body>
  <div class="layout">
    <aside>
      <div class="brand">凌霄 <span>LingXiao</span></div>
      <div class="sub">思维导图与关键架构图 · v0.1</div>
      <nav>
        ${nav}
      </nav>
    </aside>
    <main>
      <div class="hero">
        <h1>凌霄平台 · 全景图集</h1>
        <p>由 <code>docs/03-mindmap.md</code> 自动生成。修改 Markdown 后执行 <code>node docs/build-mindmap.mjs</code> 重新生成。</p>
        <div class="badges">
          <span class="badge">后台基座</span>
          <span class="badge">CMS</span>
          <span class="badge">会员前台</span>
          <span class="badge">工作流</span>
          <span class="badge">SaaS 多租户</span>
          <span class="badge">中台</span>
          <span class="badge">JDK 8 / 11 / 17</span>
        </div>
      </div>
      ${cards}
      <footer>Mermaid 渲染依赖 CDN；离线环境请使用支持 Mermaid 的 Markdown 工具打开源文件。</footer>
    </main>
  </div>

  <script type="module">
    import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs'

    mermaid.initialize({
      startOnLoad: false,
      theme: 'dark',
      securityLevel: 'loose',
      themeVariables: {
        background: '#111c30',
        primaryColor: '#1e3a5f',
        primaryTextColor: '#e6eefc',
        primaryBorderColor: '#38bdf8',
        lineColor: '#64748b',
        secondaryColor: '#312e81',
        tertiaryColor: '#164e63',
        fontFamily: '"Microsoft YaHei", "PingFang SC", sans-serif'
      },
      flowchart: { curve: 'basis', htmlLabels: true },
      mindmap: { padding: 12 }
    })

    try {
      await mermaid.run({ querySelector: '.mermaid' })
    } catch (e) {
      const box = document.createElement('div')
      box.className = 'error'
      box.textContent = 'Mermaid 渲染失败：' + e.message
      document.querySelector('main').prepend(box)
    }

    document.querySelectorAll('.zoom').forEach((btn) => {
      btn.addEventListener('click', () => {
        const card = btn.closest('.card')
        card.querySelectorAll('.diagram').forEach((d) => d.classList.toggle('expanded'))
      })
    })
  </script>
</body>
</html>
`

fs.writeFileSync(target, html, 'utf8')
console.log(`generated ${target} (${sections.length} sections, ${sections.reduce((n, s) => n + s.blocks.length, 0)} diagrams)`)
