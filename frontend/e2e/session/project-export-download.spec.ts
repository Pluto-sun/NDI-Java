import { readFile } from 'node:fs/promises';
import { expect, test } from '@playwright/test';

for (const hostname of ['127.0.0.1', 'localhost']) {
  test(`项目 CSV 通过当前访问入口下载 (${hostname})`, async ({ page, request, baseURL }) => {
    const entry = new URL(baseURL!);
    entry.hostname = hostname;
    await page.goto(new URL('/projects', entry).href);
    await page.getByLabel('账号', { exact: true }).fill('e2e_admin');
    await page.getByLabel('密码', { exact: true }).fill('e2e-session-password');
    await page.getByRole('button', { name: '登录', exact: true }).click();

    await page.getByRole('textbox', { name: '项目名称', exact: true }).fill('隔离历史项目');
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await expect(page.getByRole('row').filter({ hasText: '隔离历史项目' }).first()).toBeVisible();
    const listUrl = page.url();
    await page.getByRole('button', { name: '按当前条件导出 CSV', exact: true }).click();
    await page.getByRole('button', { name: '生成导出文件', exact: true }).click();
    await expect(page.getByText(/^导出任务：COMPLETED/)).toBeVisible({ timeout: 20_000 });

    const [downloadResponse, download] = await Promise.all([
      page.waitForResponse((response) => /\/exports\/[^/]+\/download$/.test(response.url())),
      page.waitForEvent('download'),
      page.getByRole('button', { name: '下载 CSV', exact: true }).click(),
    ]);
    expect(new URL(downloadResponse.url()).origin).toBe(entry.origin);
    expect(downloadResponse.status()).toBe(200);
    expect(downloadResponse.headers()['content-type']).toContain('text/csv');
    expect(downloadResponse.headers()['location']).toBeUndefined();
    expect(download.suggestedFilename()).toMatch(/^projects-[0-9a-f-]{36}\.csv$/);
    expect(await download.failure()).toBeNull();
    expect(await readFile((await download.path())!, 'utf8')).toContain('隔离历史项目');
    await expect(page).toHaveURL(listUrl);
    expect(page.context().pages()).toHaveLength(1);

    const anonymous = await request.get(downloadResponse.url(), { maxRedirects: 0 });
    expect(anonymous.status()).toBe(401);
  });
}
