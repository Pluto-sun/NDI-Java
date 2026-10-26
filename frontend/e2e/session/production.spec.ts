import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
test('会话排产维护走CSRF，集团角色仅可查看', async ({ page, browser }) => {
  await page.goto('/scheduling/new');
  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page
    .getByRole('combobox', { name: '省份 *', exact: true })
    .selectOption('11111111-1111-4111-8111-111111111111');
  await page
    .getByRole('combobox', { name: '地市 *', exact: true })
    .selectOption('22222222-2222-4222-8222-222222222222');
  await page.getByLabel('接口人 *', { exact: true }).fill('会话接口人');
  await page.getByLabel('项目名称 *', { exact: true }).fill('会话排产-' + randomUUID());
  await page.getByLabel('客户名称 *', { exact: true }).fill('会话客户');
  await page.getByRole('checkbox', { name: '网络化', exact: true }).check();
  await page.getByLabel('工业专网排产（万元）', { exact: true }).fill('10');
  await page.getByRole('button', { name: '保存排产', exact: true }).click();
  await expect(page.getByRole('heading', { name: '排产计划', exact: true })).toBeVisible();
  const context = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  try {
    const reader = await context.newPage();
    await reader.goto('/scheduling');
    await reader.getByLabel('账号', { exact: true }).fill('e2e_group');
    await reader.getByLabel('密码', { exact: true }).fill('e2e-session-password');
    await reader.getByRole('button', { name: '登录', exact: true }).click();
    await expect(reader.getByRole('heading', { name: '排产计划', exact: true })).toBeVisible();
    await expect(reader.getByRole('link', { name: '新增排产', exact: true })).toHaveCount(0);
    await reader.goto('/scheduling/new');
    await expect(reader.getByRole('alert')).toContainText('仅可查看');
  } finally {
    await context.close();
  }
});
