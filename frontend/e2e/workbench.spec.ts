import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { ids, chooseActor, createProject } from './support/projects';

test('工作台地图键盘缩放、区域筛选、六项指标与动态项目跳转', async ({
  page,
  request,
}, testInfo) => {
  const name = `动态-${randomUUID()}`;
  const project = await createProject(request, name);
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  await page
    .getByRole('button', { name: '展开高级分析：历史月报、地图下钻与行业分析', exact: true })
    .click();
  const map = page.getByRole('region', { name: '项目区域分布', exact: true });
  const canvas = map.getByRole('group', { name: '项目区域分布地图', exact: true });
  await expect(canvas).toBeVisible();
  if (process.env.NDI_E2E_LIVE_MAP === '1') {
    const response = await request.get(
      'http://127.0.0.1:18080/api/v1/projects/workbench/map/boundaries/100000',
      {
        headers: { 'X-NDI-Actor-Id': ids.admin },
      },
    );
    expect(response.ok(), await response.text()).toBeTruthy();
    const boundaries = await response.json();
    expect(boundaries.type).toBe('FeatureCollection');
    expect(boundaries.features.length).toBeGreaterThan(20);
    expect(
      boundaries.features.some(
        (feature: { geometry: { type: string } }) => feature.geometry.type === 'MultiPolygon',
      ),
    ).toBeTruthy();
    await expect(canvas.locator('path')).toHaveCount(boundaries.features.length);
  }
  const initial = await canvas.getAttribute('viewBox');
  await map.getByRole('button', { name: '放大地图', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(canvas).not.toHaveAttribute('viewBox', initial!);
  await map.getByRole('button', { name: '地图右移', exact: true }).click();
  await map.getByRole('button', { name: '复位', exact: true }).click();
  await expect(canvas).toHaveAttribute('viewBox', initial!);
  const metric = map.getByRole('combobox', { name: '着色与排行指标', exact: true });
  for (const code of ['projectCount', 'expectedAmount', 'network', 'gewu', 'bigData', 'wangwu']) {
    await metric.selectOption(code);
    await expect(metric).toHaveValue(code);
  }
  await map.getByText(/^筛选区域（/).click();
  await map.getByRole('button', { name: '清空', exact: true }).click();
  await expect(map.getByText('请选择至少一个区域。', { exact: true })).toBeVisible();
  await map.getByRole('button', { name: '全选', exact: true }).click();
  await expect(map.getByText('请选择至少一个区域。', { exact: true })).toHaveCount(0);
  if (process.env.NDI_E2E_LIVE_MAP === '1') {
    await canvas.getByRole('button', { name: /^浙江省：/ }).click();
    const response = await request.get(
      'http://127.0.0.1:18080/api/v1/projects/workbench/map/boundaries/330000',
      {
        headers: { 'X-NDI-Actor-Id': ids.admin },
      },
    );
    expect(response.ok(), await response.text()).toBeTruthy();
    const boundaries = await response.json();
    expect(boundaries.features.length).toBeGreaterThan(1);
    await expect(canvas.locator('path')).toHaveCount(boundaries.features.length);
    await expect(map.getByText(/图形暂不可用/)).toHaveCount(0);
  }
  const activity = page.getByRole('region', { name: '近期项目变动', exact: true });
  await expect(activity.getByRole('link', { name, exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('workbench-current.png'), fullPage: true });
  await activity.getByRole('link', { name, exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/projects/${project.id}$`));
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
});
