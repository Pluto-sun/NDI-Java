import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, get, ids } from './support/projects';

test('项目日期范围校验、空结果、恢复当前日期与真实 CSV 下载', async ({
  page,
  request,
}, testInfo) => {
  const name = `日期-${randomUUID()}`;
  const project = await createProject(request, name);
  const detail = await get<{ opportunityDate: string }>(request, ids.reporter, `/${project.id}`);
  expect(detail.opportunityDate).toBeTruthy();
  const day = new Date(detail.opportunityDate).toLocaleDateString('en-CA', {
    timeZone: 'Asia/Shanghai',
  });
  await page.goto('/projects');
  await chooseActor(page, ids.reporter);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(name);
  await page.getByText('更多筛选条件', { exact: true }).click();
  const start = page.getByLabel('项目日期起', { exact: true });
  const end = page.getByLabel('项目日期止', { exact: true });
  await start.fill('2030-12-02');
  await end.fill('2030-12-01');
  await expect(page.getByRole('alert')).toHaveText('项目日期起不得晚于项目日期止。');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(end).toBeFocused();
  await start.fill('2030-12-01');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('row')).toHaveCount(1);
  await start.fill(day);
  await end.fill(day);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('link', { name, exact: true })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: '项目日期', exact: true })).toBeVisible();
  const submitted = page.waitForResponse(
    (response) => response.url().endsWith('/exports') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '按当前条件导出 CSV', exact: true }).click();
  await page.getByRole('button', { name: '生成导出文件', exact: true }).click();
  const response = await submitted;
  expect(response.ok(), await response.text()).toBeTruthy();
  expect(response.request().postDataJSON()).toMatchObject({
    opportunityDateStart: day,
    opportunityDateEnd: day,
  });
  await expect(page.getByText(/^导出任务：COMPLETED/)).toBeVisible({ timeout: 20_000 });
  const downloading = page.waitForEvent('download');
  await page.getByRole('button', { name: '下载 CSV', exact: true }).click();
  const download = await downloading;
  expect(await download.failure()).toBeNull();
  const csv = await readFile((await download.path())!, 'utf8');
  expect(csv).toContain(name);
  expect(csv.trim().split(/\r?\n/)).toHaveLength(2);
  await page.getByRole('button', { name: '重置筛选', exact: true }).click();
  await expect(start).toHaveValue('');
  await expect(end).toHaveValue('');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('project-dates.png'), fullPage: true });
});
