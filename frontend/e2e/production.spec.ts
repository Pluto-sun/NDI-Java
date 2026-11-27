import { randomUUID } from 'node:crypto';
import { test, expect, type APIRequestContext } from '@playwright/test';
import { ids, chooseActor } from './support/projects';
import { importWorkbook } from './support/importWorkbook';
const root = 'http://127.0.0.1:18080/api/v1/production/plans';
const group = '77777777-7777-4777-8777-777777777777';
async function call<T>(request: APIRequestContext, path: string, method = 'GET', data?: unknown) {
  const response = await request.fetch(root + path, {
    method,
    headers: { 'X-NDI-Actor-Id': group },
    data,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data as T;
}

test('排产新增、筛选、编辑和删除保持金额零值及空值区分', async ({ page }, testInfo) => {
  const name = `排产界面-${randomUUID()}`;
  await page.goto('/scheduling?month=2026-10');
  await chooseActor(page, group);
  await expect(page.getByRole('heading', { name: '排产计划', exact: true })).toBeVisible();
  await expect(page.locator('.brand-logo-frame img')).toHaveAttribute('src', '/brand-logo.png');
  await page.getByRole('link', { name: '新增排产', exact: true }).click();
  await page.getByRole('link', { name: '返回排产计划', exact: true }).click();
  await expect(page).toHaveURL(/\/scheduling\?month=2026-10$/);
  await page.getByRole('link', { name: '新增排产', exact: true }).click();

  await page.getByRole('combobox', { name: '省份 *', exact: true }).selectOption(ids.province);
  await page.getByRole('combobox', { name: '地市 *', exact: true }).selectOption(ids.city);
  await page.getByLabel('接口人 *', { exact: true }).fill('文本接口人');
  await page.getByLabel('排产月份 *', { exact: true }).fill('2026-10');
  await page.getByLabel('项目名称 *', { exact: true }).fill(name);
  await expect(page.getByLabel('客户名称', { exact: true })).toHaveValue('');
  await expect(page.getByRole('link', { name: '排产导入', exact: true })).toHaveCount(0);
  const amount = (product: string, field: string) =>
    page.getByRole('group', { name: product, exact: true }).getByLabel(field, { exact: true });
  const order = '政企运营平台预计下单金额';
  const revenue = '数智业务管理系统预计计收金额';
  const save = page.getByRole('button', { name: '保存排产', exact: true });
  await save.click();
  await expect(page.getByRole('alert')).toContainText('至少填写一项大于 0 的排产金额');
  for (const invalid of ['-1', '0.00001']) {
    await amount('格物工业互联网平台', revenue).fill(invalid);
    await save.click();
    await expect(page.getByRole('alert')).toContainText('最多四位小数');
  }
  await amount('格物工业互联网平台', revenue).fill('');
  await amount('5G工业专网', order).fill('0');
  await save.click();
  await expect(page.getByRole('alert')).toContainText('至少填写一项大于 0 的排产金额');
  await amount('5G工业专网', order).fill('1E+4');
  await amount('5G工业专网', order).blur();
  await expect(amount('5G工业专网', order)).toHaveValue('10000');
  await amount('5G工业专网', order).fill('0');
  await page.getByLabel('产品简介（选填）', { exact: true }).fill('产品简介验收');
  await page.getByLabel('备注（选填）', { exact: true }).fill('新增备注');
  await amount('格物工业互联网平台', order).fill('20');
  await amount('格物工业互联网平台', revenue).fill('8');
  await expect(page.getByLabel('格物工业互联网平台总排产金额', { exact: true })).toHaveText('28');
  await page.getByRole('button', { name: '保存排产', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: name });
  await expect(row).toBeVisible();
  await expect(row).toContainText('新增备注');
  await expect(row).toContainText('产品简介验收');
  await expect(row.locator('.production-tags span')).toHaveText(['格物工业互联网平台']);
  await expect(row.locator('td.numeric')).toHaveText(['0', '28', '—', '—']);
  await page.getByLabel('项目 / 客户 / 接口人', { exact: true }).fill(name);
  const queried = page.waitForResponse((result) => {
    const url = new URL(result.url());
    return url.pathname === '/api/v1/production/plans' && url.searchParams.get('search') === name;
  });
  await page.getByRole('button', { name: '查询', exact: true }).click();
  expect((await queried).ok()).toBeTruthy();
  const scroll = page.getByRole('region', { name: '排产明细横向滚动区域', exact: true });
  await expect
    .poll(() => scroll.evaluate((element) => element.scrollWidth - element.clientWidth))
    .toBeGreaterThan(250);
  const scrollLeft = await scroll.evaluate((element) => {
    element.scrollLeft = 250;
    return element.scrollLeft;
  });
  expect(scrollLeft).toBeGreaterThan(0);
  await row.getByRole('link', { name: '编辑', exact: true }).click();
  await expect(page).toHaveURL((url) => url.searchParams.get('mode') === 'edit');
  await page.reload();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect(page).not.toHaveURL(/mode=edit/);
  await expect(page.getByRole('heading', { name: '产品金额（万元）' })).toBeVisible();
  await page.getByRole('button', { name: '编辑', exact: true }).click();
  await expect(amount('格物工业互联网平台', order)).toHaveValue('20');
  await expect(amount('格物工业互联网平台', revenue)).toHaveValue('8');
  await page.getByLabel('备注（选填）', { exact: true }).fill('修改备注');
  await amount('格物工业互联网平台', order).fill('');
  await amount('格物工业互联网平台', revenue).fill('123.45');
  await page.getByRole('button', { name: '保存排产', exact: true }).click();
  await expect(page.getByRole('heading', { name: '基本信息', exact: true })).toBeVisible();
  await page.getByRole('link', { name: '返回列表', exact: true }).click();
  await expect(page).toHaveURL((url) => url.searchParams.get('search') === name);
  await expect.poll(() => scroll.evaluate((element) => element.scrollLeft)).toBe(scrollLeft);
  await expect(row.locator('td.numeric')).toHaveText(['0', '123.45', '—', '—']);
  await expect(row).toContainText('修改备注');
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
  const headers = [
    '工业专网产品收入排产',
    '格物工业互联网平台-政企运营平台预计下单金额',
    '格物工业互联网平台数智业务管理系统预计计收金额',
    '工业大数据平台-政企运营平台预计下单金额',
    '工业大数据平台数智业务管理系统预计计收金额',
    '万悟工业智能体平台-政企运营平台预计下单金额',
    '万悟工业智能体平台数智业务管理系统预计计收金额',
  ];
  const amounts = ['0', '20', '8', '3', '4', '1E+4', '6'];
  const line = {
    接口人: '外部文本接口人',
    排产月份: '2026-10',
    省分: ref.provinces.find((p) => p.id === ids.province)!.name,
    地市: ref.cities.find((c) => c.id === ids.city)!.name,
    项目名称: name,
    三化类别: '此旧列应被忽略',
    ...Object.fromEntries(headers.map((header, index) => [header, amounts[index]])),
    项目状态: '进行中',
  };
  await page.goto('/scheduling');
  await chooseActor(page, group);
  await page.getByRole('link', { name: '排产导入', exact: true }).click();
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '排产.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([
      line,
      { ...line },
      { ...line, 项目名称: name + '修正', ...Object.fromEntries(headers.map((h) => [h, ''])) },
    ]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：三化类别', exact: true })).toHaveCount(0);
  const mapping = page.getByRole('combobox', { name: '映射：接口人', exact: true });
  await mapping.selectOption('');
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await mapping.selectOption('接口人');
  for (const [index, header] of headers.entries()) {
    const label = [2, 4, 6].includes(index) ? header.replace('数智', '-数智') : header;
    const field = page.getByRole('combobox', { name: `映射：${label}`, exact: true });
    await expect(field).toHaveValue(header);
    await field.selectOption('');
  }
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await page
    .getByRole('combobox', {
      name: '映射：格物工业互联网平台-数智业务管理系统预计计收金额',
      exact: true,
    })
    .selectOption(headers[2]);
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeEnabled();
  for (const [index, header] of headers.entries()) {
    const label = [2, 4, 6].includes(index) ? header.replace('数智', '-数智') : header;
    await page.getByRole('combobox', { name: `映射：${label}`, exact: true }).selectOption(header);
  }

  await page.getByRole('button', { name: '关闭Excel 排产导入', exact: true }).click();
  await page.getByRole('link', { name: '排产导入', exact: true }).click();
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  await expect(page.getByRole('heading', { name: '第二步：字段映射', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByText(/与文件内前序记录重复/)).toBeVisible();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出预检失败原始数据' }).click();
  expect((await download).suggestedFilename()).toMatch(/预检失败原始数据\.xlsx$/);
  await expect(page.getByText(/至少填写一项大于 0 的排产金额/)).toBeVisible();
  await expect(page.getByText(/10000/).first()).toBeVisible();
  await expect(page.getByText(/1E\+4/)).toHaveCount(0);
  const task = await call<{
    batch: { batchId: string; rows: { rowNumber: number; sourceCellsJson: string }[] };
  }>(request, '/imports/task');
  const invalid = task.batch.rows.find(
    (r) => JSON.parse(r.sourceCellsJson)['项目名称'] === name + '修正',
  )!;
  await page
    .getByRole('button', { name: `查看／修正第 ${invalid.rowNumber} 行`, exact: true })
    .click();
  const city = page.getByRole('combobox', { name: '地市', exact: true });
  const province = page.getByRole('combobox', { name: '省分', exact: true });
  await province.selectOption('');
  await expect(city).toHaveValue('');
  await province.selectOption(line.省分);
  await city.selectOption(line.地市);
  await page
    .getByLabel('格物工业互联网平台-数智业务管理系统预计计收金额', { exact: true })
    .fill('2.34');
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
  await page.goto(`/scheduling?search=${encodeURIComponent(name)}`);
  const rows = page.getByRole('table', { name: '排产计划清单' }).locator('tbody tr');
  await expect(rows.filter({ hasNotText: '修正' }).locator('td.numeric')).toHaveText([
    '0',
    '28',
    '7',
    '10006',
  ]);
  await expect(rows.filter({ hasText: '修正' }).locator('td.numeric')).toHaveText([
    '—',
    '2.34',
    '—',
    '—',
  ]);
  const result = await call<{ items: Record<string, string>[] }>(
    request,
    '?search=' + encodeURIComponent(name),
  );
  const imported = result.items.find((item) => item.projectName === name)!;
  expect(imported).toMatchObject({
    networkOrderAmount: '0.00',
    gewuOrderAmount: '20.00',
    gewuRevenueAmount: '8.00',
    bigDataOrderAmount: '3.00',
    bigDataRevenueAmount: '4.00',
    wangwuOrderAmount: '10000.00',
    wangwuRevenueAmount: '6.00',
    remark: null,
    categories: ['GEWU', 'BIG_DATA', 'WANGWU'],
  });
  const filtered = await call<{ items: unknown[] }>(
    request,
    '?category=NETWORK&search=' + encodeURIComponent(name),
  );
  expect(filtered.items).toHaveLength(0);
  const retry = await request.post(root + `/imports/${task.batch.batchId}/confirm`, {
    headers: { 'X-NDI-Actor-Id': group },
    data: { selectedRowNumbers: [4] },
  });
  expect(retry.status()).toBe(409);
  const withRemark = name + '-含备注';
  await page.getByRole('link', { name: '排产导入', exact: true }).click();
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '含备注排产.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([
      {
        ...line,
        项目名称: withRemark,
        备注: '导入备注',
        产品简介: '导入简介',
        [headers[2]]: '8.0001',
      },
    ]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：备注', exact: true })).toHaveValue('备注');
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const remarks = await call<{ items: { remark: string }[] }>(
    request,
    '?search=' + encodeURIComponent(withRemark),
  );
  expect(remarks.items[0]).toMatchObject({
    remark: '导入备注',
    productIntroduction: '导入简介',
    gewuRevenueAmount: '8.0001',
  });
});

test('管理员可批量删除带两个格物分项的排产记录', async ({ page, request }) => {
  const name = `批量删除分项-${randomUUID()}`;
  for (const suffix of ['甲', '乙']) {
    await call(request, '', 'POST', {
      reviewerName: '排产接口人',
      planMonth: '2026-10',
      provinceId: ids.province,
      cityId: ids.city,
      projectName: name + suffix,
      customerName: '验收客户',
      categories: ['GEWU'],
      gewuOrderAmount: '20',
      gewuRevenueAmount: '8',
    });
  }
  await page.goto(`/scheduling?search=${encodeURIComponent(name)}`);
  await chooseActor(page, ids.admin);
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(2);
  await page.getByRole('checkbox', { name: '选择本页全部记录' }).check();
  await page.getByRole('button', { name: '批量删除（2）' }).click();
  await page
    .getByRole('dialog', { name: '确认批量删除' })
    .getByRole('button', { name: '确认删除' })
    .click();
  await expect(page.getByText('已删除 2 条记录')).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: name })).toHaveCount(0);
});

