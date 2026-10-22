import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, ids, createProject, get } from './support/projects';

test('地图所属月份区间与总览同口径，省份下钻保留区间且空区间不读取月末快照', async ({
  page,
  request,
}, testInfo) => {
  await createProject(request, `区间地图-${randomUUID()}`);
  const current = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
  }).format(new Date());
  const expected = await get<{ projectCount: number; totalExpectedAmount: number }>(
    request,
    ids.leader,
    `/workbench/attribution?from=${current}&to=${current}`,
  );
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  const map = page.getByRole('region', { name: '项目区域分布', exact: true });
  await map
    .getByRole('combobox', { name: '地图统计口径', exact: true })
    .selectOption('ATTRIBUTION');
  await map.getByLabel('地图所属月份起始', { exact: true }).fill(current);
  await map.getByLabel('地图所属月份结束', { exact: true }).fill(current);
  const province = map.locator('.map-regions li').filter({ hasText: '本地演示省' });
  await expect(province.locator('small')).toHaveText(
    `${expected.projectCount} 个项目 · 总金额 ${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(expected.totalExpectedAmount)} 万元`,
  );
  await province.getByRole('button').click();
  await expect(map.getByLabel('地图所属月份起始', { exact: true })).toHaveValue(current);
  await expect(map.getByRole('heading', { name: '地市统计', exact: true })).toBeVisible();
  await expect(map.locator('.map-regions a')).toHaveCount(0);
  await map.getByLabel('地图所属月份起始', { exact: true }).fill('1999-01');
  await map.getByLabel('地图所属月份结束', { exact: true }).fill('1999-02');
  await expect(
    map.locator('.map-regions li').filter({ hasText: '本地演示市' }).locator('small'),
  ).toHaveText('0 个项目 · 总金额 0 万元');
  await expect(map.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await map.screenshot({ path: testInfo.outputPath('map-attribution-range.png') });
});
