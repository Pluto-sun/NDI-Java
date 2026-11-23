import { pickMonth } from './support/monthPicker';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { chooseActor, get, ids } from './support/projects';
import type { ReferenceData } from '../src/features/projects/types';

const api = 'http://127.0.0.1:18080/api/v1';
const headers = (actor = ids.admin) => ({ 'X-NDI-Actor-Id': actor });

async function add(request: APIRequestContext, path: string, data: unknown) {
  const response = await request.post(api + path, { headers: headers(), data });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data;
}
async function summary(request: APIRequestContext, path: string, actor = ids.admin) {
  const response = await request.get(api + path, { headers: headers(actor) });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data;
}
async function checkSticky(page: Page) {
  await page.locator('.module-dashboard').evaluate((content) => {
    (content as HTMLElement).style.minHeight = '180vh';
    window.scrollTo(0, document.body.scrollHeight);
  });
  const header = page.getByRole('banner');
  await expect.poll(async () => (await header.boundingBox())?.y).toBe(0);
  const control = header.locator('summary[aria-label], button[aria-pressed]').first();
  await expect(control).toBeInViewport();
  await control.click({ trial: true });
}
const production = '/production/plans';
const revenue = '/revenue/entries';

test('排产跨月汇总去重、产品金额精确、城市范围和固定标题栏', async ({ page, request }, info) => {
  const year = info.project.name === 'desktop' ? '2081' : '2082';
  const ref = await get<ReferenceData>(request, ids.admin, '/reference-data');
  const other = ref.cities.find((city) => city.provinceId !== ids.province)!;
  const plan = {
    reviewerName: '统计验收',
    planMonth: year + '-02',
    provinceId: ids.province,
    cityId: ids.city,
    projectName: randomUUID(),
    customerName: '验收客户',
    networkOrderAmount: '1.0001',
    gewuOrderAmount: '20',
    gewuRevenueAmount: '8.0002',
  };
  await add(request, production, plan);
  await add(request, production, {
    ...plan,
    projectName: randomUUID(),
    planMonth: year + '-03',
    provinceId: other.provinceId,
    cityId: other.id,
    networkOrderAmount: '50',
  });
  const deleted = await add(request, production, { ...plan, projectName: randomUUID() });
  expect(
    (
      await request.delete(api + production + '/' + deleted.id + '?version=' + deleted.version, {
        headers: headers(),
      })
    ).ok(),
  ).toBeTruthy();
  const range = `?from=${year}-02&to=${year}-03`;
  const national = await summary(request, production + '/dashboard' + range);
  expect(national).toMatchObject({ count: 2, amount: '107.0005' });
  expect(national.categories.slice(0, 2)).toMatchObject([
    { count: 2, amount: '51.0001' },
    { count: 2, amount: '56.0004' },
  ]);
  expect(national.provinces[0].amount).toBe('78.0002');
  expect(await summary(request, production + '/dashboard' + range, ids.reporter)).toMatchObject({
    count: 1,
    amount: '29.0003',
  });
  expect(
    (
      await request.get(api + production + '/dashboard?from=2026-02&to=2026-01', {
        headers: headers(),
      })
    ).status(),
  ).toBe(400);
  await page.goto('/workbench/scheduling' + range);
  await chooseActor(page, ids.admin);
  await expect(page.getByTestId('dashboard-total-count')).toHaveText('2');
  await checkSticky(page);
  await pickMonth(page.getByLabel('排产月份结束月', { exact: true }), year + '-02');
  await expect(page.getByTestId('dashboard-total-amount')).toHaveText('29.0003');
  await expect(page.getByRole('table', { name: '省分分布' }).locator('tbody tr')).toHaveCount(1);
  await page.screenshot({ path: info.outputPath('scheduling-dashboard.png'), fullPage: true });
});

