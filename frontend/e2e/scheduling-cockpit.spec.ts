import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { requiredRevenueFields } from './support/revenue';
import { chooseActor, get, ids } from './support/projects';
import type { ReferenceData } from '../src/features/projects/types';

const path = '/workbench/scheduling';
const api = 'http://127.0.0.1:18080/api/v1/production/plans';
const title = '工业互联网“三化”排产与计收监控驾驶舱';
const headers = { 'X-NDI-Actor-Id': ids.admin };

async function chooseEndMonth(page: Page, month: number) {
  const field = page.getByLabel('结束月份', { exact: true });
  await field.click();
  const panel = page.getByRole('group', { name: '结束月份选项' });
  await expect(panel).toBeInViewport();
  const anchor = (await field.boundingBox())!;
  const popup = (await panel.boundingBox())!;
  expect(popup.x).toBeLessThan(anchor.x + anchor.width);
  expect(popup.x + popup.width).toBeGreaterThan(anchor.x);
  expect(
    Math.min(
      Math.abs(popup.y - anchor.y - anchor.height),
      Math.abs(popup.y + popup.height - anchor.y),
    ),
  ).toBeLessThan(8);
  await panel.getByRole('button', { name: month + '月', exact: true }).click();
  await expect(panel).toBeHidden();
}

test('同一入口复用独立标签页，拦截后可手动打开', async ({ page, context }) => {
  await page.goto('/projects?stage=EXCHANGE');
  await chooseActor(page, ids.admin);
  const mainUrl = page.url();
  const entry = page.getByRole('link', { name: '排产驾驶舱', exact: true });
  const popupPromise = context.waitForEvent('page');
  await entry.click();
  const popup = await popupPromise;
  await expect(popup.getByRole('heading', { name: title })).toBeVisible();
  await expect(popup.getByRole('navigation', { name: '模块导航' })).toHaveCount(0);
  expect(page.url()).toBe(mainUrl);
  await entry.click({ clickCount: 2 });
  expect(context.pages()).toHaveLength(2);

  await chooseEndMonth(popup, 1);
  const chosen = popup.url();
  expect(chosen).toMatch(/to=\d{4}-01/);
  const sentinel = await popup.evaluate(() => {
    return ((window as Window & { cockpitSentinel?: number }).cockpitSentinel = Date.now());
  });
  await page.getByRole('link', { name: '排产计划', exact: true }).click();
  await expect(page).toHaveURL(/\/scheduling$/);
  await entry.click();
  await page.reload();
  await entry.click();
  expect(context.pages()).toHaveLength(2);
  expect(popup.url()).toBe(chosen);
  expect(
    await popup.evaluate(() => (window as Window & { cockpitSentinel?: number }).cockpitSentinel),
  ).toBe(sentinel);

  await popup.reload();
  await expect(popup.getByLabel('结束月份', { exact: true })).toHaveText(/\d{4}-01/);
  await popup.close();
  const reopen = context.waitForEvent('page');
  await entry.click();
  const reopened = await reopen;
  await expect(reopened.getByRole('heading', { name: title })).toBeVisible();
  expect(reopened.url()).not.toContain('?');
  await reopened.close();

  await page.evaluate(() => {
    window.open = () => null;
  });
  await entry.click();
  await expect(page.getByText('浏览器阻止了新标签页。')).toBeVisible();
  const fallbackPopup = context.waitForEvent('page');
  await page.getByRole('link', { name: '打开排产驾驶舱' }).click();
  const fallback = await fallbackPopup;
  await expect(fallback.getByRole('heading', { name: title })).toBeVisible();
  await expect(page).toHaveURL(/\/scheduling$/);
});

