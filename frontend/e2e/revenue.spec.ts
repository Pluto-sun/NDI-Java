import { requiredRevenueFields } from './support/revenue';
import { randomUUID } from 'node:crypto';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { ids, chooseActor } from './support/projects';
import { importWorkbook } from './support/importWorkbook';
import type { Entry, Approval, Reference } from '../src/features/revenue/api';
const root = 'http://127.0.0.1:18080/api/v1/revenue/entries';
const reviewer = '55555555-5555-4555-8555-555555555555';
const group = '77777777-7777-4777-8777-777777777777';
async function api<T>(
  request: APIRequestContext,
  actor: string,
  path: string,
  data?: unknown,
): Promise<T> {
  const response =
    data === undefined
      ? await request.get(root + path, { headers: { 'X-NDI-Actor-Id': actor } })
      : await request.post(root + path, { headers: { 'X-NDI-Actor-Id': actor }, data });
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).data as T;
}
async function switchActor(page: Page, actor: string) {
  const switchButton = page.getByRole('button', { name: '切换测试账号', exact: true });
  if (await switchButton.isVisible()) await switchButton.click();
  await chooseActor(page, actor);
}
const source = (name: string) => ({
  ...requiredRevenueFields,
  月账期: '202610',
  费用账期: '202609',
  省份名称: '本地演示省',
  '地市/组织名称': '本地演示市',
  合同编号: name,
  条款编号: '0001',
  三化分类: '数字化',
  条款金额: '100.00',
  '产品本月计收不含税额/标品含税收入': '10.00',
  项目名称: '来源同名项目',
  客户名称: '来源客户',
  自然客户名称: '来源自然客户',
  '业务号码/项目编号': '000009',
});

test('计收列表创建、认定审核、修改撤销和生效历史', async ({ page, request }, testInfo) => {
  const contract = 'REVENUE-' + randomUUID();
  await page.goto('/revenue');
  await switchActor(page, ids.admin);
  await expect(page.getByRole('heading', { name: '计收列表', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '计收台账', exact: true })).toHaveCount(0);
  await page.getByRole('link', { name: '新增计收', exact: true }).click();
  const reference = await api<Reference>(request, ids.admin, '/reference-data');
  for (const [field, value] of Object.entries(source(contract))) {
    if (field === '省份名称')
      await page.getByRole('combobox', { name: '省份名称 *', exact: true }).selectOption(value);
    else if (field === '三化分类')
      await page.getByRole('combobox', { name: '三化分类 *', exact: true }).selectOption(value);
    else if (field === '地市/组织名称')
      await page
        .getByRole('combobox', { name: '地市/组织名称 *', exact: true })
        .selectOption(value);
    else
      await page
        .getByLabel(field + (reference.required.includes(field) ? ' *' : '（选填）'), {
          exact: true,
        })
        .fill(field === '月账期' ? '2026-10' : field === '费用账期' ? '2026-09' : value);
  }
  await page.getByRole('button', { name: '创建计收明细', exact: true }).click();
  await expect(page).toHaveURL(/\/revenue\/[0-9a-f-]+$/);
  const id = page.url().split('/').pop()!;
  await expect(page.getByRole('button', { name: '首次认定', exact: true })).toHaveCount(0);
  await switchActor(page, ids.reporter);
  await page.goto('/revenue/' + id);
  await expect(page.getByRole('heading', { name: '当前生效认定', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '首次认定', exact: true })).toHaveCount(0);
  await switchActor(page, reviewer);
  await page.goto('/revenue/' + id);
  await page.getByRole('button', { name: '首次认定', exact: true }).click();
  const recognition = page.getByRole('dialog', { name: '首次认定', exact: true });
  await recognition.getByLabel('认定金额（元）', { exact: true }).fill('180');
  await recognition.getByLabel('通报项目名称', { exact: true }).fill('正式通报名称');
  await recognition.getByRole('combobox', { name: '项目状态', exact: true }).selectOption('已上线');
  await recognition
    .getByRole('combobox', { name: '对应省分（集成）', exact: true })
    .selectOption('数科');
  await recognition.getByRole('combobox', { name: '集团审核人' }).selectOption(group);
  await recognition.getByRole('button', { name: '提交审核', exact: true }).click();
  await expect(recognition).toHaveCount(0);
  await expect(
    page.getByRole('heading', { name: '当前生效认定', exact: true }).locator('..'),
  ).toContainText('尚无生效认定');
  await switchActor(page, group);
  await page.goto('/approvals?business=revenue');
  await expect(page.getByRole('heading', { name: '计收审核待办', exact: true })).toBeVisible();
  const item = page
    .locator('.message-item')
    .filter({ has: page.locator(`a[href="/revenue/${id}"]`) });
  await expect(item.getByRole('link')).toHaveCount(1);
  await page.screenshot({ path: testInfo.outputPath('revenue-inbox.png'), fullPage: true });
  await item.getByRole('link', { name: '查看并处理' }).click();
  await expect(page).toHaveURL('/revenue/' + id);
  await page.getByRole('button', { name: '通过', exact: true }).click();
  const approval = page.getByRole('dialog', { name: '通过计收认定申请', exact: true });
  await approval.getByRole('button', { name: '确认', exact: true }).click();
  await expect(approval).toHaveCount(0);
  const current = page.getByRole('heading', { name: '当前生效认定', exact: true }).locator('..');
  await expect(current).toContainText('180.00');
  await expect(current).toContainText('已上线');
  await expect(current).toContainText('数科');
  await switchActor(page, reviewer);
  await page.goto('/revenue/' + id);
  await page.getByRole('button', { name: '修改认定', exact: true }).click();
  const edit = page.getByRole('dialog', { name: '修改认定', exact: true });
  await edit.getByLabel('认定金额（元）', { exact: true }).fill('90');
  await edit.getByRole('combobox', { name: '集团审核人' }).selectOption(group);
  await edit.getByRole('button', { name: '提交审核', exact: true }).click();
  await expect(edit).toHaveCount(0);
  await expect(current).toContainText('180.00');
  await page.getByRole('button', { name: '撤销申请', exact: true }).click();
  const withdraw = page.getByRole('dialog', { name: '撤销计收认定申请', exact: true });
  await withdraw.getByRole('button', { name: '确认', exact: true }).click();
  await expect(withdraw).toHaveCount(0);
  await expect(current).toContainText('180.00');
  const detail = await api<Entry>(request, ids.admin, '/' + id);
  expect(Number(detail.recognition!.amount)).toBe(180);
  await page.screenshot({
    path: testInfo.outputPath('revenue-recognition-detail.png'),
    fullPage: true,
  });
  await page.goto('/revenue?search=' + contract);
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await page.getByRole('link', { name: '详情', exact: true }).click();
  await page.getByRole('link', { name: '返回计收列表', exact: true }).click();
  await expect(page).toHaveURL(new RegExp('search=' + contract));
  await expect(page.locator('tbody tr')).toHaveCount(1);
});

