import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, ids } from './support/projects';
import type { Approval, Entry } from '../src/features/revenue/api';

test('计收列表长文本、金额对齐、横向滚动、筛选返回和空状态', async ({
  page,
  request,
}, testInfo) => {
  const prefix = `计收布局-${randomUUID()}`;
  const root = 'http://127.0.0.1:18080/api/v1/revenue/entries';
  for (let index = 0; index < 3; index++) {
    const result = await request.post(root, {
      headers: { 'X-NDI-Actor-Id': ids.admin },
      data: {
        source: {
          月账期: '202611',
          省份名称: '本地演示省',
          '地市/组织名称': '工业互联网示范基地运营服务中心',
          项目名称: `${prefix}-${index}智能制造产线设备联网及工业数据采集分析服务`,
          客户名称: '先进制造产业集团股份有限公司',
          合同编号: `${prefix}-${index}`,
          条款编号: '2',
          三化分类: '数字化',
          条款金额: index === 1 ? '9999999999999999.99' : '12345678.90',
          '产品本月计收不含税额/标品含税收入': index === 1 ? '-9999999999999999.99' : '123456.78',
        },
      },
    });
    expect(result.ok(), await result.text()).toBeTruthy();
    const entry = (await result.json()).data as Entry;
    if (index < 2) {
      const recognition = await request.post(`${root}/${entry.id}/recognition`, {
        headers: {
          'X-NDI-Actor-Id': '55555555-5555-4555-8555-555555555555',
        },
        data: {
          version: entry.version,
          approverId: '77777777-7777-4777-8777-777777777777',
          recognition: {
            amount: '180.00',
            reportedProjectName: '布局验收项目',
            projectStatus: '',
            integratedProvinceId: ids.province,
          },
        },
      });
      expect(recognition.ok(), await recognition.text()).toBeTruthy();
      if (index === 0) {
        const pending = (await recognition.json()).data as Entry;
        const history = await request.get(`${root}/${entry.id}/requests`, {
          headers: { 'X-NDI-Actor-Id': '55555555-5555-4555-8555-555555555555' },
        });
        expect(history.ok(), await history.text()).toBeTruthy();
        const [approval] = (await history.json()).data as Approval[];
        const decision = await request.post(`${root}/requests/${approval.id}/decision`, {
          headers: { 'X-NDI-Actor-Id': '77777777-7777-4777-8777-777777777777' },
          data: {
            entryVersion: pending.version,
            requestVersion: approval.version,
            decision: 'APPROVED',
          },
        });
        expect(decision.ok(), await decision.text()).toBeTruthy();
      }
    }
  }
  const listUrl = `/revenue?month=2026-11&search=${encodeURIComponent(prefix)}`;
  await page.goto(listUrl);
  await chooseActor(page, ids.admin);
  const table = page.getByRole('table', { name: '计收明细清单', exact: true });
  await expect(table.locator('tbody tr')).toHaveCount(3);
  const summary = page.getByRole('region', { name: '当前筛选汇总', exact: true });
  await expect(summary.locator('strong')).toHaveText(['3', '2', '1', '1', '180.00']);
  const amountCells = table.locator('tbody tr').first().locator('.revenue-numeric');
  await expect(amountCells).toHaveText(['12,345,678.90', '123,456.78', '--']);
  const recognized = table.locator('tbody tr').filter({ hasText: '已认定' });
  await expect(recognized.locator('.revenue-recognized-amount')).toHaveText('180.00');
  const alignment = await table.evaluate((element) => {
    const geometry = (row: string) =>
      Array.from(element.querySelectorAll(`${row} .revenue-numeric`)).map((cell) => ({
        x: cell.getBoundingClientRect().x,
        width: cell.getBoundingClientRect().width,
        align: getComputedStyle(cell).textAlign,
      }));
    return { head: geometry('thead tr'), body: geometry('tbody tr:first-child') };
  });
  expect(alignment.head).toEqual(alignment.body);
  expect(alignment.head.every((cell) => cell.align === 'right')).toBeTruthy();
  expect(
    await table
      .locator('tbody td')
      .evaluateAll((cells) => cells.every((cell) => cell.scrollWidth <= cell.clientWidth)),
  ).toBeTruthy();

  const scroll = page.getByRole('region', { name: '计收明细横向滚动区域', exact: true });
  const bounds = await scroll.boundingBox();
  const detail = table.locator('tbody tr').first().getByRole('link', { name: '详情', exact: true });
  const detailBounds = await detail.boundingBox();
  expect(detailBounds!.x).toBeGreaterThan(bounds!.x);
  expect(detailBounds!.x + detailBounds!.width).toBeLessThan(bounds!.x + bounds!.width + 1);
  expect(
    await table
      .locator('th')
      .evaluateAll((cells) => cells.every((cell) => cell.scrollWidth <= cell.clientWidth)),
  ).toBeTruthy();
  const controls = await page
    .getByRole('form', { name: '计收筛选', exact: true })
    .evaluate((form) =>
      Array.from(form.querySelectorAll('input, select, button, summary'))
        .filter((control) => control.matches('summary') || !control.closest('details:not([open])'))
        .map((control) => {
          const box = control.getBoundingClientRect();
          const name = control.getAttribute('aria-label') ?? control.parentElement?.textContent;
          return { x: box.x, right: box.right, y: box.y, bottom: box.bottom, name };
        })
        .filter((control) => control.right > control.x),
    );
  controls.forEach((control, index) => {
    expect(control.x).toBeGreaterThanOrEqual(0);
    expect(control.right).toBeLessThanOrEqual(testInfo.project.use.viewport!.width);
    controls.slice(index + 1).forEach((other) => {
      expect(
        control.right <= other.x ||
          other.right <= control.x ||
          control.bottom <= other.y ||
          other.bottom <= control.y,
        `筛选控件位置重叠：${control.name?.trim()} / ${other.name?.trim()}`,
      ).toBeTruthy();
    });
  });
  await page.screenshot({ path: testInfo.outputPath('revenue-list-overview.png'), fullPage: true });
  await scroll.evaluate((element) => {
    element.scrollLeft = element.scrollWidth;
  });
  await page.screenshot({ path: testInfo.outputPath('revenue-list-amounts.png'), fullPage: true });
  await detail.click();
  await page.getByRole('link', { name: '返回计收列表', exact: true }).click();
  await expect(page.getByLabel('项目／客户／合同／条款', { exact: true })).toHaveValue(prefix);
  await expect(page.getByLabel('月账期', { exact: true })).toHaveText('2026-11');
  const state = page.getByRole('combobox', { name: '办理状态', exact: true });
  await state.selectOption('RECOGNIZED');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(table.locator('tbody tr')).toHaveCount(1);
  await page.goBack();
  await expect(state).toHaveValue('');
  await expect(table.locator('tbody tr')).toHaveCount(3);
  await page
    .getByLabel('项目／客户／合同／条款', { exact: true })
    .fill('无匹配记录-' + randomUUID());
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByText('暂无符合条件的计收明细', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '重置', exact: true }).click();
  await expect(page).toHaveURL('/revenue');
  await expect(table.locator('tbody tr').first()).toBeVisible();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});