test('只读账号从排产列表进入详情，直达编辑地址与写请求均不能编辑', async ({ page, request }) => {
  const unique = randomUUID();
  const response = await request.post('http://127.0.0.1:18080/api/v1/admin/users', {
    headers: { 'X-NDI-Actor-Id': ids.admin },
    data: {
      account: {
        loginName: '13911112222',
        email: 'production-readonly@example.test',
        displayName: '排产只读验收',
        roleCodes: ['DS_INTELLIGENCE'],
      },
      password: 'E2eOnly-Readonly123',
    },
  });
  expect(response.ok(), await response.text()).toBe(true);
  const reader = (await response.json()).data.id as string;
  const draft = {
    reviewerName: '排产接口人',
    planMonth: '2026-10',
    provinceId: ids.province,
    cityId: ids.city,
    projectName: '排产只读验收-' + unique,
    gewuOrderAmount: '20',
  };
  const plan = await call<{ id: string; version: number }>(request, '', 'POST', draft);
  await page.goto('/scheduling?search=' + encodeURIComponent(unique));
  await chooseActor(page, reader);
  const row = page.getByRole('row').filter({ hasText: draft.projectName });
  await expect(row.getByRole('link', { name: '编辑', exact: true })).toHaveCount(0);
  await row.getByRole('link', { name: '详情', exact: true }).click();
  await expect(page.getByRole('heading', { name: '基本信息', exact: true })).toBeVisible();
  await page.goto('/scheduling/' + plan.id + '?mode=edit');
  await expect(page).not.toHaveURL(/mode=edit/);
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0);
  const denied = await request.put(root + '/' + plan.id, {
    headers: { 'X-NDI-Actor-Id': reader },
    data: { version: plan.version, plan: draft },
  });
  expect(denied.status()).toBe(403);
});
