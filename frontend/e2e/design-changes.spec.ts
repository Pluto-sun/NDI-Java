import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { ids, chooseActor, get, post } from './support/projects';
import type { ProjectDetail } from '../src/features/projects/types';

test('四区月份独立，阶段切换保留时间，地图支持缩放和键盘平移', async ({ page }, testInfo) => {
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  await expect(page.getByRole('button', { name: '刷新驾驶舱', exact: true })).toBeEnabled();
  const main = page.getByRole('form', { name: '经营总览时间筛选', exact: true });
  const region = page.getByRole('form', { name: '区域时间筛选', exact: true });
  const trend = page.getByRole('form', { name: '趋势时间筛选', exact: true });
  const activity = page.getByRole('form', { name: '近期变动时间筛选', exact: true });
  const original = await activity.getByLabel('变动月份起始', { exact: true }).inputValue();
  const requests: URL[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/workbench/dashboard?')) requests.push(new URL(request.url()));
  });
  await main.getByLabel('所属月份起始', { exact: true }).fill('2000-01');
  await main.getByLabel('所属月份结束', { exact: true }).fill('2000-01');
  await main.getByRole('button', { name: '应用月份', exact: true }).click();
  await expect(page.locator('.dashboard-kpi').first().locator('strong')).toHaveText('0');
  await expect(region.getByLabel('区域月份起始', { exact: true })).toHaveValue(original);
  await expect(trend.getByLabel('趋势月份起始', { exact: true })).toHaveValue(original);
  await expect(activity.getByLabel('变动月份起始', { exact: true })).toHaveValue(original);
  expect(
    requests
      .filter((url) => url.searchParams.get('from') === '2000-01')
      .every((url) => url.searchParams.get('part') === 'OVERVIEW'),
  ).toBeTruthy();
  for (const [form, label, year] of [
    [region, '区域', '2001'],
    [trend, '趋势', '2002'],
  ] as const) {
    await form.getByLabel(`${label}月份起始`, { exact: true }).fill(`${year}-01`);
    await form.getByLabel(`${label}月份结束`, { exact: true }).fill(`${year}-01`);
    await form.getByRole('button', { name: `应用${label}月份`, exact: true }).click();
  }
  await activity.getByLabel('变动月份起始', { exact: true }).fill('2003-01');
  await activity.getByLabel('变动月份结束', { exact: true }).fill('2003-01');
  await activity.getByRole('button', { name: '查询变动', exact: true }).click();
  await page
    .getByRole('group', { name: '项目阶段快捷筛选', exact: true })
    .getByRole('button', { name: '招投标', exact: true })
    .click();
  await expect(main.getByLabel('所属月份起始', { exact: true })).toHaveValue('2000-01');
  await expect(region.getByLabel('区域月份起始', { exact: true })).toHaveValue('2001-01');
  await expect(trend.getByLabel('趋势月份起始', { exact: true })).toHaveValue('2002-01');
  await expect(activity.getByLabel('变动月份起始', { exact: true })).toHaveValue('2003-01');
  await expect(page.getByText(/较上期|上期为/)).toHaveCount(0);
  const svg = page.getByRole('img', {
    name: '省分项目分布地图，具体数据见省分排名表',
    exact: true,
  });
  if (process.env.NDI_E2E_LIVE_MAP === '1') {
    await expect.poll(() => svg.locator('path').count()).toBeGreaterThan(30);
    await expect(svg.getByText('广东', { exact: true })).toBeVisible();
  }
  await page.getByRole('button', { name: '放大地图', exact: true }).click();
  await expect(svg).not.toHaveAttribute('viewBox', '0 0 680 440');
  const before = await svg.getAttribute('viewBox');
  await svg.focus();
  await page.keyboard.press('ArrowRight');
  await expect(svg).not.toHaveAttribute('viewBox', before!);
  await page.getByRole('button', { name: '重置地图', exact: true }).click();
  await expect(svg).toHaveAttribute('viewBox', '0 0 680 440');
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath('independent-dashboard-ranges.png'),
    fullPage: true,
  });
});

