import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { test, expect } from '@playwright/test';
import { ids, chooseActor, post } from './support/projects';

test('高级筛选与真实 CSV 导出保持一致，重置后恢复其他项目', async ({ page, request }, testInfo) => {
  const prefix = `筛选-${randomUUID()}`;
  async function create(suffix: string, subject: string) {
    return post(request, ids.reporter, '', {
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
  await page.goto('/projects');
  await chooseActor(page, ids.reporter);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(prefix);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('row')).toHaveCount(3);
  await page.getByText('更多筛选条件', { exact: true }).focus();
  await page.keyboard.press('Enter');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('textbox', { name: '编号或名称关键字', exact: true })).toBeFocused();
  await page.getByRole('checkbox', { name: '含已终止项目', exact: true }).uncheck();
  await page.getByRole('combobox', { name: '签约主体', exact: true }).selectOption('DS_SIGN');
  await page.getByRole('textbox', { name: '省分负责人', exact: true }).fill('省分负责人');
  await page.getByRole('textbox', { name: '地市客户经理', exact: true }).fill('%_');
  await page.getByRole('textbox', { name: '产品单位支撑专家', exact: true }).fill('%_专家');
  await page.getByRole('textbox', { name: '编号或名称关键字', exact: true }).fill(prefix);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('row')).toHaveCount(2);
  await expect(page.getByRole('link', { name: `${prefix}-目标`, exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: `${prefix}-其他`, exact: true })).toHaveCount(0);
  const submitted = page.waitForResponse(
    (response) => response.url().endsWith('/exports') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '按当前条件导出 CSV', exact: true }).click();
  const response = await submitted;
  expect(response.ok(), await response.text()).toBeTruthy();
  expect(response.request().postDataJSON()).not.toHaveProperty('stage');
  expect(response.request().postDataJSON()).not.toHaveProperty('cityId');
  expect(response.request().postDataJSON()).toHaveProperty('includeTerminated', false);
  expect(response.request().postDataJSON()).toMatchObject({
    signingSubjectCode: 'DS_SIGN',
    productSupportExpertName: '%_专家',
    provinceLeaderName: '省分负责人',
    cityAccountManagerName: '%_',
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
  expect(csv.trim().split(/\r?\n/)).toHaveLength(2);
  await page.getByRole('button', { name: '重置筛选', exact: true }).click();
  await expect(page.getByRole('button', { name: '下载 CSV', exact: true })).toHaveCount(0);
  await expect(page.getByRole('textbox', { name: '地市客户经理', exact: true })).toHaveValue('');
  await expect(page.getByRole('combobox', { name: '签约主体', exact: true })).toHaveValue('');
  await expect(page.getByRole('link', { name: `${prefix}-其他`, exact: true })).toBeVisible();
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
    await page.getByRole('textbox', { name: '省分负责人', exact: true }).scrollIntoViewIfNeeded();
    await expect(page.getByRole('textbox', { name: '省分负责人', exact: true })).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBeTruthy();
  }
});
