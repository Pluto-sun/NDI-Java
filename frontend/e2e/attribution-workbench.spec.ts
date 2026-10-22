import { test, expect } from '@playwright/test';
import { chooseActor, ids, createProject, get } from './support/projects';
import { randomUUID } from 'node:crypto';

test('所属月份区间统计与月末快照分开，支持阶段、金额、空结果和失败重试', async ({
  page,
  request,
}, testInfo) => {
  await createProject(request, `归属统计-${randomUUID()}`);
  await page.goto('/workbench');
  await chooseActor(page, ids.leader);
  const panel = page.getByRole('region', { name: '项目所属月份区间统计', exact: true });
  const currentMonth = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
  }).format(new Date());
  await panel.getByLabel('所属月份起始', { exact: true }).fill(currentMonth);
  await panel.getByLabel('所属月份结束', { exact: true }).fill(currentMonth);
  await expect(panel.getByRole('button', { name: '查询所属月份统计', exact: true })).toBeEnabled();
  await panel
    .getByRole('group', { name: '所属月份统计阶段', exact: true })
    .getByRole('checkbox', { name: '方案交流', exact: true })
    .check();
  await panel.getByRole('button', { name: '查询所属月份统计', exact: true }).click();
  const expected = await get<{ projectCount: number; categoryTotals: { network: number } }>(
    request,
    ids.leader,
    `/workbench/attribution?from=${currentMonth}&to=${currentMonth}&stage=SOLUTION_EXCHANGE`,
  );
  expect(expected.projectCount).toBeGreaterThan(0);
  await expect(
    panel.locator('.kpi').filter({ hasText: '区间项目数量' }).locator('strong'),
  ).toHaveText(String(expected.projectCount));
  await panel
    .getByRole('combobox', { name: '所属月份趋势指标', exact: true })
    .selectOption('network');
  await expect(
    panel.getByRole('table', { name: '按项目所属月份的阶段指标明细：网络化金额', exact: true }),
  ).toBeVisible();
  await panel.getByLabel('所属月份起始', { exact: true }).fill('1999-01');
  await panel.getByLabel('所属月份结束', { exact: true }).fill('1999-02');
  await panel.getByRole('button', { name: '查询所属月份统计', exact: true }).click();
  await expect(
    panel.locator('.kpi').filter({ hasText: '区间项目数量' }).locator('strong'),
  ).toHaveText('0');
  await page.route('**/workbench/attribution?*', (route) =>
    route.fulfill({ status: 503, body: '{}' }),
  );
  await panel.getByRole('button', { name: '查询所属月份统计', exact: true }).click();
  await expect(panel.getByRole('button', { name: '重试所属月份统计', exact: true })).toBeVisible();
  await page.unroute('**/workbench/attribution?*');
  await panel.getByRole('button', { name: '重试所属月份统计', exact: true }).click();
  await expect(
    panel.locator('.kpi').filter({ hasText: '区间项目数量' }).locator('strong'),
  ).toHaveText('0');
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await panel.screenshot({ path: testInfo.outputPath('attribution-workbench.png') });
});
