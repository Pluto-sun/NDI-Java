import { expect, test } from '@playwright/test';
import { importWorkbook } from './support/importWorkbook';
import { ids } from './support/projects';

test('项目 Excel 的 multipart 上限包含边界工作簿和额外字段', async ({ request }) => {
  const url = 'http://127.0.0.1:18080/api/v1/projects/imports/inspect';
  const max = 20 * 1024 * 1024;
  for (const length of [max, max + 1]) {
    const workbook = importWorkbook([{ 项目名称: '边界验收' }], {}, length);
    expect(workbook.length).toBe(length);
    const response = await request.post(url, {
      headers: { 'X-NDI-Actor-Id': ids.admin },
      multipart: {
        file: {
          name: '边界验收.xlsx',
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: workbook,
        },
        extra: '随工作簿提交的附加字段',
      },
    });
    expect(response.status(), await response.text()).toBe(length === max ? 200 : 413);
    if (length > max) expect(await response.text()).toContain('20 MB');
  }
});

test('计收导入关闭返回列表，管理员可恢复且接口人无导入权限', async ({ page, request }) => {
  const base = 'http://127.0.0.1:18080/api/v1/revenue/entries';
  const reviewer = { 'X-NDI-Actor-Id': '55555555-5555-4555-8555-555555555555' };
  expect((await request.post(base, { headers: reviewer, data: { source: {} } })).status()).toBe(
    403,
  );
  expect((await request.get(base + '/imports/task', { headers: reviewer })).status()).toBe(403);
  await page.goto('/revenue/import');
  await page.getByLabel('账号', { exact: true }).selectOption(ids.admin);
  await page.getByRole('button', { name: '进入项目管理', exact: true }).click();
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '计收返回验收.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([{ 月账期: '2026-11', 三化分类: '数字化' }]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('heading', { name: '第二步：字段映射' })).toBeVisible();
  await page.getByRole('button', { name: '关闭Excel 计收导入', exact: true }).click();
  await expect(page).toHaveURL(/\/revenue$/);
  await page.goto('/revenue/import');
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  await expect(page.getByRole('heading', { name: '第二步：字段映射' })).toBeVisible();
  const headers = { 'X-NDI-Actor-Id': ids.admin };
  const path = base + '/imports/task';
  const active = (await (await request.get(path, { headers })).json()).data;
  expect(
    (
      await request.delete(path, {
        headers,
        params: { expectedTaskId: active.id, expectedVersion: active.version },
      })
    ).ok(),
  ).toBeTruthy();
});

test('项目预检明确拒绝 2001 行，排产可全选并确认 2000 行', async ({ page, request }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '相同后端边界在桌面验收中执行一次');
  test.setTimeout(120_000);
  const headers = { 'X-NDI-Actor-Id': ids.admin };
  const file = (name: string, buffer: Buffer) => ({
    name,
    buffer,
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const overflow = importWorkbook(
    Array.from({ length: 2001 }, (_, index) => ({
      省分: '本地演示省',
      地市: '本地演示市',
      项目名称: `项目边界-${index}`,
      项目负责人: '验收人',
      客户需求: '验收测试',
      项目阶段: '方案交流',
      '项目总金额（万）': '1',
      '5G工业专网预计金额（万）': '1',
    })),
  );
  const endpoint = 'http://127.0.0.1:18080/api/v1/projects/imports/preview';
  const rejected = await request.post(endpoint, {
    headers,
    multipart: { file: file('行数超限.xlsx', overflow) },
  });
  expect(rejected.status(), await rejected.text()).toBe(400);
  expect((await rejected.json()).code).toBe('PROJECT_IMPORT_TOO_MANY_ROWS');

  const tag = Date.now().toString();
  const rows = Array.from({ length: 2000 }, (_, index) => ({
    接口人: '验收接口人',
    排产月份: '2099-11',
    省分: '本地演示省',
    地市: '本地演示市',
    项目名称: `排产边界-${tag}-${index}`,
    工业专网产品收入排产: '1',
  }));
  await page.goto('/scheduling/import');
  await page.getByLabel('账号', { exact: true }).selectOption(ids.admin);
  await page.getByRole('button', { name: '进入项目管理', exact: true }).click();
  await page
    .getByLabel('Excel 文件', { exact: true })
    .setInputFiles(file('整批上限.xlsx', importWorkbook(rows)));
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await page.getByRole('button', { name: '选择全部通过行', exact: true }).click();
  const importing = page.waitForResponse(
    (response) => /\/production\/plans\/imports\/[^/]+\/confirm$/.test(response.url()),
    { timeout: 90_000 },
  );
  await page.getByRole('button', { name: '确认导入 2000 行并结束任务', exact: true }).click();
  const completed = await importing;
  expect(completed.ok(), await completed.text()).toBeTruthy();
  await expect(page.getByRole('heading', { name: '导入任务已完成' })).toBeVisible({
    timeout: 30_000,
  });
});
