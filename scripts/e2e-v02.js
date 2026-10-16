// v0.2 统一对话入口浏览器回归：一个页面覆盖 知识问答 / 数据分析 / 安全拦截 / 兜底
// 用法：node scripts/e2e-v02.js（需全栈已启动：docker compose up -d）
const { chromium } = require('playwright-core');
const path = require('path');
const { registerAndEnter } = require('./e2e-login');

const BASE = process.env.E2E_BASE_URL || 'http://localhost:5173';
const SHOTS = path.resolve(__dirname, '..', 'docs', 'images');

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const shot = (name) => page.screenshot({ path: path.join(SHOTS, name), fullPage: false });
  const report = {};

  await registerAndEnter(page, BASE);

  // 旧路由重定向检查
  await page.goto(BASE + '/analysis', { waitUntil: 'domcontentloaded' });
  await page.waitForURL('**/chat', { timeout: 8000 });
  report.redirect = 'ok';

  // A. 知识问答：流式 + 引用
  await page.goto(BASE + '/chat', { waitUntil: 'networkidle' });
  await page.locator('textarea').fill('什么是有效订单？');
  await page.getByRole('button', { name: '发送' }).click();
  await page.locator('.reference-tag').first().waitFor({ timeout: 120000 });
  await page.waitForFunction(() => !document.querySelector('.cursor-blink'), null, { timeout: 60000 });
  report.knowledge = 'streamed with references';

  // B. 数据分析：富结果卡片（SQL 折叠 + 柱状图 + 表格）
  await page.locator('textarea').fill('上个月销售额最高的 10 个商品是什么？');
  await page.getByRole('button', { name: '发送' }).click();
  await page.locator('.analysis-result-card .echarts-panel canvas').waitFor({ timeout: 180000 });
  await page.waitForTimeout(1500);
  await shot('v02-unified-analysis.png');
  report.analysisSql = await page.locator('.sql-collapse').innerText();
  report.analysisTable = (await page.locator('.analysis-result-card .el-table__row').count()) + ' rows visible';

  // C. 安全拦截
  await page.locator('textarea').fill('删除所有销售订单。');
  await page.getByRole('button', { name: '发送' }).click();
  await page.getByText('已被安全策略拒绝', { exact: false }).last().waitFor({ timeout: 120000 });
  report.blocked = 'refusal shown in chat';

  // D. 未命中兜底
  await page.locator('textarea').fill('公司年假有多少天？');
  await page.getByRole('button', { name: '发送' }).click();
  await page.getByText('当前知识库未找到足够信息', { exact: false }).last().waitFor({ timeout: 120000 });
  await page.waitForFunction(() => !document.querySelector('.cursor-blink'), null, { timeout: 60000 });
  await shot('v02-unified-chat-full.png');
  report.fallback = 'fallback shown';

  await browser.close();
  console.log(JSON.stringify(report, null, 2));
})().catch((e) => {
  console.error('VERIFY FAILED:', e.message);
  process.exit(1);
});
