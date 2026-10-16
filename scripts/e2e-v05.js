// v0.5 复合问题浏览器验证：一句话两问 → 知识气泡 + 数据富结果卡片
const { chromium } = require('playwright-core');
const path = require('path');
const { registerAndEnter } = require('./e2e-login');

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await registerAndEnter(page, 'http://localhost:5173');

  await page.locator('textarea').fill('什么是有效订单？上个月销售额最高的 3 个商品是什么？');
  await page.getByRole('button', { name: '发送' }).click();

  // 等待富结果卡片（数据部分）与引用（知识部分）同时出现
  await page.locator('.analysis-result-card').waitFor({ timeout: 240000 });
  await page.locator('.reference-tag').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);

  const assistantCount = await page.locator('.message-row.assistant').count();
  const hasCard = (await page.locator('.analysis-result-card').count()) > 0;
  const hasRef = (await page.locator('.reference-tag').count()) > 0;

  await page.screenshot({
    path: path.resolve(__dirname, '..', 'docs', 'images', 'v05-compound.png'),
    fullPage: false
  });

  console.log(JSON.stringify({ assistantCount, hasCard, hasRef }));
  await browser.close();
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
