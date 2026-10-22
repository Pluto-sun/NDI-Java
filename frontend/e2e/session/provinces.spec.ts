import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

test('正式管理员先建省分再建地市，重复省分不覆盖既有目录', async ({ page }, testInfo) => {
  const name = `初始化省-${randomUUID().slice(0, 8)}`;
  await page.goto('/admin/accounts');
  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.getByRole('button', { name: '新增省分', exact: true }).click();
  const form = page.getByRole('form', { name: '新增省分', exact: true });
  await form.getByLabel('新增省分名称', { exact: true }).fill(name);
  await form.getByRole('button', { name: '添加省分', exact: true }).click();
  await expect(form.getByRole('status')).toHaveText('省分已添加，可继续新增地市和配置账号。');
  await form.screenshot({ path: testInfo.outputPath('province-created.png') });
  await form.getByLabel('新增省分名称', { exact: true }).fill(` ${name} `);
  await form.getByRole('button', { name: '添加省分', exact: true }).click();
  await expect(form.getByRole('alert')).toContainText('已存在同名省分');
  await page.getByRole('button', { name: '关闭新增省分', exact: true }).click();
  await page.getByRole('button', { name: '新增地市', exact: true }).click();
  await page
    .getByRole('combobox', { name: '地市归属省分', exact: true })
    .selectOption({ label: name });
  await page
    .getByLabel('新增地市名称', { exact: true })
    .fill(`初始化市-${randomUUID().slice(0, 8)}`);
  await page.getByRole('button', { name: '添加地市', exact: true }).click();
  await expect(page.getByText('地市已添加，账号归属选项已更新。', { exact: true })).toBeVisible();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
});
