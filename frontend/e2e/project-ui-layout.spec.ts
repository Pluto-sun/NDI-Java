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
      { id: 'guangdong', name: '广东省', summary: { ...body.data.current, totalAmount: 994 } },
      { id: 'zhejiang', name: '浙江省', summary: { ...body.data.current, totalAmount: 6 } },
    ];
    await route.fulfill({ json: body });
  });
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  const map = page.locator('.dashboard-map');
  await expect(map.locator('.dashboard-map-label')).toHaveCount(29);
  await expect(map.locator('.dashboard-map-leader')).toHaveCount(0);
  const compact = map.getByRole('group', { name: '小面积区域快捷查看', exact: true });
  await expect(compact.getByRole('button')).toHaveText(['北京', '天津', '上海', '香港', '澳门']);
  await compact.getByRole('button', { name: '上海', exact: true }).focus();
  await expect(map.getByRole('status')).toContainText('上海市');
  await expect(map.locator('.dashboard-map-province.is-active')).toHaveCount(1);
  await compact.getByRole('button', { name: '北京', exact: true }).click();
  await expect(map.getByRole('status')).toContainText('北京市');
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
  await page
    .locator('.dashboard-region-grid')
    .screenshot({ path: testInfo.outputPath('region-layout.png') });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});

test('项目清单固定列宽完整换行，详情金额摘要紧凑且主档使用完整宽度', async ({
  page,
  request,
}, testInfo) => {
  const name = `完整显示-${randomUUID()}-超长项目名称用于验证完整换行而不是省略号`;
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
  await expect(table.getByRole('link', { name, exact: true })).toBeVisible();
  await expect(table.getByRole('columnheader', { name: '创建时间', exact: true })).toHaveCount(0);
  await expect(table.getByRole('columnheader', { name: '项目日期', exact: true })).toHaveCount(0);
  await expect(table.locator('tbody tr').filter({ hasText: name }).locator('td')).toHaveCount(9);
  const cells = await table
    .locator('tbody tr')
    .first()
    .locator('td')
    .evaluateAll((elements) =>
      elements.map((e) => ({
        width: e.getBoundingClientRect().width,
        fits: e.scrollWidth <= e.clientWidth,
        whiteSpace: getComputedStyle(e).whiteSpace,
      })),
    );
  expect(cells).toHaveLength(9);
  expect(cells.map((cell) => Math.round(cell.width))).toEqual([
    320, 120, 180, 180, 110, 130, 112, 180, 120,
  ]);
  expect(cells.every((c) => c.fits && c.whiteSpace === 'normal')).toBeTruthy();
  await table.screenshot({ path: testInfo.outputPath('project-table.png') });
  await table.getByRole('link', { name, exact: true }).click();
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
