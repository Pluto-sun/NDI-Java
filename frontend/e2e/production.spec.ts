import { randomUUID } from 'node:crypto';
import { test, expect, type APIRequestContext } from '@playwright/test';
import { ids, chooseActor } from './support/projects';
import { importWorkbook } from './support/importWorkbook';
const root = 'http://127.0.0.1:18080/api/v1/production/plans';
async function call<T>(request: APIRequestContext, path: string, method = 'GET', data?: unknown) {
  const response = await request.fetch(root + path, {
    method,
    headers: { 'X-NDI-Actor-Id': ids.reporter },
    data,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data as T;
}

test('排产新增、筛选、编辑和删除保持金额零值及空值区分', async ({ page }, testInfo) => {
  const name = `排产界面-${randomUUID()}`;
  await page.goto('/scheduling');
  await chooseActor(page, ids.reporter);
  await expect(page.getByRole('heading', { name: '排产计划', exact: true })).toBeVisible();
  await expect(page.locator('.brand-logo-frame img')).toHaveAttribute('src', '/brand-logo.png');
  await page.getByRole('link', { name: '新增排产', exact: true }).click();
  await page.getByLabel('接口人 *', { exact: true }).fill('文本接口人');
  await page.getByLabel('排产月份 *', { exact: true }).fill('2026-10');
  await page.getByLabel('项目名称 *', { exact: true }).fill(name);
  await page.getByLabel('客户名称 *', { exact: true }).fill('排产客户');
  await page.getByRole('checkbox', { name: '网络化', exact: true }).check();
  await page.getByRole('checkbox', { name: '格物', exact: true }).check();
  await page.getByLabel('工业专网排产（万元）', { exact: true }).fill('0');
  await page.getByRole('button', { name: '保存排产', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: name });
  await expect(row).toBeVisible();
  await expect(row).toContainText('0.00');
  await expect(row).toContainText('—');
  await page.getByLabel('项目 / 客户 / 接口人', { exact: true }).fill(name);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await row.getByRole('button', { name: '编辑', exact: true }).click();
  await page.getByLabel('工业专网排产（万元）', { exact: true }).fill('123.45');
  await page.getByRole('button', { name: '保存排产', exact: true }).click();
  await expect(row).toContainText('123.45');
  await page.screenshot({ path: testInfo.outputPath('production-list.png'), fullPage: true });
  await row.getByRole('button', { name: '删除', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '删除排产', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '确认删除', exact: true }).click();
  await expect(row).toHaveCount(0);
  await expect(page.getByText('排产记录已删除', { exact: true })).toBeVisible();
  await expect(page.getByText('排产记录已删除', { exact: true })).toHaveCount(0, { timeout: 7000 });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});

test('排产导入串行步骤、必填映射、暂存恢复及一次性选中导入', async ({
  page,
  request,
}, testInfo) => {
  const ref = await call<{
    provinces: { id: string; name: string }[];
    cities: { id: string; name: string }[];
  }>(request, '/reference-data');
  const name = `排产导入-${randomUUID()}`;
  const line = {
    接口人: '外部文本接口人',
    排产月份: '2026-10',
    省分: ref.provinces.find((p) => p.id === ids.province)!.name,
    地市: ref.cities.find((c) => c.id === ids.city)!.name,
    项目名称: name,
    客户名称: '导入客户',
    三化类别: '网络化',
    '工业专网排产（万元）': '0',
    '格物平台排产（万元）': '',
    '工业大数据排产（万元）': '',
    '万悟平台排产（万元）': '',
    项目状态: '进行中',
  };
  await page.goto('/scheduling/new');
  await chooseActor(page, ids.reporter);
  await page.getByRole('link', { name: '排产导入', exact: true }).click();
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '排产.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([
      line,
      { ...line },
      { ...line, 项目名称: name + '修正', '工业专网排产（万元）': '' },
    ]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  const mapping = page.getByRole('combobox', { name: '映射：接口人', exact: true });
  await mapping.selectOption('');
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await mapping.selectOption('接口人');
  await page.getByRole('button', { name: '关闭Excel 排产导入', exact: true }).click();
  await page.getByRole('link', { name: '排产导入', exact: true }).click();
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  await expect(page.getByRole('heading', { name: '第二步：字段映射', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByText(/与文件内前序记录重复/)).toBeVisible();
  const task = await call<{
    batch: { batchId: string; rows: { rowNumber: number; sourceCellsJson: string }[] };
  }>(request, '/imports/task');
  const invalid = task.batch.rows.find(
    (r) => JSON.parse(r.sourceCellsJson)['项目名称'] === name + '修正',
  )!;
  await page
    .getByRole('button', { name: `查看／修正第 ${invalid.rowNumber} 行`, exact: true })
    .click();
  await page.getByLabel('工业专网排产（万元）', { exact: true }).fill('2.34');
  await page.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await expect(
    page.getByRole('dialog', { name: `修正第 ${invalid.rowNumber} 行`, exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole('checkbox', { name: `选择第 ${invalid.rowNumber} 行`, exact: true }),
  ).toBeChecked();
  await page.screenshot({ path: testInfo.outputPath('production-import.png'), fullPage: false });
  await page.getByRole('button', { name: '确认导入 2 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  expect(await call(request, '/imports/task')).toBeNull();
  const retry = await request.post(root + `/imports/${task.batch.batchId}/confirm`, {
    headers: { 'X-NDI-Actor-Id': ids.reporter },
    data: { selectedRowNumbers: [4] },
  });
  expect(retry.status()).toBe(409);
});
