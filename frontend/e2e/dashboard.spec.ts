import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, get, ids } from './support/projects';

test('驾驶舱五阶段、真实统计、产品筛选与项目清单即时筛选', async ({ page, request }, testInfo) => {
  const name = `驾驶舱-${randomUUID()}`;
  await createProject(request, name);
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  const tabs = page.getByRole('group', { name: '项目阶段快捷筛选', exact: true });
  await expect(tabs.getByRole('button')).toHaveCount(5);
  const heading = page.getByRole('heading', { name: '项目管理驾驶舱', exact: true });
  await expect(
    heading.locator('..').getByRole('group', { name: '项目阶段快捷筛选', exact: true }),
  ).toBeVisible();
  const buttonBox = await tabs.getByRole('button').first().boundingBox();
  expect(buttonBox!.height).toBeLessThanOrEqual(36);
  if (testInfo.project.name === 'desktop') {
    const titleBox = await heading.boundingBox();
    const tabsBox = await tabs.boundingBox();
    expect(tabsBox!.x).toBeGreaterThan(titleBox!.x + titleBox!.width);
    expect(
      Math.abs(tabsBox!.y + tabsBox!.height / 2 - titleBox!.y - titleBox!.height / 2),
    ).toBeLessThan(3);
  }
  await expect(tabs.getByRole('button', { name: '全部', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await expect(page.locator('.dashboard-kpi')).toHaveCount(5);
  await expect(
    page.getByRole('heading', { name: '阶段分布概览（项目数／金额）', exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('heading', { name: '近期项目变动情况', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name, exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('dashboard-all.png'), fullPage: true });
  for (const [code, label] of [
    ['SOLUTION_EXCHANGE', '方案交流'],
    ['BIDDING', '招投标'],
    ['SIGNED', '签约'],
    ['COLLECTION', '计收'],
  ]) {
    const loaded = page.waitForResponse(
      (response) =>
        response.url().includes('/workbench/dashboard?') &&
        response.url().includes(`stage=${code}`),
    );
    await tabs.getByRole('button', { name: label, exact: true }).click();
    expect((await loaded).ok()).toBeTruthy();
    await expect(
      page.getByRole('heading', { name: `产品分布概览（${label}阶段）`, exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('heading', { name: `趋势分析（${label}阶段）`, exact: true }),
    ).toBeVisible();
    const now = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }).slice(0, 7);
    const stats = await get<{ current: { projectCount: number } }>(
      request,
      ids.admin,
      `/workbench/dashboard?from=${now.slice(0, 4)}-01&to=${now}&stage=${code}`,
    );
    await expect(page.locator('.dashboard-kpi').first().locator('strong')).toHaveText(
      String(stats.current.projectCount),
    );
  }
  await tabs.getByRole('button', { name: '方案交流', exact: true }).click();
  const regionFilters = page.locator('.dashboard-section-toolbar');
  await regionFilters
    .getByRole('combobox', { name: '三化类别', exact: true })
    .selectOption('DIGITAL');
  await regionFilters.getByRole('combobox', { name: '产品', exact: true }).selectOption('BIG_DATA');
  await expect(regionFilters.getByRole('combobox', { name: '产品', exact: true })).toHaveValue(
    'BIG_DATA',
  );
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('dashboard-solution.png'), fullPage: true });
  await page.getByRole('link', { name: '查看项目清单', exact: true }).click();
  await expect(page.getByRole('heading', { name: '项目列表', exact: true })).toBeVisible();
  const listTabs = page.getByRole('group', { name: '项目阶段快捷筛选', exact: true });
  await expect(
    page
      .getByRole('heading', { name: '项目列表', exact: true })
      .locator('..')
      .getByRole('group', { name: '项目阶段快捷筛选', exact: true }),
  ).toBeVisible();
  await expect(listTabs.getByRole('button', { name: '方案交流', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  const listRequest = page.waitForRequest(
    (req) => /\/api\/v1\/projects\?/.test(req.url()) && req.url().includes('stage=BIDDING'),
  );
  await listTabs.getByRole('button', { name: '招投标', exact: true }).click();
  await listRequest;
  await expect(listTabs.getByRole('button', { name: '招投标', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  const allRequest = page.waitForRequest(
    (req) => /\/api\/v1\/projects\?/.test(req.url()) && !req.url().includes('stage='),
  );
  await listTabs.getByRole('button', { name: '全部', exact: true }).click();
  await allRequest;
  await expect(listTabs.getByRole('button', { name: '全部', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
});
