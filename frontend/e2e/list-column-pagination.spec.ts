import { requiredRevenueFields } from './support/revenue';
import { verifyStickyAction } from './support/stickyActions';
import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, ids } from './support/projects';

test('排产金额与表头同轴，排产及计收共用右侧分页并可翻页', async ({ page, request }, testInfo) => {
  const prefix = `列表对齐-${randomUUID()}`;
  for (let index = 0; index < 21; index++) {
    const plan = await request.post('http://127.0.0.1:18080/api/v1/production/plans', {
      headers: { 'X-NDI-Actor-Id': ids.reporter },
      data: {
        reviewerName: '排产接口人',
        planMonth: '2026-01',
        provinceId: ids.province,
        cityId: ids.city,
        projectName: `${prefix}-${index}`,
        customerName: '分页验收客户',
        categories: ['NETWORK', 'GEWU', 'BIG_DATA', 'WANGWU'],
        networkOrderAmount: '11.11',
        gewuOrderAmount: '222.22',
        bigDataOrderAmount: '3333.33',
        wangwuOrderAmount: '44444.44',
        projectStatus: '推进中',
      },
    });
    expect(plan.ok(), await plan.text()).toBeTruthy();
    const revenue = await request.post('http://127.0.0.1:18080/api/v1/revenue/entries', {
      headers: { 'X-NDI-Actor-Id': ids.admin },
      data: {
        source: {
          ...requiredRevenueFields,
          月账期: '202611',
          自然客户名称: '分页自然客户',
          省份名称: '本地演示省',
          合同编号: `${prefix}-${index}`,
          条款编号: '0001',
          三化分类: '数字化',
          条款金额: '100.00',
          '产品本月计收不含税额/标品含税收入': '10.00',
          项目名称: prefix,
        },
      },
    });
    expect(revenue.ok(), await revenue.text()).toBeTruthy();
  }
  await page.goto(`/scheduling?search=${encodeURIComponent(prefix)}`);
  await chooseActor(page, ids.reporter);
  const table = page.getByRole('table', { name: '排产计划清单', exact: true });
  await expect(table.locator('tbody tr')).toHaveCount(20);
  const cells = table.locator('tbody tr').first().locator('td.numeric');
  await expect(cells).toHaveText(['11.11', '222.22', '3333.33', '44444.44']);
  const headerGeometry = await table.locator('thead th.numeric').evaluateAll((elements) =>
    elements.map((e) => ({
      x: e.getBoundingClientRect().x,
      width: e.getBoundingClientRect().width,
      align: getComputedStyle(e).textAlign,
      padding: getComputedStyle(e).paddingRight,
    })),
  );
  const cellGeometry = await cells.evaluateAll((elements) =>
    elements.map((e) => ({
      x: e.getBoundingClientRect().x,
      width: e.getBoundingClientRect().width,
      align: getComputedStyle(e).textAlign,
      padding: getComputedStyle(e).paddingRight,
    })),
  );
  expect(headerGeometry).toEqual(cellGeometry);
  expect(headerGeometry.every((c) => c.align === 'right')).toBeTruthy();
  const region = page.getByRole('region', { name: '排产明细横向滚动区域', exact: true });
  await verifyStickyAction(region, table.getByRole('link', { name: '编辑', exact: true }).first());
  const regionBox = await region.boundingBox();
  const edit = await table
    .locator('tbody tr')
    .first()
    .getByRole('link', { name: '编辑', exact: true })
    .boundingBox();
  expect(edit!.x).toBeGreaterThan(regionBox!.x);
  expect(edit!.x + edit!.width).toBeLessThanOrEqual(regionBox!.x + regionBox!.width);
  await page.screenshot({
    path: testInfo.outputPath('production-list-overview.png'),
    fullPage: false,
  });
  for (const [route, label] of [
    ['scheduling', '排产计划'],
    ['revenue', '计收列表'],
  ]) {
    await page.goto(`/${route}?search=${encodeURIComponent(prefix)}`);
    if (route === 'revenue') {
      const scroller = page.locator('.revenue-list-scroll');
      await verifyStickyAction(
        scroller,
        scroller.getByRole('link', { name: '详情', exact: true }).first(),
      );
    }
    const pager = page.getByRole('navigation', { name: `${label}分页`, exact: true });
    await expect(pager).toContainText('共 21 条');
    await expect(pager).toContainText('第 1 / 2 页');
    await expect(pager.getByRole('button', { name: '上一页', exact: true })).toBeDisabled();
    const box = await pager.boundingBox();
    const controls = await pager.locator('.list-pagination-controls').boundingBox();
    expect(controls!.x + controls!.width).toBeGreaterThan(box!.x + box!.width - 24);
    expect(controls!.x).toBeGreaterThanOrEqual(box!.x);
    await pager.getByRole('button', { name: '下一页', exact: true }).click();
    await expect(pager).toContainText('第 2 / 2 页');
    await expect(pager.getByRole('button', { name: '下一页', exact: true })).toBeDisabled();
    await page.screenshot({ path: testInfo.outputPath(`${route}-page-two.png`), fullPage: true });
    await pager.getByRole('button', { name: '上一页', exact: true }).click();
    await expect(pager).toContainText('第 1 / 2 页');
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
  }
});

test('项目清单分页与计收和排产一致，并保留查询条件', async ({ page, request }) => {
  const prefix = `项目分页-${randomUUID()}`;
  for (let index = 0; index < 21; index++) {
    await createProject(request, `${prefix}-${index}`);
  }

  await page.goto(`/projects?projectName=${encodeURIComponent(prefix)}`);
  await chooseActor(page, ids.reporter);
  const pager = page.getByRole('navigation', { name: '项目列表分页', exact: true });
  await expect(pager).toContainText('共 21 条');
  await expect(pager).toContainText('第 1 / 2 页');
  await expect(pager.getByRole('button', { name: '上一页' })).toBeDisabled();
  await pager.getByRole('button', { name: '下一页' }).focus();
  await page.keyboard.press('Enter');
  await expect(pager).toContainText('第 2 / 2 页');
  await expect(pager.getByRole('button', { name: '下一页' })).toBeDisabled();
  expect(new URL(page.url()).searchParams.get('projectName')).toBe(prefix);
  await pager.getByRole('button', { name: '上一页' }).click();
  await expect(pager).toContainText('第 1 / 2 页');
});
