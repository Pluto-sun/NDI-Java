import { revenueAmount, revenueCardAmount } from '../src/features/revenue-cockpit/format';
import { expect, test, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { chooseActor, get, ids } from './support/projects';
import { requiredRevenueFields } from './support/revenue';
import type { ReferenceData } from '../src/features/projects/types';
import type { AnnualOverview } from '../src/features/revenue-cockpit/annualOverview';

const path = '/workbench/revenue';
const root = 'http://127.0.0.1:18080/api/v1/revenue/entries';
const auth = (actor = ids.admin) => ({ headers: { 'X-NDI-Actor-Id': actor } });
const reviewer = '55555555-5555-4555-8555-555555555555';
const group = '77777777-7777-4777-8777-777777777777';

test('计收和排产使用各自具名标签页，刷新主页面后仍复用', async ({ page, context }) => {
  await page.goto('/projects?stage=COLLECTION');
  await chooseActor(page, ids.admin);
  const mainUrl = page.url();
  const entry = page.getByRole('link', { name: '计收驾驶舱', exact: true });
  const opening = context.waitForEvent('page');
  await entry.click();
  const cockpit = await opening;
  await expect(cockpit.getByTestId('revenue-board')).toBeVisible();
  await expect(cockpit.getByRole('navigation', { name: '模块导航' })).toHaveCount(0);
  await entry.click({ clickCount: 2 });
  expect(context.pages()).toHaveLength(2);
  const openingScheduling = context.waitForEvent('page');
  await page.getByRole('link', { name: '排产驾驶舱', exact: true }).click();
  const scheduling = await openingScheduling;
  await expect(scheduling.getByTestId('scheduling-board')).toBeVisible();
  expect(context.pages()).toHaveLength(3);
  await cockpit.evaluate(() => {
    document.documentElement.dataset.cockpitSentinel = 'retained';
  });
  await page.reload();
  await entry.click();
  expect(context.pages()).toHaveLength(3);
  expect(await cockpit.evaluate(() => document.documentElement.dataset.cockpitSentinel)).toBe(
    'retained',
  );
  expect(page.url()).toBe(mainUrl);
  await cockpit.close();

  const reopening = context.waitForEvent('page');
  await entry.click();
  const reopened = await reopening;
  await expect(reopened.getByTestId('revenue-board')).toBeVisible();
  await reopened.close();
  await page.evaluate(() => {
    window.open = () => null;
  });
  await entry.click();
  await expect(page.getByText('浏览器阻止了新标签页。')).toBeVisible();
  const fallback = context.waitForEvent('page');
  await page.getByRole('link', { name: '打开计收驾驶舱' }).click();
  await expect((await fallback).getByTestId('revenue-board')).toBeVisible();
  expect(page.url()).toBe(mainUrl);
});

async function addRecognition(
  request: APIRequestContext,
  year: number,
  fee: number,
  index: number,
  integratedProvinceId: string | null,
  integratedOrganization?: string,
) {
  const amount = String(2_000_000_000 + index * 100_000_000);
  const created = await request.post(root, {
    ...auth(),
    data: {
      source: {
        ...requiredRevenueFields,
        月账期: year + '-12',
        费用账期: year + '-' + String(fee).padStart(2, '0'),
        省份名称: '具体组织',
        '地市/组织名称': '年度总览验收组织',
        合同编号: randomUUID(),
        三化分类: index % 2 ? '数字化' : '智能化',
        条款金额: amount,
        '产品本月计收不含税额/标品含税收入': amount,
      },
    },
  });
  expect(created.ok(), await created.text()).toBe(true);
  const { id, version } = (await created.json()).data;
  const pending = await request.post(root + '/' + id + '/recognition', {
    ...auth(reviewer),
    data: {
      version,
      approverId: group,
      recognition: {
        amount,
        reportedProjectName: '年度总览验收',
        integratedProvinceId,
        integratedOrganization,
      },
    },
  });
  expect(pending.ok(), await pending.text()).toBe(true);
  const saved = (await pending.json()).data;
  const approved = await request.post(root + '/requests/' + saved.pendingRequestId + '/decision', {
    ...auth(group),
    data: {
      entryVersion: saved.version,
      requestVersion: 0,
      decision: 'APPROVED',
      opinion: '',
    },
  });
  expect(approved.ok(), await approved.text()).toBe(true);
}

test('年度快照在四种视口完整展示，明细内部滚动与组织行正常', async ({ page, request }, info) => {
  const before = await request.get(root + '/annual-overview', auth());
  expect(before.ok(), await before.text()).toBe(true);
  const prior: AnnualOverview = (await before.json()).data;
  const ref = await get<ReferenceData>(request, ids.admin, '/reference-data');
  const names = [
    '广东',
    '湖北',
    '江苏',
    '四川',
    '山东',
    '河南',
    '浙江',
    '安徽',
    '福建',
    '江西',
    '云南',
    '湖南',
    '河北',
  ];
  for (const [index, name] of names.entries()) {
    const province = ref.provinces.find((option) => option.name.startsWith(name))!;
    await addRecognition(
      request,
      prior.year,
      Math.min(prior.currentMonth, index + 1),
      index,
      province.id,
    );
  }
  for (const [index, org] of ['数科', '数智'].entries()) {
    await addRecognition(request, prior.year, prior.currentMonth, index + names.length, null, org);
  }

  await page.goto(path);
  await chooseActor(page, ids.admin);
  const board = page.getByTestId('revenue-board');
  const metrics = page.getByRole('region', { name: '年度计收指标' });
  await expect(metrics.getByRole('heading')).toHaveCount(6);
  await expect(metrics.getByText('13', { exact: true })).toBeVisible();
  await expect(page.getByRole('group', { name: '三化累计计收省分地图' })).toBeVisible();
  const ranking = page.getByRole('region', { name: '累计计收金额 TOP 10' });
  for (const org of ['数科', '数智'])
    await expect(ranking.getByRole('rowheader', { name: org })).toBeVisible();
  const details = page.getByRole('region', { name: '各省各月份计收及格物差距明细' });
  const scroll = details.getByRole('region', { name: '计收明细滚动区域' });
  const organization = details
    .getByRole('row')
    .filter({ has: page.getByRole('rowheader', { name: '数智', exact: true }) });
  expect((await organization.getByRole('cell').allTextContents()).slice(-6)).toEqual(
    Array(6).fill(''),
  );
  const sizes =
    info.project.name === 'small-screen'
      ? [[375, 812]]
      : [
          [1920, 1080],
          [1680, 944],
          [1366, 768],
        ];
  for (const [width, height] of sizes) {
    await page.setViewportSize({ width, height });
    await expect
      .poll(async () => Math.round((await board.boundingBox())!.width))
      .toBe(Math.round(Math.min(width, (height * 1680) / 944)));
    expect(
      await page.evaluate(() => [
        document.documentElement.scrollWidth,
        document.documentElement.scrollHeight,
      ]),
    ).toEqual([width, height]);
    expect(await scroll.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(
      true,
    );
    for (const table of await board.locator('table').all()) {
      expect(
        await table.evaluate(
          (element) => element.scrollWidth <= element.parentElement!.clientWidth,
        ),
      ).toBe(true);
    }
    await page.screenshot({ path: info.outputPath(`revenue-${width}x${height}.png`) });
  }
  await expect(details.getByRole('button', { name: '下一页' })).toHaveCount(0);
  const bounds = (await scroll.boundingBox())!;
  const x = bounds.x + bounds.width / 2;
  const y = bounds.y + bounds.height * 0.8;
  if (info.project.name === 'small-screen') {
    const device = await page.context().newCDPSession(page);
    await device.send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 1 });
    await device.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] });
    for (let step = 1; step <= 5; step++) {
      await device.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{ x, y: y - (step * bounds.height) / 10 }],
      });
    }
    await device.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await device.send('Emulation.setTouchEmulationEnabled', { enabled: false });
    await device.detach();
  } else {
    await page.mouse.move(x, y);
    await page.mouse.wheel(0, 70);
  }
  await expect.poll(async () => scroll.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
  const header = scroll.getByRole('columnheader', { name: '1月', exact: true });
  const position = (await header.boundingBox())!.y;
  await scroll.focus();
  await page.keyboard.press('End');
  await expect
    .poll(async () =>
      scroll.evaluate((element) => element.scrollHeight - element.scrollTop - element.clientHeight),
    )
    .toBeLessThan(2);
  expect((await header.boundingBox())!.y).toBeCloseTo(position, 0);
  await expect(scroll.locator('tbody tr').last().getByRole('rowheader')).toBeInViewport();
  await expect(metrics.getByText('千万元', { exact: true })).toHaveCount(5);
  await expect(details.getByText('三化计收（万元）', { exact: true })).toBeVisible();
  expect(revenueCardAmount('12345')).toBe('12.35');
  expect(revenueCardAmount('-12345.5')).toBe('-12.35');
  expect(revenueCardAmount('0.000001')).toBe('0');
  expect(revenueAmount('12345')).toBe('12,345');
  expect(revenueAmount('9023372036854775807.125')).toBe('9,023,372,036,854,775,807.13');
  const trend = page.getByRole('region', { name: '1–12 月三化计收趋势' });
  await trend.locator('g[tabindex]').first().focus();
  await expect(trend.getByRole('tooltip')).toContainText('1月');
  await expect(trend.getByRole('tooltip')).toContainText('万元');
  await page.reload();
  await expect(metrics.getByRole('heading')).toHaveCount(6);
  expect([401, 403]).toContain((await request.get(root + '/annual-overview')).status());
});