test('计收月账期口径、未认定记录、具体组织与省内权限', async ({ page, request }, info) => {
  const month = info.project.name === 'desktop' ? '2083-02' : '2084-02';
  const ref = await get<ReferenceData>(request, ids.admin, '/reference-data');
  const province = ref.provinces.find((option) => option.id === ids.province)!.name;
  const source = {
    月账期: month,
    三化分类: '数字化',
    条款金额: '100000',
    '产品本月计收不含税额/标品含税收入': '2',
    合同编号: randomUUID(),
  };
  await add(request, revenue, { source: { ...source, 省份名称: province } });
  await add(request, revenue, { source: { ...source, 合同编号: randomUUID() } });
  await add(request, revenue, { source: { ...source, 合同编号: randomUUID(), 省份名称: '数科' } });
  await add(request, revenue, {
    source: { ...source, 合同编号: randomUUID(), 月账期: month.replace('-02', '-01') },
  });
  const noCategory = await request.post(api + revenue, {
    headers: headers(),
    data: { source: { ...source, 三化分类: '' } },
  });
  expect(noCategory.status()).toBe(400);
  const range = `?from=${month}&to=${month}`;
  const scoped = await summary(request, revenue + '/dashboard' + range, ids.reporter);
  expect(scoped).toMatchObject({ count: 1, amount: '0' });
  await page.goto('/workbench/revenue' + range);
  await chooseActor(page, ids.admin);
  await expect(page.getByTestId('dashboard-total-count')).toHaveText('3');
  await expect(page.getByRole('rowheader', { name: '未归属', exact: true })).toBeVisible();
  await expect(page.getByRole('rowheader', { name: '数科', exact: true })).toBeVisible();
  await checkSticky(page);
  await pickMonth(page.getByLabel('月账期起始月', { exact: true }), month.replace('-02', '-01'));
  await expect(page.getByTestId('dashboard-total-count')).toHaveText('4');
  await page.screenshot({ path: info.outputPath('revenue-dashboard.png') });
});

test('能力指标四个视图按各自台账计数并可在滚动时切换', async ({ page, request }, info) => {
  const prefix = randomUUID();
  const location = { provinceId: ids.province, cityId: ids.city };
  const datasets = [
    [
      'gewu-deployments',
      '格物部署清单',
      '部署项数量',
      {
        ...location,
        fields: {
          接口人: '验收',
          客户名称: '验收客户',
          项目名称: prefix,
          制造类型: '流程制造',
          国民经济行业分类: '食品制造业',
          所属行业: '食品',
          部署版本: '1',
          上一周期是否已接设备: '否',
        },
      },
    ],
    [
      'wangwu',
      '万悟部署情况',
      '部署记录总数',
      {
        provinceId: ids.province,
        fields: { 接口人: '验收', 部署地点: '企业', 具体名称: prefix, 部署进度: '未部署' },
      },
    ],
    [
      'agents-live',
      '已上线智能体',
      '上线智能体记录数',
      {
        provinceId: ids.province,
        fields: {
          接口人: '验收',
          省分智能化接口人: '验收',
          环节: '生产制造',
          应用项目名称: prefix,
          最终客户名称: '验收客户',
          智能体名称: prefix,
          智能体功能简述: '测试',
          是否为三方生态迁移: '否',
          是否计收: '未计收',
          是否在计收表中出现: '否',
        },
      },
    ],
    [
      'agents-development',
      '开发中智能体',
      '开发中智能体台账总数',
      {
        provinceId: ids.province,
        fields: { 智能体名称: prefix, 应用项目名称: prefix, 状态: '已完成' },
      },
    ],
  ] as const;
  const totals: number[] = [];
  for (const [kind, , , input] of datasets) {
    const root = '/capability/' + kind;
    const before = await summary(request, root + '/dashboard');
    const city = await summary(request, root + '/dashboard', ids.reporter);
    await add(request, root, input);
    expect((await summary(request, root + '/dashboard')).count).toBe(before.count + 1);
    expect((await summary(request, root + '/dashboard', ids.reporter)).count).toBe(
      city.count + (kind === 'gewu-deployments' ? 1 : 0),
    );
    totals.push(before.count + 1);
  }
  await page.goto('/workbench/capability');
  await chooseActor(page, ids.admin);
  const controls = page.getByRole('group', { name: '能力指标视图' });
  for (const [index, [, label, metric]] of datasets.entries()) {
    await controls.getByRole('button', { name: label, exact: true }).click();
    await expect(page.getByText(metric, { exact: true })).toBeVisible();
    await expect(page.getByTestId('dashboard-total-count')).toHaveText(String(totals[index]));
    if (index === 0) await checkSticky(page);
  }
  await page.screenshot({ path: info.outputPath('capability-dashboard.png') });
});
