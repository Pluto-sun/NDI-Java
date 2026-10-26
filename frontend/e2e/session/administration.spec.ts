import { randomInt } from 'node:crypto';
import { test, expect, type BrowserContext } from '@playwright/test';

async function deviceLogin(context: BrowserContext, loginName: string, password: string) {
  const csrf = await context.request.get('/api/v1/auth/csrf');
  const response = await context.request.post('/api/v1/auth/sessions', {
    headers: { 'X-XSRF-TOKEN': (await csrf.json()).data.token },
    data: { loginName, password },
  });
  expect(response.status()).toBe(201);
}

test('管理员使用既有地市创建账号、编辑、重置密码和启停均在正式会话中生效', async ({
  page,
  browser,
}, testInfo) => {
  const phone = `138${randomInt(10000000, 99999999)}`;
  const cityName = '本地演示市';
  const password = 'new-user-test-password';
  await page.goto('/admin/accounts');
  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '账号与组织', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '新增地市', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '新建账号', exact: true }).click();
  await page.getByLabel('登录账号', { exact: true }).fill(phone);
  await page.getByLabel('账号姓名', { exact: true }).fill('浏览器账号验收');
  await page.getByLabel('初始密码', { exact: true }).fill(password);
  await page
    .getByRole('combobox', { name: '所属省分', exact: true })
    .selectOption({ label: '本地演示省' });
  await page
    .getByRole('combobox', { name: '所属地市', exact: true })
    .selectOption({ label: cityName });
  await page.getByRole('button', { name: '创建账号', exact: true }).click();
  await expect(page.getByText('账号已创建。', { exact: true })).toBeVisible();
  await page.getByLabel('搜索账号', { exact: true }).fill(phone);
  await page.getByRole('button', { name: '查询账号', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: phone });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText(cityName);
  await row.getByRole('button', { name: '编辑账号', exact: true }).click();
  await page.getByLabel('账号姓名', { exact: true }).fill('编辑后的验收用户');
  await page.getByRole('checkbox', { name: '省分三化负责人', exact: true }).check();
  await page.getByRole('checkbox', { name: '省分数字化负责人', exact: true }).check();
  await page.getByRole('button', { name: '保存账号', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page
    .getByRole('combobox', { name: '管理角色', exact: true })
    .selectOption('PROVINCE_REPORTER');
  await expect(row).toContainText('编辑后的验收用户');
  await expect(row).toContainText('省分三化负责人');
  await expect(row).toContainText('省分数字化负责人');

  const device = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  try {
    await deviceLogin(device, phone, password);
    expect((await device.request.get('/api/v1/admin/users')).status()).toBe(403);
    await row.getByRole('button', { name: '重置密码', exact: true }).click();
    await page.getByLabel('重置密码', { exact: true }).fill('reset-test-password');
    await page.getByLabel('确认重置密码', { exact: true }).fill('reset-test-password');
    await page.getByRole('button', { name: '确认重置密码', exact: true }).click();
    await expect(page.getByText('密码已重置，所有旧会话已失效。', { exact: true })).toBeVisible();
    expect((await device.request.get('/api/v1/auth/me')).status()).toBe(401);
    await deviceLogin(device, phone, 'reset-test-password');
    await row.getByRole('button', { name: '停用账号', exact: true }).click();
    await page.getByRole('button', { name: '确认变更', exact: true }).click();
    await expect(page.getByText('账号已停用，旧会话已失效。', { exact: true })).toBeVisible();
    expect((await device.request.get('/api/v1/auth/me')).status()).toBe(401);
    await row.getByRole('button', { name: '启用账号', exact: true }).click();
    await page.getByRole('button', { name: '确认变更', exact: true }).click();
    await expect(page.getByText('账号已启用，请重新登录。', { exact: true })).toBeVisible();
    expect((await device.request.get('/api/v1/auth/me')).status()).toBe(401);
    await deviceLogin(device, phone, 'reset-test-password');
    await page.reload();
    await expect(page.getByRole('heading', { name: '账号与组织', exact: true })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBeTruthy();
    await page.screenshot({ path: testInfo.outputPath('administration.png'), fullPage: true });
  } finally {
    await device.close();
  }
});
