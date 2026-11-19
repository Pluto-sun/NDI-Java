import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, ids } from './support/projects';
import { readFileSync } from 'node:fs';
const nationalBoundary = JSON.parse(
  readFileSync(new URL('./fixtures/national-boundary.json', import.meta.url), 'utf8'),
);

// 简化后的公开行政边界仅用于标签布局验收，不提供业务统计。
test('全国地图标签不重叠且位于省界上层，99.4%占比使用完整轨道', async ({ page }, testInfo) => {
  await page.route('**/workbench/map/boundaries/100000', (route) =>
    route.fulfill({ json: nationalBoundary }),
  );
  await page.route('**/workbench/dashboard?*', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.current.totalAmount = 1000;
    body.data.provinces = [
      {
        id: 'guangdong',
        name: '广东省',
        cities: [],
        summary: { ...body.data.current, totalAmount: 994 },
      },
      {
        id: 'zhejiang',
        name: '浙江省',
        cities: [],
        summary: { ...body.data.current, totalAmount: 6 },
      },
    ];
    await route.fulfill({ json: body });
  });
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  const map = page.locator('.dashboard-map');
  await expect(map.locator('.dashboard-map-label')).toHaveCount(31);
  await expect(map.locator('.dashboard-map-callout')).toHaveCount(3);
  for (const name of ['北京', '天津', '上海']) {
    await expect(map.locator('.dashboard-map-label').filter({ hasText: name })).toHaveCount(1);
  }
  for (const name of ['台湾', '香港', '澳门']) {
    await expect(map.locator('.dashboard-map-label').filter({ hasText: name })).toHaveCount(0);
  }
  const island = map.locator('path').filter({ hasText: '海南省' });
  expect((await island.getAttribute('d'))?.match(/M/g)).toHaveLength(1);
  const labels = await map.locator('.dashboard-map-label').evaluateAll((elements) =>
    elements.map((element) => {
      const box = (element as SVGGraphicsElement).getBBox();
      return { name: element.textContent, x: box.x, y: box.y, w: box.width, h: box.height };
    }),
  );
  for (let i = 0; i < labels.length; i++) {
    const a = labels[i];
    expect(a.x).toBeGreaterThanOrEqual(0);
    expect(a.x + a.w).toBeLessThanOrEqual(680);
    for (const b of labels.slice(i + 1)) {
      expect(
        a.x >= b.x + b.w || b.x >= a.x + a.w || a.y >= b.y + b.h || b.y >= a.y + a.h,
        `${a.name}与${b.name}不应重叠`,
      ).toBeTruthy();
    }
  }
  expect(
    await map
      .locator('svg')
      .evaluate((svg) => svg.lastElementChild?.classList.contains('dashboard-map-labels')),
  ).toBeTruthy();
  const row = page.locator('.dashboard-ranking tr').filter({ hasText: '广东省' });
  await expect(row).toContainText('99.4%');
  const ranking = page.locator('.dashboard-ranking .table-scroll');
  expect(
    await ranking.evaluate((element) => element.scrollWidth <= element.clientWidth),
  ).toBeTruthy();
  const ratio = await row
    .locator('.dashboard-share-track')
    .evaluate(
      (track) =>
        track.firstElementChild!.getBoundingClientRect().width /
        track.getBoundingClientRect().width,
    );
  expect(ratio).toBeCloseTo(0.994, 2);
  await map.getByRole('button', { name: '放大地图', exact: true }).click();
  await map.getByText('广东', { exact: true }).click();
  await expect(page.getByRole('heading', { name: '地市分布', exact: true })).toBeVisible();
  await map.getByRole('button', { name: '返回全国', exact: true }).click();
  await expect(page.getByRole('heading', { name: '省分分布', exact: true })).toBeVisible();
  await page
    .locator('.dashboard-region-grid')
    .screenshot({ path: testInfo.outputPath('region-layout.png') });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});

test('项目清单省略长文本、固定详情入口并保留详情标题', async ({ page, request }, testInfo) => {
  const name = `完整显示-${randomUUID()}-超长项目名称用于验证横向滚动与省略号超长项目名称用于验证横向滚动与省略号超长项目名称用于验证横向滚动与省略号`;
  await createProject(request, name);
  await page.goto('/projects');
  await chooseActor(page, ids.reporter);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(name);
  const queried = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return url.pathname === '/api/v1/projects' && url.searchParams.get('projectName') === name;
  });
  await page.getByRole('button', { name: '查询', exact: true }).click();
  expect((await queried).ok()).toBeTruthy();
  const table = page.getByRole('table', { name: '项目清单', exact: true });
  const row = table.locator('tbody tr').filter({ hasText: name });
  const action = row.getByRole('link', { name: `详情：${name}`, exact: true });
  await expect(action).toBeVisible();
  await expect(row.getByRole('link', { name, exact: true })).toHaveCount(0);
  await expect(table.getByRole('columnheader', { name: '项目名称', exact: true })).toBeVisible();
  await expect(table.getByRole('columnheader', { name: '行业', exact: true })).toBeVisible();
  const projectName = row.getByText(name, { exact: true });
  await projectName.focus();
  await expect(page.locator('.cell-tooltip')).toHaveText(name);
  await projectName.press('Escape');
  const scroll = table.locator('..');
  const originalX = (await action.boundingBox())!.x;
  await scroll.evaluate((element) => {
    element.scrollLeft = element.scrollWidth;
  });
  expect((await action.boundingBox())!.x).toBeCloseTo(originalX, 0);
  await scroll.screenshot({ path: testInfo.outputPath('project-table.png') });
  await action.click();
  const title = page.getByRole('heading', { name, exact: true });
  await expect(title).toBeVisible();
  await expect(page.locator('#project-detail-title')).toContainText('方案交流');
  await page.getByRole('heading', { name: '项目主档', exact: true }).scrollIntoViewIfNeeded();
  await expect(title).toBeInViewport();
  await page.screenshot({ path: testInfo.outputPath('project-header.png') });
  const overview = page.locator('.project-detail-overview');
  await expect(overview.getByRole('heading', { name: '项目主档', exact: true })).toBeVisible();
  const summary = page.getByRole('region', { name: '三化分类金额', exact: true });
  expect((await summary.boundingBox())!.height).toBeLessThan(200);
  expect((await overview.boundingBox())!.width).toBeGreaterThan(
    testInfo.project.name === 'desktop' ? 900 : 280,
  );
  await overview.screenshot({ path: testInfo.outputPath('project-overview.png') });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});

test('近期变动固定单行并可聚焦查看完整项目名称', async ({ page, request }, testInfo) => {
  const name = `动态表验收-${randomUUID()}-${'较长项目名称'.repeat(14)}`;
  const project = await createProject(request, name);
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  const table = page.getByRole('table', { name: '近期项目变动', exact: true });
  const link = table.getByRole('link', { name, exact: true });
  await expect(link).toBeVisible();
  const dimensions = await table
    .locator('tbody tr')
    .evaluateAll((rows) => rows.map((row) => row.getBoundingClientRect().height));
  expect(new Set(dimensions).size).toBe(1);
  await link.focus();
  await expect(page.locator('.cell-tooltip')).toHaveText(name);
  await link.press('Escape');
  await table.locator('..').screenshot({ path: testInfo.outputPath('dashboard-activity.png') });
  await link.click();
  await expect(page).toHaveURL(new RegExp('/projects/' + project.id + '$'));
});