test('集团三步导入33字段、修正重复条款、只导入一次；接口人无导入权限', async ({
  page,
  request,
}, testInfo) => {
  const contract = 'IMPORT-' + randomUUID();
  const reference = await api<Reference>(request, ids.admin, '/reference-data');
  expect(reference.fields).toHaveLength(33);
  const first = Object.fromEntries(
    reference.fields.map((f) => [f, source(contract)[f as keyof ReturnType<typeof source>] ?? '']),
  );
  const second = { ...first, 条款金额: '' };
  await page.goto('/revenue');
  await switchActor(page, group);
  await page.getByRole('link', { name: '计收导入', exact: true }).click();
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '计收.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([first, first, second]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：认定金额', exact: true })).toHaveValue('');
  const amountMapping = page.getByRole('combobox', { name: '映射：条款金额', exact: true });
  await amountMapping.selectOption('');
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await amountMapping.selectOption('条款金额');
  const categoryMapping = page.getByRole('combobox', { name: '映射：三化分类', exact: true });
  await categoryMapping.selectOption('');
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await categoryMapping.selectOption('三化分类');

  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByText('条款金额必填', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '查看／修正第 5 行', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '修正第 5 行', exact: true });
  await dialog.getByLabel('条款金额', { exact: true }).fill('100');
  await dialog.getByLabel('条款编号', { exact: true }).fill('0002');
  await dialog.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath('revenue-import-preview.png'),
    fullPage: false,
  });
  await page.getByRole('button', { name: '确认导入 2 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  await page.goto('/revenue?search=' + contract);
  await expect(page.locator('tbody tr')).toHaveCount(2);
  await switchActor(page, reviewer);
  await page.goto('/revenue');
  await expect(page.getByRole('link', { name: '计收导入', exact: true })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '新增计收', exact: true })).toHaveCount(0);
  const denied = await request.get(root + '/imports/task', {
    headers: { 'X-NDI-Actor-Id': reviewer },
  });
  expect(denied.status()).toBe(403);
});

