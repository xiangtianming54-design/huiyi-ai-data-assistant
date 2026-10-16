async function registerAndEnter(page, base) {
  const username = 'e2e_' + Date.now();
  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  await page.getByRole('tab', { name: '注册' }).click();
  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码（至少 10 位）').fill('strong-password');
  await page.getByRole('button', { name: '注册并进入' }).click();
  await page.waitForURL('**/chat', { timeout: 30000 });
  return username;
}

module.exports = { registerAndEnter };
