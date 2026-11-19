import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

async function login(page: Page, loginName = 'e2e_admin') {
  await page.getByLabel('账号', { exact: true }).fill(loginName);
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
}

async function logout(page: Page) {
  await page.getByRole('button', { name: '退出登录', exact: true }).click();
  await page
    .getByRole('dialog', { name: '确认退出登录' })
    .getByRole('button', { name: '确认退出', exact: true })
    .click();
  await expect(page).toHaveURL(/\/login$/);
}

test('取消保持会话，同账号恢复筛选，切换账号返回首页', async ({ page }) => {
  const path = '/scheduling?month=2026-10&category=GEWU&search=验收';
  await page.goto(path);
  await login(page);
  const list = page.getByRole('heading', { name: '排产计划', exact: true });
  await expect(list).toBeVisible();
  const original = page.url();
  await page.getByRole('button', { name: '退出登录', exact: true }).click();
  await page
    .getByRole('dialog', { name: '确认退出登录' })
    .getByRole('button', { name: '取消' })
    .click();
  await expect(page).toHaveURL(original);
  expect((await page.request.get('/api/v1/auth/me')).status()).toBe(200);
  await logout(page);
  await page.reload();
  await login(page);
  await expect(page).toHaveURL(original);
  await expect(page.getByLabel('项目 / 客户 / 接口人', { exact: true })).toHaveValue('验收');
  await logout(page);
  await login(page, 'e2e_group');
  await expect(page).toHaveURL(/\/workbench$/);
});

test('同账号退出后记录失效或访问被拒绝时返回首页', async ({ page }) => {
  await page.goto('/projects');
  await login(page);
  await expect(page).toHaveURL(/\/projects$/);
  for (const status of [404, 403]) {
    const path = '/projects/' + randomUUID();
    await page.goto(path);
    await logout(page);
    await page.route('**/api/v1' + path, (route) =>
      route.fulfill({ status, json: { code: 'REJECTED' } }),
    );
    await login(page);
    await expect(page).toHaveURL(/\/workbench$/);
  }
});
