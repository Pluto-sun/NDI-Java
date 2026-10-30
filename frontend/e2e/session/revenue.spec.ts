import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { importWorkbook } from '../support/importWorkbook';
test('正式会话计收创建导入受CSRF保护，集团可创建但不能认定', async ({ page }) => {
  await page.goto('/revenue');
  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '计收列表', exact: true })).toBeVisible();
  const root = '/api/v1/revenue/entries';
  const values = {
    月账期: '202610',
    省份名称: '广东省',
    合同编号: 'SESSION-' + randomUUID(),
    条款编号: '0001',
    条款金额: '10',
    '产品本月计收不含税额/标品含税收入': '0',
  };
  expect((await page.request.post(root, { data: { source: values } })).status()).toBe(403);
  const denied = await page.request.post(root + '/imports/task', {
    multipart: {
      file: {
        name: '计收.xlsx',
        mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        buffer: importWorkbook([values]),
      },
    },
  });
  expect(denied.status()).toBe(403);
  await page.getByRole('link', { name: '计收导入', exact: true }).click();
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '计收.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([values]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
});
