// 架构图渲染：mermaid -> docs/images/architecture.png
// 用法：npm i mermaid playwright-core && node scripts/render-architecture.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright-core');

(async () => {
  const mmd = fs.readFileSync(path.resolve(__dirname, '..', 'docs', 'images', 'arch-diagram.mmd'), 'utf8');
  const mermaidJs = path.resolve(__dirname, '..', 'node_modules', 'mermaid', 'dist', 'mermaid.min.js');
  if (!fs.existsSync(mermaidJs)) {
    console.error('mermaid 未安装：请先 npm i mermaid');
    process.exit(1);
  }

  const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>
  body { margin: 0; padding: 24px; background: #fff; font-family: "Microsoft YaHei", sans-serif; }
  #container { display: inline-block; background: #fff; }
</style></head>
<body>
  <div id="container"><pre class="mermaid">${mmd}</pre></div>
  <script>${fs.readFileSync(mermaidJs, 'utf8')}</script>
  <script>
    mermaid.initialize({ startOnLoad: true, theme: 'default', flowchart: { useMaxWidth: false, htmlLabels: true } });
    window.renderDone = new Promise((resolve) => {
      const timer = setInterval(() => {
        if (document.querySelector('#container svg')) { clearInterval(timer); resolve(); }
      }, 100);
      setTimeout(resolve, 10000);
    });
  </script>
</body></html>`;

  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage();
  await page.setContent(html, { waitUntil: 'load' });
  await page.waitForSelector('#container svg', { timeout: 15000 });
  const svg = await page.locator('#container');
  await svg.screenshot({ path: path.resolve(__dirname, '..', 'docs', 'images', 'architecture.png') });
  await browser.close();
  console.log('architecture.png rendered');
})().catch((e) => { console.error('RENDER FAILED:', e.message); process.exit(1); });
