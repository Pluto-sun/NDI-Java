import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { importWorkbook } from '../support/importWorkbook';
import type { ImportTask, ImportBatch } from '../../src/features/projects/types';

test('正式管理员会话从创建页导入，上传受 CSRF 保护且完成后不保留活动任务', async ({
  page,
  browser,
}, testInfo) => {
  await page.goto('/projects/new');
  await expect(page.getByRole('img', { name: '联通格物', exact: true })).toBeVisible();
  await expect(page).toHaveTitle('联通格物 · 运营管理平台');
  await expect(page.getByText('NDI ACCOUNT', { exact: true })).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('liantong-gewu-login.png'), fullPage: true });
  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '项目创建', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '新建项目', exact: true })).toHaveCount(0);
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  const row = {
    省分: '广东省',
    地市: '广州市',
    客户名称: '正式导入客户',
    客户所属行业: '石油、煤炭及其他燃料加工业',
    项目名称: `正式会话导入-${randomUUID()}`,
    项目负责人: '外部负责人',
    首条项目进展: '已完成首次交流',
    下一步动作: '安排下一轮沟通',
    项目建设内容: '正式会话建设内容',
    项目阶段: '方案交流',
    '项目总金额（万）': '100',
    '网络化预计金额（万）': '50',
    '格物预计金额（万）': '',
    '大数据预计金额（万）': '',
    '万悟预计金额（万）': '',
  };
  const buffer = importWorkbook([row]);
  const denied = await page.request.post('/api/v1/projects/imports/task', {
    multipart: {
      file: {
        name: '正式.xlsx',
        mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        buffer,
      },
    },
  });
  expect(denied.status()).toBe(403);
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '正式.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer,
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByRole('table', { name: '项目导入预检结果', exact: true })).toBeVisible();
  const task = (await (await page.request.get('/api/v1/projects/imports/task')).json())
    .data as ImportTask;
  const result = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/imports/${task.batch!.batchId}/confirm`) &&
      response.status() === 200,
  );
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  const batch = (await (await result).json()).data as ImportBatch;
  expect(batch.importedRows).toBe(1);
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  expect((await (await page.request.get('/api/v1/projects/imports/task')).json()).data).toBeNull();
  const context = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  try {
    const reader = await context.newPage();
    await reader.goto('/projects/new');
    await reader.getByLabel('账号', { exact: true }).fill('e2e_group');
    await reader.getByLabel('密码', { exact: true }).fill('e2e-session-password');
    await reader.getByRole('button', { name: '登录', exact: true }).click();
    await expect(reader).toHaveURL(/\/projects$/);
    expect((await context.request.get('/api/v1/projects/imports/task')).status()).toBe(403);
  } finally {
    await context.close();
  }
});
