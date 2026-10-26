import { expect, test } from '@playwright/test';

test('正式会话使用预置全国省市目录，旧新增接口也不能绕过只读约束', async ({ page }, testInfo) => {
  await page.goto('/admin/accounts');
  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '账号与组织', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '新增省分', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '新增地市', exact: true })).toHaveCount(0);
  const configuration = await page.request.get('/api/v1/admin/configuration');
  expect(configuration.ok()).toBeTruthy();
  const data = (await configuration.json()).data;
  expect(data.provinces.length).toBeGreaterThanOrEqual(34);
  expect(data.cities.length).toBeGreaterThanOrEqual(372);
  expect(data.cities.some((city: { name: string }) => city.name === '广州市')).toBeTruthy();
  const csrf = (await (await page.request.get('/api/v1/auth/csrf')).json()).data.token;
  const denied = await page.request.post('/api/v1/admin/provinces', {
    headers: { 'X-XSRF-TOKEN': csrf },
    data: { name: '不可自建的省份' },
  });
  expect(denied.status()).toBe(409);
  expect((await denied.json()).code).toBe('ORGANIZATION_CATALOG_READ_ONLY');
  await page.screenshot({ path: testInfo.outputPath('standard-directory.png'), fullPage: true });
});
