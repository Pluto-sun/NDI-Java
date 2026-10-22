import { test, expect } from '@playwright/test';
import { chooseActor, ids } from './support/projects';

test('地图阶段组合筛选、终止联动及历史金额不受当前主档影响', async ({ page }, testInfo) => {
  await page.goto('/workbench');
  await chooseActor(page, ids.leader);
  const map = page.getByRole('region', { name: '项目区域分布', exact: true });
  await map.getByLabel('统计月份', { exact: true }).fill('2020-01');
  const stages = map.getByRole('group', { name: '地图项目阶段', exact: true });
  const rows = map.locator('.map-regions li');
  const row = rows.filter({ hasText: '2 个项目 · 总金额 300 万元' });
  await expect(row).toHaveCount(1);
  await stages.getByRole('checkbox', { name: '方案交流', exact: true }).check();
  await expect(rows.filter({ hasText: '1 个项目 · 总金额 100 万元' })).toHaveCount(1);
  await stages.getByRole('checkbox', { name: '招投标', exact: true }).check();
  await expect(row).toHaveCount(1);
  await stages.getByRole('checkbox', { name: '已终止', exact: true }).check();
  await expect(map.getByRole('checkbox', { name: '含已终止项目', exact: true })).toBeChecked();
  await expect(rows.filter({ hasText: '3 个项目 · 总金额 600 万元' })).toHaveCount(1);
  await map.getByRole('checkbox', { name: '含已终止项目', exact: true }).uncheck();
  await expect(stages.getByRole('checkbox', { name: '已终止', exact: true })).not.toBeChecked();
  await expect(row).toHaveCount(1);
  await map.getByRole('combobox', { name: '着色与排行指标', exact: true }).selectOption('network');
  await expect(row.locator('strong')).toHaveText('60 万元');
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await map.screenshot({ path: testInfo.outputPath('map-stage-combination.png') });
});
