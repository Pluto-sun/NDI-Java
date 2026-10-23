import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { test, expect } from '@playwright/test';
import { ids, chooseActor, post, get } from './support/projects';

test('导出字段按选择顺序持久化，真实 CSV 仅包含所选列并防护公式文本', async ({
  page,
  request,
}, testInfo) => {
  const name = `字段-${randomUUID()}`;
  await post(request, ids.reporter, '', {
    provinceId: ids.province,
    cityId: ids.city,
    projectName: name,
    customerName: '字段验收客户',
    industryCode: 'C25',
    customerNeed: '验证导出字段选择',
    totalAmount: '100',
    provinceLeaderUserId: ids.leader,
    cityAccountManagerName: '=经理',
    signingSubjectCode: 'DS_SIGN',
    productSupportExpertNames: ['@专家乙', '支撑专家甲'],
    categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50' }],
    initialProgress: '开始交流',
    nextAction: '核对字段',
    confirmNew: true,
    duplicateReason: '隔离字段验收项目',
  });
  await page.goto('/projects');
  await chooseActor(page, ids.reporter);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(name);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('link', { name, exact: true })).toBeVisible();
  await expect(page.locator('.export-field-picker')).toHaveCount(0);
  await page.getByRole('button', { name: '按当前条件导出 CSV', exact: true }).click();
  const group = page.getByRole('group', { name: '导出字段选择', exact: true });
  await expect(group.getByRole('checkbox', { checked: true })).toHaveCount(11);
  await page.getByRole('button', { name: '清空导出字段', exact: true }).click();
  await expect(page.getByText('请至少选择一个导出字段。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '生成导出文件', exact: true })).toBeDisabled();
  const labels = [
    '地市客户经理',
    '项目名称',
    '产品单位支撑专家',
    '省分',
    '地市',
    '项目阶段',
    '签约主体',
    '省分负责人',
    '客户所属行业',
    '三化类别',
    '最近更新时间',
  ];
  const fields = [
    'cityAccountManagerName',
    'projectName',
    'productSupportExpertNames',
    'province',
    'city',
    'currentStageName',
    'signingSubjectCode',
    'provinceLeaderName',
    'customerIndustry',
    'categories',
    'updatedAt',
  ];
  for (const label of labels)
    await group.getByRole('checkbox', { name: label, exact: true }).check();
  const submitted = page.waitForResponse(
    (response) => response.url().endsWith('/exports') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '生成导出文件', exact: true }).click();
  const response = await submitted;
  expect(response.ok(), await response.text()).toBeTruthy();
  const task = (await response.json()).data;
  expect(task.fields).toEqual(fields);
  expect(response.request().postDataJSON().fields).toEqual(fields);
  await expect(page.getByText(/^导出任务：COMPLETED/)).toBeVisible({ timeout: 20_000 });
  const persisted = await get<{ fields: string[] }>(request, ids.reporter, `/exports/${task.id}`);
  expect(persisted.fields).toEqual(fields);
  const downloading = page.waitForEvent('download');
  await page.getByRole('button', { name: '下载 CSV', exact: true }).click();
  const download = await downloading;
  expect(await download.failure()).toBeNull();
  const csv = await readFile((await download.path())!, 'utf8');
  expect(csv).toContain(labels.join(',') + '\r\n');
  expect(csv).toContain('"\'=经理"');
  expect(csv).toContain('"\'@专家乙、支撑专家甲"');
  expect(csv).toContain('DS_SIGN');
  expect(csv).toContain('方案交流');
  expect(csv).toContain('网络化');
  expect(csv).not.toContain('字段验收客户');
  expect(csv.trim().split(/\r?\n/)).toHaveLength(2);
  await page.getByRole('button', { name: '按当前条件导出 CSV', exact: true }).click();
  await expect(group.getByRole('checkbox', { name: '地市客户经理', exact: true })).toBeChecked();
  await page.getByRole('button', { name: '恢复默认导出字段', exact: true }).click();
  await expect(group.getByRole('checkbox', { checked: true })).toHaveCount(11);
  await expect(page.getByRole('button', { name: '下载 CSV', exact: true })).toHaveCount(0);
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath('selected-export-fields.png'),
    fullPage: true,
  });
});

test('导出字段加载失败时展开错误并可重试，加载前不允许导出', async ({ page }) => {
  let unavailable = true;
  await page.route('**/api/v1/projects/exports/fields', async (route) => {
    if (unavailable) {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: '导出字段暂不可用' }),
      });
    } else await route.continue();
  });
  await page.goto('/projects');
  await chooseActor(page, ids.reporter);
  await page.getByRole('button', { name: '按当前条件导出 CSV', exact: true }).click();
  const retry = page.getByRole('button', { name: '重试加载导出字段', exact: true });
  await expect(retry).toBeVisible();
  await expect(page.getByRole('button', { name: '生成导出文件', exact: true })).toBeDisabled();
  unavailable = false;
  await retry.click();
  await expect(page.getByRole('button', { name: '生成导出文件', exact: true })).toBeEnabled();
  await expect(page.getByRole('alert')).toHaveCount(0);
});
