import { chooseListOptions } from './support/listFilters';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { test, expect } from '@playwright/test';
import { ids, chooseActor, post } from './support/projects';

test('高级筛选与真实 CSV 导出保持一致，重置后恢复其他项目', async ({ page, request }, testInfo) => {
  const prefix = `筛选-${randomUUID()}`;
  async function create(suffix: string, subject: string) {
    return post<{ id: string }>(request, ids.reporter, '', {
      provinceId: ids.province,
      cityId: ids.city,
      projectName: `${prefix}-${suffix}`,
      customerName: '筛选验收客户',
      industryCode: 'C25',
      customerNeed: '高级筛选及导出验收',
      totalAmount: '100',
      provinceLeaderUserId: ids.leader,
      cityAccountManagerName: '客户%_经理',
      categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50' }],
      initialProgress: '开始交流',
      nextAction: '核对预算',
      confirmNew: true,
      duplicateReason: '隔离验收项目',
      signingSubjectCode: subject,
      productSupportExpertNames: ['架构%_专家甲', '架构%_专家乙'],
    });
  }
  await create('目标', 'DS_SIGN');
  await create('其他', 'PROVINCE_SIGN');
  const terminated = await create('已结束', 'DS_SIGN');
  const approval = await post<{ id: string }>(
    request,
    ids.reporter,
    `/${terminated.id}/major-changes`,
    {
      changeType: 'TERMINATE',
      initiatorLevel: 'CITY',
      approverId: ids.leader,
      reason: '验收项目列表终止项目筛选',
    },
  );
  await post(request, ids.leader, `/approvals/${approval.id}/decision`, {
    approved: true,
    opinion: '同意终止',
  });
  await page.goto('/projects?includeTerminated=false');
  await chooseActor(page, ids.reporter);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(prefix);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('row')).toHaveCount(4);
  await expect(page.getByRole('row').filter({ hasText: prefix + '-已结束' })).toContainText(
    '已终止',
  );
  await expect(page.getByRole('checkbox', { name: '含已终止项目', exact: true })).toHaveCount(0);
  await chooseListOptions(page, '当前阶段', ['方案交流']);
  await expect(page.getByRole('row')).toHaveCount(4);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('row')).toHaveCount(3);
  await chooseListOptions(page, '当前阶段', [], true);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('row')).toHaveCount(4);

  const filters = page.getByRole('form', { name: '项目筛选' });
  const queryButton = filters.getByRole('button', { name: '查询' });
  const resetButton = filters.getByRole('button', { name: '重置筛选' });
  const city = filters.getByRole('button', { name: '归属地市／区县' });
  const [box, queryBox, resetBox, cityBox] = await Promise.all(
    [filters, queryButton, resetButton, city].map((element) => element.boundingBox()),
  );
  expect(queryBox!.y).toBeCloseTo(resetBox!.y, 0);
  expect(box!.x + box!.width - resetBox!.x - resetBox!.width).toBeLessThan(21);
  expect(cityBox!.width).toBeLessThanOrEqual(169);
  if (testInfo.project.name !== 'small-screen') {
    const productBox = await filters.getByRole('button', { name: '产品类别' }).boundingBox();
    const actionsShareRow = queryBox!.y < productBox!.y + productBox!.height;
    if (actionsShareRow) {
      expect(queryBox!.x).toBeGreaterThan(productBox!.x + productBox!.width);
      expect(queryBox!.y + queryBox!.height).toBeCloseTo(productBox!.y + productBox!.height, 0);
    } else {
      expect(queryBox!.y).toBeGreaterThan(productBox!.y + productBox!.height);
    }
  } else {
    const provinceBox = await filters.getByRole('button', { name: '归属省分' }).boundingBox();
    expect(provinceBox!.y).toBeCloseTo(cityBox!.y, 0);
    expect(provinceBox!.width).toBeCloseTo(cityBox!.width, 0);
  }

  await page.getByText('更多筛选条件', { exact: true }).focus();
  await page.keyboard.press('Enter');
  await page.keyboard.press('Tab');
  await chooseListOptions(page, '签约主体', ['数科签约']);
  await page.getByRole('textbox', { name: '项目负责人', exact: true }).fill('%_专家');
  await page.getByRole('textbox', { name: '编号或名称关键字', exact: true }).fill(prefix);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('row')).toHaveCount(3);
  await expect(page.getByRole('link', { name: `详情：${prefix}-目标`, exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: `详情：${prefix}-其他`, exact: true })).toHaveCount(
    0,
  );
  const submitted = page.waitForResponse(
    (response) => response.url().endsWith('/exports') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '按当前条件导出 CSV', exact: true }).click();
  await page.getByRole('button', { name: '生成导出文件', exact: true }).click();
  const response = await submitted;
  expect(response.ok(), await response.text()).toBeTruthy();
  expect(response.request().postDataJSON()).not.toHaveProperty('stage');
  expect(response.request().postDataJSON()).not.toHaveProperty('cityId');
  expect(response.request().postDataJSON()).toHaveProperty('includeTerminated', true);
  expect(response.request().postDataJSON()).toMatchObject({
    signingSubjectCodes: ['DS_SIGN'],
    responsiblePersonName: '%_专家',
    keyword: prefix,
  });
  await expect(page.getByText(/^导出任务：COMPLETED/)).toBeVisible({ timeout: 20_000 });
  const downloading = page.waitForEvent('download');
  await page.getByRole('button', { name: '下载 CSV', exact: true }).click();
  const download = await downloading;
  expect(await download.failure()).toBeNull();
  const csv = await readFile((await download.path())!, 'utf8');
  expect(csv).toContain(`${prefix}-目标`);
  expect(csv).not.toContain(`${prefix}-其他`);
  expect(csv).toContain(`${prefix}-已结束`);
  expect(csv.trim().split(/\r?\n/)).toHaveLength(3);
  await page.getByRole('button', { name: '重置筛选', exact: true }).click();
  await expect(page.getByRole('button', { name: '下载 CSV', exact: true })).toHaveCount(0);
  await expect(page.getByRole('textbox', { name: '项目负责人', exact: true })).toHaveValue('');
  await expect(page.getByRole('button', { name: '签约主体', exact: true })).toHaveText('全部');
  await expect(page.getByRole('link', { name: `详情：${prefix}-其他`, exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  // 用实际视口位置核对未聚焦的跳转链接未遮挡筛选输入。
  expect(
    await page
      .getByRole('link', { name: '跳到主内容', exact: true })
      .evaluate((element) => element.getBoundingClientRect().bottom),
  ).toBeLessThanOrEqual(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath('advanced-project-filters.png'),
    fullPage: true,
  });
  if (testInfo.project.name === 'small-screen') {
    await page.setViewportSize({ width: 812, height: 375 });
    await page.getByRole('textbox', { name: '项目负责人', exact: true }).scrollIntoViewIfNeeded();
    await expect(page.getByRole('textbox', { name: '项目负责人', exact: true })).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBeTruthy();
  }
});