test('具体组织由集团审核，省分不能因集成省分而取得权限', async ({ page, request }, testInfo) => {
  const row = {
    ...source('ORG-' + randomUUID()),
    省份名称: '具体组织',
    '地市/组织名称': '测试组织全称',
  };
  const entry = await api<Entry>(request, ids.admin, '', { source: row });
  await page.goto('/revenue');
  await switchActor(page, reviewer);
  await page.goto('/revenue/' + entry.id);
  await page.getByRole('button', { name: '首次认定', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '首次认定', exact: true });
  await dialog.getByRole('combobox', { name: '集团审核人' }).selectOption(group);
  await dialog.getByLabel('认定金额（元）', { exact: true }).fill('0');
  await dialog.getByLabel('通报项目名称', { exact: true }).fill('组织通报');
  await dialog
    .getByRole('combobox', { name: '对应省分（集成）', exact: true })
    .selectOption(ids.province);
  await dialog.getByRole('button', { name: '提交审核', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('认定金额须大于零');
  await dialog.getByLabel('认定金额（元）', { exact: true }).fill('101');
  await dialog.getByRole('button', { name: '提交审核', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const pending = await api<Entry>(request, reviewer, '/' + entry.id);
  expect(pending.recognition).toBeNull();
  const [approval] = await api<Approval[]>(request, reviewer, `/${entry.id}/requests`);
  const saved = await api<Entry>(request, group, `/requests/${approval.id}/decision`, {
    entryVersion: pending.version,
    requestVersion: approval.version,
    decision: 'APPROVED',
  });
  expect(Number(saved.recognition!.amount)).toBe(101);
  const denied = await request.get(root + '/' + entry.id, {
    headers: { 'X-NDI-Actor-Id': ids.reporter },
  });
  expect(denied.status()).toBe(403);
  await switchActor(page, ids.reporter);
  await page.goto('/notifications?category=revenue');
  await expect(page.getByRole('heading', { name: '计收通知', exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('revenue-notifications.png'), fullPage: true });
});

test('创建和导入统一按七字段查重，费用账期不同可共存', async ({ page, request }, testInfo) => {
  const contract = 'SEVEN-KEY-' + randomUUID();
  const first = source(contract);
  await api<Entry>(request, ids.admin, '', { source: first });
  await page.goto('/revenue/new');
  await switchActor(page, ids.admin);
  const reference = await api<Reference>(request, ids.admin, '/reference-data');
  for (const [field, value] of Object.entries({ ...first, 费用账期: '2026-08' })) {
    if (field === '省份名称')
      await page.getByRole('combobox', { name: '省份名称 *', exact: true }).selectOption(value);
    else if (field === '三化分类')
      await page.getByRole('combobox', { name: '三化分类 *', exact: true }).selectOption(value);
    else if (field === '地市/组织名称')
      await page
        .getByRole('combobox', { name: '地市/组织名称 *', exact: true })
        .selectOption(value);
    else
      await page
        .getByLabel(field + (reference.required.includes(field) ? ' *' : '（选填）'), {
          exact: true,
        })
        .fill(field === '月账期' ? '2026-10' : value);
  }
  await page.getByRole('button', { name: '创建计收明细', exact: true }).click();
  await expect(page).toHaveURL(/\/revenue\/[0-9a-f-]+$/);
  const repeated = await request.post(root, {
    headers: { 'X-NDI-Actor-Id': ids.admin },
    data: { source: { ...first, 费用账期: '2026-08' } },
  });
  expect(repeated.status()).toBe(409);
  await page.goto('/revenue/import');
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '七字段.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([
      { ...first, 产品编码: '' },
      { ...first, '业务号码/项目编号': '000011' },
      { ...first, 自然客户名称: '新增自然客户' },
      { ...first, 产品编码: '000012' },
      { ...first, 费用账期: '202607' },
      { ...first, 费用账期: '2026-07' },
      { ...first, '业务号码/项目编号': '000011' },
    ]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByText('系统内已存在相同业务唯一键的记录', { exact: true })).toBeVisible();
  await expect(
    page.getByText('与文件内前序记录重复：业务唯一键相同', { exact: true }).first(),
  ).toBeVisible();
  await page.getByRole('button', { name: '查看／修正第 3 行', exact: true }).click();
  const locationDialog = page.getByRole('dialog', { name: '修正第 3 行', exact: true });
  const province = locationDialog.getByRole('combobox', { name: '省份名称', exact: true });
  const city = locationDialog.getByRole('combobox', { name: '地市/组织名称', exact: true });
  await province.selectOption('');
  await expect(city).toHaveValue('');
  await province.selectOption('本地演示省');
  await city.selectOption('本地演示市');
  await page.keyboard.press('Escape');
  await expect(locationDialog).toHaveCount(0);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出预检失败原始数据' }).click();
  expect((await download).suggestedFilename()).toMatch(/预检失败原始数据\.xlsx$/);
  await page.screenshot({ path: testInfo.outputPath('seven-key-preview.png'), fullPage: true });
  await page.getByRole('button', { name: '确认导入 4 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  await page.goto('/revenue?search=' + contract);
  await expect(page.locator('tbody tr')).toHaveCount(6);
});

test('数科固定组织、无审签日期与导入修正使用一致选项', async ({ page, request }, testInfo) => {
  const contract = 'DS-OPTIONS-' + randomUUID();
  await page.goto('/revenue/new');
  await switchActor(page, ids.admin);
  await page.getByRole('combobox', { name: '省份名称 *', exact: true }).selectOption('数科');
  const organization = page.getByRole('combobox', { name: '地市/组织名称 *', exact: true });
  await expect(organization).toHaveValue('联通数字科技有限公司本部');
  await expect(organization.locator('option')).toHaveCount(1);
  await page.getByLabel('月账期 *', { exact: true }).fill('2026-11');
  await page.getByLabel('合同编号 *', { exact: true }).fill(contract);
  await page.getByLabel('费用账期 *', { exact: true }).fill('2026-10');
  await page.getByLabel('业务号码/项目编号 *', { exact: true }).fill('000042');
  await page.getByLabel('产品名称 *', { exact: true }).fill('数科产品');
  await page.getByLabel('条款编号（选填）', { exact: true }).fill('001');
  await page.getByLabel('自然客户名称（选填）', { exact: true }).fill('数科自然客户');
  await page.getByRole('combobox', { name: '三化分类 *', exact: true }).selectOption('数字化');
  await page.getByLabel('条款金额 *', { exact: true }).fill('100');
  await page.getByLabel('产品本月计收不含税额/标品含税收入 *', { exact: true }).fill('10');
  await page.getByText('其他来源字段（选填）', { exact: true }).click();
  await page.getByLabel('审签日期（选填）', { exact: true }).fill('--');
  await page.getByRole('button', { name: '创建计收明细', exact: true }).click();
  await expect(page).toHaveURL(/\/revenue\/[0-9a-f-]+$/);
  const entry = await api<Entry>(request, ids.admin, '/' + page.url().split('/').pop()!);
  expect(entry.source).toMatchObject({
    省份名称: '数科',
    '地市/组织名称': '联通数字科技有限公司本部',
    审签日期: '',
  });
  await page.goto('/revenue?province=DS_TECH&search=' + contract);
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await page.goto('/revenue?province=ORGANIZATION&search=' + contract);
  await expect(page.locator('tbody tr')).toHaveCount(0);
  await page.goto('/revenue/import');
  const record = {
    ...requiredRevenueFields,
    月账期: '202611',
    省份名称: '数科',
    '地市/组织名称': '其他组织',
    合同编号: contract + '-IMPORT',
    自然客户名称: '数科自然客户',
    条款编号: '001',
    三化分类: '数字化',
    条款金额: '100',
    '产品本月计收不含税额/标品含税收入': '10',
    审签日期: '--',
  };
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '数科.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([record]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await page
    .getByRole('group', { name: '预检状态筛选', exact: true })
    .getByRole('button', { name: '失败', exact: true })
    .click();
  await page.getByRole('button', { name: '查看／修正第 3 行', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '修正第 3 行', exact: true });
  await dialog
    .getByRole('combobox', { name: '地市/组织名称', exact: true })
    .selectOption('联通数字科技有限公司本部');
  await dialog.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByText('暂无符合筛选条件的数据', { exact: true })).toBeVisible();
  await page
    .getByRole('group', { name: '预检状态筛选', exact: true })
    .getByRole('button', { name: '预检通过', exact: true })
    .click();
  await page.screenshot({ path: testInfo.outputPath('digital-tech-options.png'), fullPage: true });
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
});

test('数字条款归一后四字段查重，创建与导入支持常见审签日期时间', async ({
  page,
  request,
}, testInfo) => {
  const contract = 'NORMALIZE-' + randomUUID();
  const first = { ...source(contract), 条款编号: '002', 审签日期: '2026-07-20 00:00:00' };
  await page.goto('/revenue/new');
  await switchActor(page, ids.admin);
  const reference = await api<Reference>(request, ids.admin, '/reference-data');
  await page.getByText('其他来源字段（选填）', { exact: true }).click();
  for (const [field, value] of Object.entries(first)) {
    if (field === '省份名称')
      await page.getByRole('combobox', { name: '省份名称 *', exact: true }).selectOption(value);
    else if (field === '三化分类')
      await page.getByRole('combobox', { name: '三化分类 *', exact: true }).selectOption(value);
    else if (field === '地市/组织名称')
      await page
        .getByRole('combobox', { name: '地市/组织名称 *', exact: true })
        .selectOption(value);
    else
      await page
        .getByLabel(field + (reference.required.includes(field) ? ' *' : '（选填）'), {
          exact: true,
        })
        .fill(field === '月账期' ? '2026-10' : field === '费用账期' ? '2026-09' : value);
  }
  await page.getByRole('button', { name: '创建计收明细', exact: true }).click();
  await expect(page).toHaveURL(/\/revenue\/[0-9a-f-]+$/);
  const saved = await api<Entry>(request, ids.admin, '/' + page.url().split('/').pop()!);
  expect(saved.clauseNo).toBe('2');
  expect(saved.source).toMatchObject({
    条款编号: '2',
    审签日期: '2026-07-20',
    合同编号: contract,
    '业务号码/项目编号': '000009',
  });
  const duplicate = await request.post(root, {
    headers: { 'X-NDI-Actor-Id': ids.admin },
    data: { source: { ...first, 条款编号: '2' } },
  });
  expect(duplicate.status()).toBe(409);
  const noClause = await api<Entry>(request, ids.admin, '', {
    source: { ...first, 条款编号: '--', 审签日期: '--' },
  });
  expect(noClause.clauseNo).toBe('');
  expect(noClause.source['审签日期']).toBe('');
  await page.goto('/revenue/import');
  const imported = { ...first, 合同编号: contract + '-IMPORT', 审签日期: '2026/7/20 0:00:00' };
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '条款与日期.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([
      imported,
      { ...imported, 条款编号: '2' },
      { ...first, 条款编号: '0002' },
    ]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(
    page.getByText('与文件内前序记录重复：业务唯一键相同', { exact: true }),
  ).toBeVisible();
  await expect(page.getByText('系统内已存在相同业务唯一键的记录', { exact: true })).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath('clause-date-normalization.png'),
    fullPage: true,
  });
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const list = await api<{ items: Entry[] }>(request, ids.admin, '?search=' + contract + '-IMPORT');
  expect(list.items).toHaveLength(1);
  expect(list.items[0].clauseNo).toBe('2');
  expect(list.items[0].source['审签日期']).toBe('2026-07-20');
});

test('六个来源字段在新增、映射和预检中必填，可修正后导入', async ({ page, request }) => {
  const contract = 'REQUIRED-' + randomUUID();
  await page.goto('/revenue/new');
  await switchActor(page, ids.admin);
  for (const field of [
    '费用账期',
    '省份名称',
    '地市/组织名称',
    '业务号码/项目编号',
    '合同编号',
    '产品名称',
  ]) {
    await expect(
      page
        .locator('label')
        .filter({ hasText: new RegExp('^' + field + ' \\*') })
        .locator('input, select'),
    ).toHaveAttribute('required', '');
    const invalid = await request.post(root, {
      headers: { 'X-NDI-Actor-Id': ids.admin },
      data: { source: { ...source(contract), [field]: '   ' } },
    });
    expect(invalid.status()).toBe(400);
    expect(await invalid.text()).toContain(field + '必填');
  }
  await page.goto('/revenue/import');
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '六个来源字段.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([{ ...source(contract), 产品名称: '--' }]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  const field = page.getByRole('combobox', { name: '映射：产品名称', exact: true });
  await field.selectOption('');
  const preview = page.getByRole('button', { name: '下一步：开始预检', exact: true });
  await expect(preview).toBeDisabled();
  await field.selectOption('产品名称');
  await preview.click();
  await expect(page.getByText('产品名称必填', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '查看／修正第 3 行', exact: true }).click();
  const editor = page.getByRole('dialog', { name: '修正第 3 行', exact: true });
  await editor.getByRole('textbox', { name: '产品名称', exact: true }).fill('修正产品');
  await editor.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const listing = await api<{ items: Entry[] }>(request, ids.admin, '?search=' + contract);
  expect(listing.items[0].source).toMatchObject({ 产品名称: '修正产品', 费用账期: '2026-09' });
});

test('计收工作簿有同名项目列时自动识别表头并可分别映射', async ({ page, request }) => {
  const contract = 'DUP-HEADER-' + randomUUID();
  await page.goto('/revenue/import');
  await switchActor(page, ids.admin);
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '同名表头.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook(
      [
        {
          ...source(contract),
          省份名称: '广东',
          '地市/组织名称': '广州',
          认定金额: '50',
          项目状态: '已上线',
          第二项目名称: '通报验收项目',
          三化分类: '网络化',
          '对应省分（集成）': '数智',
        },
      ],
      { 第二项目名称: '项目名称' },
    ),
  });
  await expect(page.getByRole('combobox', { name: '工作表', exact: true })).toHaveValue(
    '验收工作表',
  );
  await expect(page.getByRole('combobox', { name: '表头行', exact: true })).toHaveValue('2');
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：项目名称', exact: true })).toHaveValue(
    /^项目名称（.+列）$/,
  );
  await expect(page.getByRole('combobox', { name: '映射：项目状态', exact: true })).toHaveValue(
    '项目状态',
  );
  const reported = page.getByRole('combobox', { name: '映射：通报项目名称', exact: true });
  await expect(reported).toHaveValue('');
  await reported.selectOption({
    label: (await reported.locator('option').allTextContents())
      .filter((value) => /^项目名称（.+列）$/.test(value))
      .at(-1)!,
  });
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const result = await api<{ items: Entry[] }>(request, ids.admin, '?search=' + contract);
  expect(result.items[0].source).toMatchObject({
    项目名称: '来源同名项目',
    省份名称: '广东省',
    '地市/组织名称': '广州市',
  });
  expect(result.items[0].recognition?.projectStatus).toBe('已上线');
  expect(result.items[0].recognition?.integratedOrganization).toBe('数智');
  expect(result.items[0].recognition?.reportedProjectName).toBe('通报验收项目');
});

