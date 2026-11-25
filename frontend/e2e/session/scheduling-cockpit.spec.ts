import { test, expect } from '@playwright/test';

test('独立驾驶舱受会话保护，登录后恢复直达月份，退出后刷新要求重新登录', async ({ page }) => {
  const path = '/workbench/scheduling?from=2026-01&to=2026-10';
  await page.goto(path);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('scheduling-board')).toHaveCount(0);
  const api = '/api/v1/production/plans/scheduling-overview?from=2026-01&to=2026-10';
  expect((await page.request.get(api)).status()).toBe(401);

  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(path.replace('?', '\\?') + '$'));
  await expect(page.getByRole('region', { name: '累计指标' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '模块导航' })).toHaveCount(0);
  await page.reload();
  await expect(page.getByLabel('结束月份', { exact: true })).toHaveText('2026-10');

  const main = await page.context().newPage();
  await main.goto('/projects');
  await main.getByRole('button', { name: '退出登录', exact: true }).click();
  await main.getByRole('button', { name: '确认退出', exact: true }).click();
  await expect(main).toHaveURL(/\/login$/);
  await page.reload();
  await expect(page).toHaveURL(/\/login$/);
});
