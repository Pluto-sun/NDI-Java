import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { expect, test } from '@playwright/test';

test('管理员创建完整备份、核对摘要并下载；非管理员拒绝访问且失败校验不下载', async ({
  page,
  browser,
}, testInfo) => {
  await page.goto('/admin/backups');
  await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
  await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '备份管理', exact: true })).toBeVisible();
  const create = page.getByRole('button', { name: '创建备份', exact: true });
  let posts = 0;
  page.on('request', (request) => {
    if (request.url().endsWith('/api/v1/admin/backups') && request.method() === 'POST') posts++;
  });
  await create.click();
  await expect(page.getByRole('heading', { name: '确认创建完整备份', exact: true })).toBeFocused();
  await page.getByRole('button', { name: '取消创建', exact: true }).click();
  expect(posts).toBe(0);
  await create.click();
  const accepted = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v1/admin/backups') &&
      response.request().method() === 'POST' &&
      response.status() === 202,
  );
  await page.getByRole('button', { name: '确认创建', exact: true }).click();
  const task = (await (await accepted).json()).data;
  const row = page.getByRole('listitem').filter({ hasText: task.backupCode });
  await expect(row).toContainText('状态：已完成', { timeout: 30000 });
  const response = await page.request.get(`/api/v1/admin/backups/${task.id}`);
  expect(response.ok()).toBeTruthy();
  const ready = (await response.json()).data;
  expect(ready.sha256).toMatch(/^[0-9a-f]{64}$/);
  expect(ready).not.toHaveProperty('workerToken');
  let downloads = 0;
  page.on('download', () => downloads++);
  const event = page.waitForEvent('download');
  await row.getByRole('button', { name: '下载归档', exact: true }).click();
  const download = await event;
  expect(await download.failure()).toBeNull();
  expect(download.suggestedFilename()).toBe(`ndi-backup-${task.id}.zip`);
  const path = await download.path();
  expect(path).not.toBeNull();
  expect(
    createHash('sha256')
      .update(await readFile(path!))
      .digest('hex'),
  ).toBe(ready.sha256);
  await page.route(`**/api/v1/admin/backups/${task.id}/download-check`, (route) =>
    route.fulfill({
      status: 410,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'BACKUP_UNAVAILABLE', detail: '归档校验失败，请核查存储' }),
    }),
  );
  await row.getByRole('button', { name: '下载归档', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('归档校验失败');
  expect(downloads).toBe(1);
  await page.unroute(`**/api/v1/admin/backups/${task.id}/download-check`);
  await page.getByRole('button', { name: '刷新备份记录', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(row).toContainText(ready.sha256);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('backup-completed.png'), fullPage: true });

  const outsider = await browser.newContext({ baseURL: 'http://127.0.0.1:15173' });
  try {
    expect((await outsider.request.get(`/api/v1/admin/backups/${task.id}/download`)).status()).toBe(
      401,
    );
    const csrf = (await (await outsider.request.get('/api/v1/auth/csrf')).json()).data.token;
    const login = await outsider.request.post('/api/v1/auth/sessions', {
      headers: { 'X-XSRF-TOKEN': csrf },
      data: { loginName: 'e2e_group', password: 'e2e-session-password' },
    });
    expect(login.status()).toBe(201);
    expect((await outsider.request.get('/api/v1/admin/backups')).status()).toBe(403);
    expect((await outsider.request.get(`/api/v1/admin/backups/${task.id}/download`)).status()).toBe(
      403,
    );
    const otherPage = await outsider.newPage();
    await otherPage.goto('/admin/backups');
    await expect(otherPage).toHaveURL(/\/projects$/);
    await expect(otherPage.getByRole('link', { name: '备份管理', exact: true })).toHaveCount(0);
  } finally {
    await outsider.close();
  }
});
