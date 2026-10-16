// M5 五场景端到端浏览器验证（先注册登录，再走 v1 工作区对话）
// 用法：npm i playwright-core && node scripts/e2e-m5.js（需全栈已启动：docker compose up -d）
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');
const { registerAndEnter } = require('./e2e-login');

const BASE = process.env.E2E_BASE_URL || 'http://localhost:5173';
const API = process.env.E2E_API_URL || 'http://localhost:8080';
const SHOTS = path.resolve(__dirname, '..', 'docs', 'images');
const GLOSSARY = path.resolve(__dirname, '..', 'sample-data', 'knowledge', 'business-glossary.md');

async function bootstrapOwnerWorkspace(page) {
  const refreshToken = await page.evaluate(() => localStorage.getItem('huiyi_refresh'));
  const refreshed = await fetch(API + '/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  }).then((res) => res.json());
  const token = refreshed.data.accessToken;
  const created = await fetch(API + '/api/v1/workspaces', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    body: JSON.stringify({ name: 'E2E Demo', code: 'e2e-demo-' + Date.now() }),
  }).then((res) => res.json());
  const workspaceId = created.data.id;
  const form = new FormData();
  form.append('file', new Blob([fs.readFileSync(GLOSSARY)]), 'business-glossary.md');
  const uploaded = await fetch(API + `/api/v1/workspaces/${workspaceId}/knowledge/upload`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: form,
  }).then((res) => res.json());
  if (uploaded.code !== 0) {
    throw new Error('upload failed: ' + JSON.stringify(uploaded));
  }
  await page.evaluate(({ workspaceId, refresh }) => {
    localStorage.setItem('huiyi_workspace_id', String(workspaceId));
    localStorage.setItem('huiyi_refresh', refresh);
  }, { workspaceId, refresh: refreshed.data.refreshToken });
  await page.goto(BASE + '/chat', { waitUntil: 'networkidle' });
  await page.locator('textarea').waitFor({ timeout: 30000 });
}

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const shot = (name) => page.screenshot({ path: path.join(SHOTS, name), fullPage: false });
  const report = {};
  const ask = async (question) => {
    await page.locator('textarea').fill(question);
    await page.getByRole('button', { name: '发送' }).click();
  };

  await registerAndEnter(page, BASE);
  await bootstrapOwnerWorkspace(page);

  // ===== 场景 1：口径问答（RAG + 引用来源） =====
  await ask('什么是有效订单？');
  await page.locator('.reference-tag').first().waitFor({ timeout: 120000 });
  await page.waitForFunction(
    () => !document.querySelector('.cursor-blink'), null, { timeout: 60000 }
  );
  await shot('m5-s1-rag-answer.png');
  report.s1 = await page.locator('.message-row.assistant .message-bubble').last().innerText();

  // ===== 场景 5：未命中兜底 =====
  await ask('公司年假有多少天？');
  await page.getByText('当前知识库未找到足够信息', { exact: false }).last().waitFor({ timeout: 120000 });
  await shot('m5-s5-no-hit.png');
  report.s5 = 'fallback shown';

  // ===== 场景 2：销售排名（SQL + 表格 + 柱状图） =====
  await ask('上个月销售额最高的 10 个商品是什么？');
  await page.locator('.echarts-panel canvas, .analysis-result-card').first().waitFor({ timeout: 180000 });
  await page.waitForTimeout(1200);
  await shot('m5-s2-top10.png');
  report.s2 = await page.locator('.sql-block').first().innerText().catch(() => 'analysis card shown');

  // ===== 场景 3：渠道分析 =====
  await ask('华东地区各渠道的有效订单量是多少？');
  await page.waitForFunction(
    () => {
      const tags = document.querySelectorAll('.answer-meta .el-tag, .analysis-result-card');
      return tags.length > 0;
    },
    null,
    { timeout: 180000 }
  );
  await page.waitForTimeout(1200);
  await shot('m5-s3-channel.png');
  report.s3 = 'chart rendered';

  // ===== 场景 4：安全拦截 =====
  await ask('删除所有销售订单。');
  await page.getByText('已被安全策略拒绝', { exact: false }).waitFor({ timeout: 120000 });
  await shot('m5-s4-blocked.png');
  report.s4 = 'refusal shown';

  // ===== 知识库列表 =====
  await page.goto(BASE + '/knowledge', { waitUntil: 'networkidle' });
  await page.locator('.el-table__row, .el-empty').first().waitFor({ timeout: 30000 });
  await shot('m5-knowledge.png');
  report.knowledge = 'knowledge page shown';

  await browser.close();
  console.log(JSON.stringify(report, null, 2));
})().catch((e) => {
  console.error('VERIFY FAILED:', e.message);
  process.exit(1);
});