async function seedRevenue(
  request: APIRequestContext,
  month: string,
  organization: string,
  provinceId: string,
  amount: number,
  category: string,
) {
  const root = 'http://127.0.0.1:18080/api/v1/revenue';
  const reviewer = '55555555-5555-4555-8555-555555555555';
  const approver = '77777777-7777-4777-8777-777777777777';
  const created = await request.post(root + '/entries', {
    headers,
    data: {
      source: {
        ...requiredRevenueFields,
        月账期: month.slice(0, 4) + '-12',
        费用账期: month,
        省份名称: '具体组织',
        '地市/组织名称': organization,
        合同编号: 'COCKPIT-' + month,
        三化分类: category,
        条款金额: amount.toString(),
        '产品本月计收不含税额/标品含税收入': amount.toString(),
      },
    },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const { id, version } = (await created.json()).data;
  const listed = await request.get(root + '/organizations', { headers });
  expect(listed.ok()).toBeTruthy();
  const item = (await listed.json()).data.find(
    (value: { name: string }) => value.name === organization,
  );
  const mapped = await request.put(root + '/organizations/' + item.key, {
    headers,
    data: { provinceId },
  });
  expect(mapped.ok(), await mapped.text()).toBeTruthy();
  const pending = await request.post(root + '/entries/' + id + '/recognition', {
    headers: { 'X-NDI-Actor-Id': reviewer },
    data: {
      version,
      approverId: approver,
      recognition: {
        amount: amount.toString(),
        reportedProjectName: '认定去重验收项目',
        projectStatus: '实施中',
      },
    },
  });
  expect(pending.ok(), await pending.text()).toBeTruthy();
  const pendingEntry = (await pending.json()).data;
  const approved = await request.post(
    root + '/entries/requests/' + pendingEntry.pendingRequestId + '/decision',
    {
      headers: { 'X-NDI-Actor-Id': approver },
      data: {
        entryVersion: pendingEntry.version,
        requestVersion: 0,
        decision: 'APPROVED',
        opinion: '',
      },
    },
  );
  expect(approved.ok(), await approved.text()).toBeTruthy();
}

test('真实排产汇总驱动六张卡、地图、趋势和表内分页', async ({ page, request }, info) => {
  const year = info.project.name === 'desktop' ? '2085' : '2086';
  const ref = await get<ReferenceData>(request, ids.admin, '/reference-data');
  const provinces = [
    ...['广东', '浙江', '山东', '江苏', '福建', '湖北', '四川', '河南', '安徽'].map((name) =>
      ref.provinces.find((candidate) => candidate.name.startsWith(name))!,
    ),
  ];
  for (const [index, province] of provinces.entries()) {
    const city = ref.cities.find((candidate) => candidate.provinceId === province.id)!;
    const response = await request.post(api, {
      headers,
      data: {
        reviewerName: '验收接口人',
        planMonth: year + '-' + String(1 + index).padStart(2, '0'),
        provinceId: province.id,
        cityId: city.id,
        projectName: '驾驶舱同名项目',
        customerName: '验收客户',
        networkOrderAmount: 2000 + 110 * index,
        gewuOrderAmount: 7000 + 130 * index,
        wangwuOrderAmount: 4000 + 90 * index,
      },
    });
    expect(response.ok(), await response.text()).toBeTruthy();
    const recognized = [
      140_000_000, 85_000_000, 102_000_000, 130_000_000, 91_000_000, 150_000_000, 150_500_000,
      138_000_000, 161_000_000,
    ][index];
    await seedRevenue(
      request,
      year + '-' + String(1 + index).padStart(2, '0'),
      '排产驾驶舱验收组织' + year + province.name,
      province.id,
      recognized,
      index % 2 ? '智能化' : '数字化',
    );
  }
  const range = `?from=${year}-01&to=${year}-10`;
  await page.goto(path + range);
  await expect(page.getByRole('banner')).toHaveCount(0);
  await chooseActor(page, ids.admin);
  const board = page.getByTestId('scheduling-board');
  const metrics = page.getByRole('region', { name: '累计指标' });
  await expect(metrics.getByRole('heading')).toHaveCount(6);
  await expect(metrics.getByText('12.89', { exact: true }).first()).toBeVisible();
  await expect(metrics.getByText('1', { exact: true })).toHaveCount(2);
  await expect(metrics.getByText('11.48', { exact: true })).toBeVisible();
  await expect(page.getByRole('group', { name: '全国省分排产计收地图' })).toBeVisible();
  const first = page.getByRole('region', { name: '全国三化排产计收区域分布' });
  const second = page.getByRole('region', { name: '月度排产与计收趋势' });
  const layouts =
    info.project.name === 'small-screen'
      ? [[375, 812]]
      : [
          [1920, 1080],
          [1680, 960],
          [1366, 768],
        ];
  for (const [width, height] of layouts) {
    await page.setViewportSize({ width, height });
    await expect
      .poll(async () => Math.round((await board.boundingBox())!.width))
      .toBe(Math.round(Math.min(width, (height * 1680) / 960)));
    const bounds = (await board.boundingBox())!;
    expect(bounds.x).toBeGreaterThanOrEqual(-1);
    expect(bounds.y).toBeGreaterThanOrEqual(-1);
    expect(bounds.x * 2 + bounds.width).toBeCloseTo(width, 0);
    expect(bounds.y * 2 + bounds.height).toBeCloseTo(height, 0);
    expect((await first.boundingBox())?.height).toBeCloseTo(
      (await second.boundingBox())!.height,
      0,
    );
    expect(
      await page.evaluate(() => ({
        x: document.documentElement.scrollWidth - innerWidth,
        y: document.documentElement.scrollHeight - innerHeight,
      })),
    ).toEqual({ x: 0, y: 0 });
    for (const section of await page.locator('.sc-bottom .sc-data').all()) {
      expect(
        await section.evaluate(
          (element) => element.scrollWidth <= element.parentElement!.clientWidth,
        ),
      ).toBe(true);
    }
    await page.screenshot({ path: info.outputPath(`scheduling-${width}x${height}.png`) });
  }

  const table = page.getByRole('region', { name: '各省三化月度排产与计收情况' });
  await expect(table.locator('tbody tr')).toHaveCount(6);
  await table.getByRole('button', { name: '省分月度表下一页' }).click();
  await expect(table.locator('tbody tr')).toHaveCount(5);
  await expect(table.getByText('2 / 2')).toBeVisible();
  const graph = second.getByRole('group', { name: '月度金额柱状图与兑现率折线图' });
  const january = graph.locator('g[tabindex]').first();
  await january.focus();
  await expect(second.getByRole('tooltip')).toContainText('13,000 万元');
  await january.hover();
  await expect(second.getByRole('tooltip')).toContainText(year + '-01');

  await chooseEndMonth(page, 1);
  await expect(metrics.getByText('1.30', { exact: true }).first()).toBeVisible();
  await expect(table.locator('tbody tr')).toHaveCount(2);
  await expect(page.getByText('区间内没有可比较的上月。')).toBeVisible();

  await page.route('**/production/plans/scheduling-overview?*', (route) =>
    route.fulfill({
      status: 403,
      json: { code: 'PRODUCTION_FORBIDDEN', message: '拒绝访问' },
    }),
  );
  await page.reload();
  await expect(page.getByRole('alert')).toHaveText(/没有查看排产驾驶舱的权限/);
  const unauthorized = await request.get(api + '/scheduling-overview' + range);
  expect([401, 403]).toContain(unauthorized.status());
});