test('负责人自由名单在详情弹窗配置职责与账号，省分负责人唯一且允许多人客户经理', async ({
  page,
  request,
}, testInfo) => {
  const project = await post<{ id: string }>(request, ids.reporter, '', {
    provinceId: ids.province,
    cityId: ids.city,
    projectName: `自由人员-${randomUUID()}`,
    customerName: '客户',
    industryCode: 'C25',
    customerNeed: '人员配置验收',
    totalAmount: '100',
    responsiblePersonNames: ['负责人甲', '负责人乙'],
    categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50' }],
    initialProgress: '开始交流',
    nextAction: '配置人员',
  });
  await page.goto(`/projects/${project.id}`);
  await chooseActor(page, ids.reporter);
  await expect(
    page.locator('.project-person-list').getByText('负责人甲', { exact: true }),
  ).toBeVisible();
  const initial = await get<ProjectDetail>(request, ids.reporter, `/${project.id}`);
  expect(
    initial.responsiblePersons.every(
      (person) => person.userId === null && person.responsibility === null,
    ),
  ).toBeTruthy();
  await page.getByRole('button', { name: '配置人员', exact: true }).click();
  const modal = page.getByRole('dialog', { name: '配置项目负责人', exact: true });
  const first = modal.getByRole('group', { name: '负责人 1', exact: true });
  await first
    .getByRole('combobox', { name: '项目职责', exact: true })
    .selectOption('PROVINCE_LEADER');
  await expect(first.getByRole('combobox', { name: '关联系统账号', exact: true })).toBeEnabled();
  await first.getByRole('combobox', { name: '关联系统账号', exact: true }).selectOption(ids.leader);
  const second = modal.getByRole('group', { name: '负责人 2', exact: true });
  await expect(second.locator('option[value="PROVINCE_LEADER"]')).toHaveAttribute('disabled', '');
  await second
    .getByRole('combobox', { name: '项目职责', exact: true })
    .selectOption('CITY_MANAGER');
  await modal.getByRole('button', { name: '添加负责人', exact: true }).click();
  const third = modal.getByRole('group', { name: '负责人 3', exact: true });
  await third.getByRole('textbox', { name: '姓名', exact: true }).fill('负责人丙');
  await third.getByRole('combobox', { name: '项目职责', exact: true }).selectOption('CITY_MANAGER');
  await page.screenshot({
    path: testInfo.outputPath('people-configuration-modal.png'),
    fullPage: false,
  });
  await modal.getByRole('button', { name: '保存人员配置', exact: true }).click();
  await expect(modal).toHaveCount(0);
  const configured = await get<ProjectDetail>(request, ids.reporter, `/${project.id}`);
  expect(configured.responsiblePersons).toHaveLength(3);
  expect(
    configured.responsiblePersons.filter((person) => person.responsibility === 'CITY_MANAGER'),
  ).toHaveLength(2);
  expect(configured.responsiblePersons[0].userId).toBe(ids.leader);
  expect(configured.versionNo).toBeGreaterThan(initial.versionNo);
  await page.getByRole('button', { name: '配置人员', exact: true }).click();
  await modal
    .getByRole('group', { name: '负责人 1', exact: true })
    .getByRole('textbox', { name: '姓名', exact: true })
    .fill('未保存的名字');
  await modal.getByRole('button', { name: '取消', exact: true }).click();
  expect((await get<ProjectDetail>(request, ids.reporter, `/${project.id}`)).versionNo).toBe(
    configured.versionNo,
  );
});

test('列表详情往返保留已应用条件、第二页及未查询输入草稿', async ({ page, request }) => {
  test.setTimeout(90_000);
  const prefix = `返回-${randomUUID()}`;
  await Promise.all(
    Array.from({ length: 21 }, (_, index) =>
      post(request, ids.reporter, '', {
        provinceId: ids.province,
        cityId: ids.city,
        projectName: `${prefix}-${String(index).padStart(2, '0')}`,
        customerName: '客户',
        industryCode: 'C25',
        customerNeed: '列表返回验收',
        totalAmount: '100',
        responsiblePersonNames: ['返回验收负责人'],
        categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50' }],
        initialProgress: '首次交流',
        nextAction: '核对筛选',
        confirmNew: true,
        duplicateReason: '独立分页验收数据',
      }),
    ),
  );
  await page.goto('/projects');
  await chooseActor(page, ids.reporter);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(prefix);
  await page.getByText('更多筛选条件', { exact: true }).click();
  await page.getByRole('textbox', { name: '项目负责人', exact: true }).fill('返回验收负责人');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page).toHaveURL(/page=1/);
  await expect(
    page.getByRole('table', { name: '项目清单', exact: true }).getByRole('row'),
  ).toHaveCount(2);
  const appliedUrl = page.url();
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(`${prefix}-未查询`);
  await page
    .getByRole('table', { name: '项目清单', exact: true })
    .getByRole('link')
    .first()
    .click();
  await expect(page.getByRole('button', { name: '配置人员', exact: true })).toBeVisible();
  await page.goBack();
  await expect(page).toHaveURL(appliedUrl);
  await expect(page.getByRole('textbox', { name: '项目名称', exact: true })).toHaveValue(
    `${prefix}-未查询`,
  );
  await expect(page.getByRole('textbox', { name: '项目负责人', exact: true })).toHaveValue(
    '返回验收负责人',
  );
  await expect(
    page.getByRole('table', { name: '项目清单', exact: true }).getByRole('row'),
  ).toHaveCount(2);
  await page
    .getByRole('table', { name: '项目清单', exact: true })
    .getByRole('link')
    .first()
    .click();
  await page.getByRole('link', { name: '← 返回项目列表', exact: true }).click();
  await expect(page).toHaveURL(appliedUrl);
  await expect(page.getByRole('textbox', { name: '项目名称', exact: true })).toHaveValue(
    `${prefix}-未查询`,
  );
});
