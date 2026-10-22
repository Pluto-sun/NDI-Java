import { test, expect } from '@playwright/test';
import { chooseActor, ids } from './support/projects';

test('历史月报六指标、阶段组合、终止口径及冻结属地和行业', async ({ page }, testInfo) => {
  await page.clock.setFixedTime(new Date('2026-10-31T12:00:00Z'));
  await page.goto('/workbench');
  await chooseActor(page, ids.leader);
  const from = page.getByLabel('趋势起始月份', { exact: true });
  const to = page.getByLabel('报表／趋势结束月份', { exact: true });
  await expect(to).toHaveValue('2026-09');
  await from.fill('2020-01');
  await to.fill('2020-01');
  const stages = page.getByRole('group', { name: '趋势阶段', exact: true });
  await stages.getByRole('button', { name: '招投标', exact: true }).click();
  const load = page.getByRole('button', { name: '查看月报与趋势', exact: true });
  await load.click();
  const count = (month: string) =>
    page
      .locator('.kpi')
      .filter({ hasText: `${month} 项目数` })
      .locator('strong');
  await expect(count('2020-01')).toHaveText('2');
  const cities = page.getByRole('heading', { name: '当月地市分布', exact: true }).locator('..');
  await expect(cities).toContainText('历史甲市');
  await expect(cities).toContainText('300 万元');
  await expect(cities).not.toContainText('历史乙市');
  const metric = page.getByRole('combobox', { name: '趋势指标', exact: true });
  const metrics = [
    ['projectCount', '项目数量', ['1 个', '1 个']],
    ['totalExpectedAmount', '项目总金额', ['100 万元', '200 万元']],
    ['network', '网络化金额', ['20 万元', '40 万元']],
    ['gewu', '格物金额', ['10 万元', '20 万元']],
    ['bigData', '大数据金额', ['5 万元', '10 万元']],
    ['wangwu', '万悟金额', ['1 万元', '2 万元']],
  ] as const;
  for (const [code, label, values] of metrics) {
    await metric.selectOption(code);
    const table = page.getByRole('table', {
      name: `各月月末项目阶段指标明细：${label}`,
      exact: true,
    });
    await expect(table.getByRole('row', { name: /2020-01/ }).getByRole('cell')).toHaveText([
      ...values,
    ]);
  }
  await to.fill('2020-02');
  await metric.selectOption('totalExpectedAmount');
  await load.click();
  await expect(count('2020-02')).toHaveText('2');
  await expect(cities).toContainText('历史甲市');
  await expect(cities).toContainText('220 万元');
  await expect(cities).toContainText('历史乙市');
  await expect(cities).toContainText('110 万元');
  const industries = page.getByRole('heading', { name: '当月行业分布', exact: true }).locator('..');
  await expect(industries).toContainText('化学原料和化学制品制造业');
  const table = page.getByRole('table', {
    name: '各月月末项目阶段指标明细：项目总金额',
    exact: true,
  });
  await expect(table.getByRole('row', { name: /2020-01/ }).getByRole('cell')).toHaveText([
    '100 万元',
    '200 万元',
  ]);
  await expect(table.getByRole('row', { name: /2020-02/ }).getByRole('cell')).toHaveText([
    '0 万元',
    '110 万元',
  ]);
  const historyPanel = page
    .getByRole('heading', { name: '历史月报与趋势', exact: true })
    .locator('..');
  await historyPanel.getByRole('checkbox', { name: '含已终止项目', exact: true }).check();
  await stages.getByRole('button', { name: '已终止', exact: true }).click();
  await load.click();
  await expect(count('2020-02')).toHaveText('3');
  await expect(table.getByRole('row', { name: /2020-02/ }).getByRole('cell')).toHaveText([
    '0 万元',
    '110 万元',
    '330 万元',
  ]);
  await historyPanel.getByRole('checkbox', { name: '含已终止项目', exact: true }).uncheck();
  await expect(stages.getByRole('button', { name: '已终止', exact: true })).toHaveCount(0);
  await stages.getByRole('button', { name: '方案交流', exact: true }).click();
  await stages.getByRole('button', { name: '招投标', exact: true }).click();
  await load.click();
  await expect(page.getByRole('alert')).toHaveText(
    '请选择至少一个阶段，且起始月份不得晚于报表月份。',
  );
  await expect(table).toHaveCount(0);
  await stages.getByRole('button', { name: '方案交流', exact: true }).click();
  await stages.getByRole('button', { name: '招投标', exact: true }).click();
  await from.fill('2020-02');
  await to.fill('2020-01');
  await load.click();
  await expect(page.getByRole('alert')).toHaveText(
    '请选择至少一个阶段，且起始月份不得晚于报表月份。',
  );
  await from.fill('2020-01');
  await to.fill('2020-02');
  await load.click();
  await expect(count('2020-02')).toHaveText('2');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('monthly-dimensions.png'), fullPage: true });
  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, ids.reporter);
  await page.getByLabel('趋势起始月份', { exact: true }).fill('2020-01');
  await page.getByLabel('报表／趋势结束月份', { exact: true }).fill('2020-01');
  await page.getByRole('button', { name: '查看月报与趋势', exact: true }).click();
  await expect(count('2020-01')).toHaveText('2');
  await page.getByLabel('报表／趋势结束月份', { exact: true }).fill('2020-02');
  await page.getByRole('button', { name: '查看月报与趋势', exact: true }).click();
  await expect(count('2020-02')).toHaveText('1');
});

test('上月跨年按经营时区计算，历史月报加载失败后重试恢复', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2025-12-31T16:30:00Z'));
  let unavailable = true;
  await page.route('**/api/v1/projects/workbench/history?*', async (route) => {
    if (unavailable)
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: '历史月报暂不可用' }),
      });
    else await route.continue();
  });
  await page.goto('/workbench');
  await chooseActor(page, ids.leader);
  await expect(page.getByLabel('报表／趋势结束月份', { exact: true })).toHaveValue('2025-12');
  await page.getByLabel('趋势起始月份', { exact: true }).fill('2020-01');
  await page.getByLabel('报表／趋势结束月份', { exact: true }).fill('2020-01');
  const load = page.getByRole('button', { name: '查看月报与趋势', exact: true });
  await load.click();
  await expect(page.getByRole('alert')).toHaveText('历史月报暂不可用');
  unavailable = false;
  await load.click();
  await expect(
    page.locator('.kpi').filter({ hasText: '2020-01 项目数' }).locator('strong'),
  ).toHaveText('2');
  await expect(page.getByRole('alert')).toHaveCount(0);
});
