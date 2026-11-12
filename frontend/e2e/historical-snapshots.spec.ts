import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { chooseActor, get, ids, post } from './support/projects';
import { importWorkbook } from './support/importWorkbook';
import type { Dashboard } from '../src/features/workbench/dashboardApi';

test('快照导入区分缺失、部分补录、整月完整及当月实时', async ({ page, request }) => {
  const now = new Date();
  const current = now.toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }).slice(0, 7);
  const year = Number(current.slice(0, 4));
  const month = Number(current.slice(5));
  const previous = new Date(Date.UTC(year, month - 2, 1)).toISOString().slice(0, 7);
  const project = await post<{ projectCode: string }>(request, ids.reporter, '', {
    provinceId: ids.province,
    cityId: ids.city,
    projectName: '历史快照验收-' + randomUUID(),
    responsiblePersonNames: ['验收负责人'],
    customerNeed: '设备接入',
    totalAmount: '100',
    categoryAmounts: [{ categoryCode: 'GEWU', expectedAmount: '60' }],
    initialProgress: '需求调研',
    nextAction: '安排交流',
  });
  const view = () =>
    get<Dashboard>(
      request,
      ids.reporter,
      `/workbench/dashboard?part=TREND&product=GEWU&from=${previous}&to=${current}`,
    );
  const before = await view();
  expect(before.trend[1].source).toBe('LIVE');
  expect(
    before.trend[1].productStages?.some(
      (row) => row.stageCode === 'SOLUTION_EXCHANGE' && row.count > 0,
    ),
  ).toBe(true);
  const denied = await request.get(
    'http://127.0.0.1:18080/api/v1/admin/historical-snapshots/imports/task',
    {
      headers: { 'X-NDI-Actor-Id': ids.reporter },
    },
  );
  expect(denied.status()).toBe(403);
  await page.goto('/admin/historical-snapshots');
  await chooseActor(page, ids.admin);
  const row = {
    快照月份: previous,
    系统项目编号: project.projectCode,
    月末阶段: '招投标',
    月末归属省分: '本地演示省',
    地市: '本地演示市',
    月末项目总金额: '100',
    '5G工业专网月末金额': '0',
    格物工业互联网平台月末金额: '35',
    工业大数据平台月末金额: '0',
    万悟工业智能体平台月末金额: '0',
  };
  async function preview(cells: Record<string, string>) {
    await page.getByRole('link', { name: 'Excel 导入', exact: true }).click();
    await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
      name: '历史快照.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: importWorkbook([cells]),
    });
    await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
    await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  }
  await preview(row);
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const partial = await view();
  if (before.trend[0].source !== 'SNAPSHOT') {
    expect(partial.trend[0].source).toBe('PARTIAL');
    expect(partial.trend[0].productStages).toEqual([]);
  }
  await page.goto('/admin/historical-snapshots');
  await page.getByLabel('快照月份', { exact: true }).fill(previous);
  await page.getByRole('button', { name: '确认整月完整', exact: true }).click();
  await page.getByRole('button', { name: '确认完整', exact: true }).click();
  await expect(page.getByText(`${previous} 已标记为整月完整。`)).toBeVisible();
  const after = await view();
  expect(after.trend[0].source).toBe('SNAPSHOT');
  const bidding = after.trend[0].productStages?.find((row) => row.stageCode === 'BIDDING');
  expect(bidding?.count).toBeGreaterThanOrEqual(1);
  expect(Number(bidding?.amount)).toBeGreaterThanOrEqual(35);
  await preview(row);
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByText(/成功导入 0 行/)).toBeVisible();

  await page.goto('/admin/historical-snapshots');
  await preview({ ...row, 格物工业互联网平台月末金额: '0' });
  await expect(page.getByText(/已有快照与导入值冲突/)).toBeVisible();
  await page.getByRole('button', { name: '查看／修正第 3 行', exact: true }).click();
  await page.getByLabel('确认覆盖的修订号', { exact: true }).fill('0');
  await page.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const corrected = await view();
  expect(
    corrected.trend[0].productStages?.find((item) => item.stageCode === 'BIDDING')?.count ?? 0,
  ).toBe((bidding?.count ?? 0) - 1);
});