test('集团导入数字化和智能化时四项认定信息直接生效', async ({ page, request }) => {
  const prefix = 'IMPORT-RECOGNIZED-' + randomUUID();
  const rows = ['数字化', '智能化'].map((category) => ({
    ...source(prefix + '-' + category),
    三化分类: category,
    认定金额: '23.50',
    通报项目名称: category + '导入验收',
    项目状态: '已上线',
    '对应省分（集成）': '数智',
  }));
  await page.goto('/revenue/import');
  await switchActor(page, group);
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '直接认定.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook(rows),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await page.getByRole('button', { name: '确认导入 2 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const { items } = await api<{ items: Entry[] }>(request, group, '?search=' + prefix);
  expect(items).toHaveLength(2);
  for (const item of items) {
    expect(item.state).toBe('RECOGNIZED');
    expect(Number(item.recognition?.amount)).toBe(23.5);
    expect(item.recognition).toMatchObject({
      reportedProjectName: item.source['三化分类'] + '导入验收',
      projectStatus: '已上线',
      integratedOrganization: '数智',
    });
  }
});

test('管理员导入网络化时可留空通报项目名称，数字化和智能化直接认定时必填', async ({
  page,
  request,
}) => {
  const prefix = 'IMPORT-REPORTED-NAME-' + randomUUID();
  const rows = ['网络化', '数字化', '智能化'].map((category) => ({
    ...source(prefix + '-' + category),
    三化分类: category,
    认定金额: '23.50',
    '对应省分（集成）': '数智',
  }));
  await page.goto('/revenue/import');
  await switchActor(page, ids.admin);
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '分类通报项目名称.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook(rows),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：通报项目名称', exact: true })).toHaveValue(
    '',
  );
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByText('通报项目名称必填', { exact: true })).toHaveCount(2);
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const { items } = await api<{ items: Entry[] }>(request, ids.admin, '?search=' + prefix);
  expect(items).toHaveLength(1);
  expect(items[0].source['三化分类']).toBe('网络化');
  expect(items[0].state).toBe('RECOGNIZED');
  expect(items[0].recognition?.reportedProjectName).toBe('');
  expect(Number(items[0].recognition?.amount)).toBe(23.5);
});
