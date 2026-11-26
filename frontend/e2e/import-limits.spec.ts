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

test('2001 和 4200 条以上排产数据均可整批预检、选择和确认', async ({ page, request }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '相同后端规模在桌面验收中执行一次');
  test.setTimeout(180_000);
  const headers = { 'X-NDI-Actor-Id': ids.admin };
  await page.goto('/scheduling/import');
  await page.getByLabel('账号', { exact: true }).selectOption(ids.admin);
  await page.getByRole('button', { name: '进入项目管理', exact: true }).click();
  await page.route('**/production/plans/imports/task', async (route) => {
    if (route.request().method() === 'PUT' && route.request().postDataJSON().step === 3)
      await new Promise((resolve) => setTimeout(resolve, 500));
    await route.continue();
  });
  await page.route('**/production/plans/imports/*/confirm', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 500));
    await route.continue();
  });
  for (const count of [2001, 4200]) {
    const started = Date.now();
    const tag = `排产规模验收-${Date.now()}-`;
    const failed = count === 2001 ? 0 : 1;
    const rows = Array.from({ length: count + failed }, (_, index) => ({
      接口人: '验收接口人',
      排产月份: '2099-11',
      省分: '本地演示省',
      地市: '本地演示市',
      项目名称: tag + index,
      工业专网产品收入排产: index === count ? '0' : '1',
    }));
    const buffer = importWorkbook(rows);
    expect(buffer.length).toBeLessThan(20 * 1024 * 1024);
    await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
      name: '大批次排产.xlsx',
      buffer,
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    await page
      .getByRole('button', { name: '下一步：字段映射', exact: true })
      .click({ timeout: 45_000 });
    await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
    await expect(page.getByRole('dialog', { name: '正在处理' })).toContainText('正在预检 Excel…');
    await expect(
      page.getByText(
        new RegExp(`总计 ${count + failed} 行.*字段通过 ${count} 行.*失败 ${failed} 行`),
      ),
    ).toBeVisible({ timeout: 60_000 });
    if (failed) {
      await expect(page.getByRole('button', { name: '导出预检失败原始数据' })).toBeVisible();
      await page
        .getByRole('group', { name: '预检状态筛选' })
        .getByRole('button', { name: '失败', exact: true })
        .click();
      await expect(page.getByRole('table', { name: '排产导入预检结果' })).toContainText('大于 0');
    }
    await page.getByRole('button', { name: '选择全部通过行', exact: true }).click();
    const importing = page.waitForResponse(
      (response) => /\/production\/plans\/imports\/[^/]+\/confirm$/.test(response.url()),
      { timeout: 90_000 },
    );
    await page.getByRole('button', { name: `确认导入 ${count} 行并结束任务`, exact: true }).click();
    await expect(page.getByRole('dialog', { name: '正在处理' })).toContainText('正在确认导入…');
    const completed = await importing;
    expect(completed.ok(), await completed.text()).toBeTruthy();
    expect((await completed.json()).data).toMatchObject({
      importedRows: count,
      failedRows: failed,
    });
    await expect(page.getByRole('heading', { name: '导入任务已完成' })).toBeVisible({
      timeout: 30_000,
    });
    const listing = await request.get('http://127.0.0.1:18080/api/v1/production/plans', {
      headers,
      params: { search: tag, size: 1 },
    });
    expect(listing.ok()).toBeTruthy();
    expect((await listing.json()).data.totalElements).toBe(count);
    console.info(
      `排产验证 ${count + failed} 行，文件 ${buffer.length} 字节，含界面操作耗时 ${Date.now() - started} ms`,
    );
    await page.getByRole('button', { name: '开始新的导入', exact: true }).click();
  }
});

test('月度 Token 接受 2001 源行和 2004 条展开记录', async ({ request }, info) => {
  test.skip(info.project.name !== 'desktop', '真实入库规模在桌面项目运行一次');
  test.setTimeout(180_000);
  const headers = { 'X-NDI-Actor-Id': ids.admin };
  const root = 'http://127.0.0.1:18080/api/v1/capability/agents-live';
  const agents: string[] = [];
  for (let start = 0; start < 167; start += 8) {
    const block = await Promise.all(
      Array.from({ length: Math.min(167 - start, 8) }, async (_, offset) => {
        const name = `Token 行数验收-${start + offset}`;
        const created = await request.post(root, {
          headers,
          data: {
            provinceId: ids.province,
            fields: {
              省分: '本地演示省',
              接口人: '验收',
              省分智能化接口人: '验收',
              智能体名称: name,
              智能体功能简述: '验收',
              环节: '研发设计',
              应用项目名称: name,
              最终客户名称: '验收客户',
              是否为三方生态迁移: '否',
              是否计收: '未计收',
              是否在计收表中出现: '否',
            },
          },
        });
        expect(created.ok(), await created.text()).toBe(true);
        return (await created.json()).data.id as string;
      }),
    );
    agents.push(...block);
  }
  const monthKeys = Array.from({ length: 12 }, (_, i) => i + 1 + '月Token量（万）');
  const monthly = (agent: string, included: number[]) =>
    Object.fromEntries([
      ['系统ID', agent],
      ...monthKeys.map((key, index) => [key, included.includes(index) ? '1' : '']),
    ]);
  const split = agents
    .flatMap((agent) => monthKeys.map((_, index) => monthly(agent, [index])))
    .slice(0, 2001);
  const wide = agents.map((agent) =>
    monthly(
      agent,
      monthKeys.map((_, index) => index),
    ),
  );
  for (const { rows, count, year } of [
    { rows: split, count: 2001, year: '2099' },
    { rows: wide, count: 2004, year: '2098' },
  ]) {
    const buffer = importWorkbook(rows, {}, undefined, false);
    expect(buffer.length).toBeLessThan(20 * 1024 * 1024);
    const file = {
      name: 'Token规模验收.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer,
    };
    const preview = await request.post(root + '/token-import/preview', {
      headers,
      multipart: { file, year },
      timeout: 90_000,
    });
    expect(preview.ok(), await preview.text()).toBe(true);
    const data = (await preview.json()).data;
    const ready = data.rows.filter((row: { status: string }) => row.status === 'READY');
    expect(ready).toHaveLength(count);
    const selection = {
      name: 'selection.json',
      mimeType: 'application/json',
      buffer: Buffer.from(JSON.stringify(ready.map((row: { key: string }) => row.key))),
    };
    const saved = await request.post(root + '/token-import/confirm', {
      headers,
      multipart: { file, year, sheet: data.sheet, selection },
      timeout: 90_000,
    });
    expect(saved.ok(), await saved.text()).toBe(true);
    expect((await saved.json()).data).toBe(count);
    const replay = await request.post(root + '/token-import/preview', {
      headers,
      multipart: { file, year },
      timeout: 90_000,
    });
    expect(replay.ok(), await replay.text()).toBe(true);
    expect(
      (await replay.json()).data.rows.filter((row: { status: string }) => row.status === 'SKIPPED'),
    ).toHaveLength(count);
  }
});
