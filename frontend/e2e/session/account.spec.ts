import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';

const password = 'e2e-session-password';

test('正式登录、资料保存、重新加载、退出及修改密码撤销其他设备会话', async ({
  page,
  browser,
}, testInfo) => {
  const loginName = testInfo.project.name === 'desktop' ? 'local_city' : 'local_province';
  await page.goto('/account');
  await expect(page.getByRole('heading', { name: '登录账号', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '选择账号', exact: true })).toHaveCount(0);
  await page.getByLabel('账号', { exact: true }).fill(loginName);
  await page.getByLabel('密码', { exact: true }).fill('incorrect-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('账号或密码错误');
  await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '个人设置', exact: true })).toBeVisible();
  await expect(page.getByText('本地联调模式', { exact: false })).toHaveCount(0);
  const cookies = await page.context().cookies();
  expect(cookies.find((cookie) => cookie.name === 'ndi_session')).toMatchObject({
    httpOnly: true,
    sameSite: 'Lax',
  });
  const secondDevice = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  try {
    const csrf = await secondDevice.request.get('/api/v1/auth/csrf');
    const token = (await csrf.json()).data.token;
    const secondLogin = await secondDevice.request.post('/api/v1/auth/sessions', {
      headers: { 'X-XSRF-TOKEN': token },
      data: { loginName, password },
    });
    expect(secondLogin.status()).toBe(201);
    const displayName = `个人资料验收-${randomUUID().slice(0, 8)}`;
    await page.getByLabel('姓名', { exact: true }).fill(displayName);
    await page.getByLabel('邮箱（选填）', { exact: true }).fill(`${randomUUID()}@example.test`);
    await page.getByRole('button', { name: '保存资料', exact: true }).click();
    await expect(page.getByText('个人资料已保存。', { exact: true })).toBeVisible();
    await expect(page.locator('.account')).toContainText(displayName);
    await page.reload();
    await expect(page.getByLabel('姓名', { exact: true })).toHaveValue(displayName);
    await page.getByRole('button', { name: '退出登录', exact: true }).click();
    await expect(page.getByRole('heading', { name: '登录账号', exact: true })).toBeVisible();
    expect((await secondDevice.request.get('/api/v1/auth/me')).status()).toBe(200);
    await page.getByLabel('账号', { exact: true }).fill(loginName);
    await page.getByLabel('密码', { exact: true }).fill(password);
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await expect(page.getByRole('heading', { name: '个人设置', exact: true })).toBeVisible();
    await page.getByLabel('当前密码', { exact: true }).fill(password);
    await page.getByLabel('新密码', { exact: true }).fill('changed-e2e-password');
    await page.getByLabel('确认新密码', { exact: true }).fill('mismatch-password');
    await page.getByRole('button', { name: '修改密码并重新登录', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('两次输入的新密码不一致');
    await page.getByLabel('确认新密码', { exact: true }).fill('changed-e2e-password');
    await page.getByRole('button', { name: '修改密码并重新登录', exact: true }).click();
    await expect(page.getByRole('heading', { name: '登录账号', exact: true })).toBeVisible();
    await expect(page.getByText('密码已修改，请重新登录。', { exact: true })).toBeVisible();
    expect((await secondDevice.request.get('/api/v1/auth/me')).status()).toBe(401);
    await page.getByLabel('账号', { exact: true }).fill(loginName);
    await page.getByLabel('密码', { exact: true }).fill('changed-e2e-password');
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await expect(page.getByRole('heading', { name: '个人设置', exact: true })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBeTruthy();
    const skipLink = page.getByRole('link', { name: '跳到主内容', exact: true });
    await skipLink.evaluate((element) => (element as HTMLElement).blur());
    await expect(skipLink).not.toBeInViewport();
    await skipLink.focus();
    await expect(skipLink).toBeInViewport();
    await skipLink.evaluate((element) => (element as HTMLElement).blur());
    await page.screenshot({ path: testInfo.outputPath('account-settings.png'), fullPage: true });
  } finally {
    await secondDevice.close();
  }
});
