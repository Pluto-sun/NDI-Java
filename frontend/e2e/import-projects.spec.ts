import { randomUUID } from 'node:crypto';
import { test, expect, type Page as BrowserPage, type APIRequestContext } from '@playwright/test';
import { ids, chooseActor, get, post, createProject } from './support/projects';
import { importWorkbook } from './support/importWorkbook';
import type {
  ImportTask,
  ImportBatch,
  ReferenceData,
  ProjectDetail,
  ProjectProgress,
} from '../src/features/projects/types';

async function sourceRow(request: APIRequestContext, name: string) {
  const reference = await get<ReferenceData>(request, ids.reporter, '/reference-data');
  return {
    省分: reference.provinces.find((p) => p.id === ids.province)!.name,
    地市: reference.cities.find((c) => c.id === ids.city)!.name,
    客户名称: '导入验收客户',
    客户所属行业: reference.industries.find((i) => i.code === 'C25')!.label,
    项目名称: name,
    项目建设内容: '导入建设内容',
    项目负责人: '外部负责人甲；外部负责人乙',
    首条项目进展: '已完成首次交流',
    下一步动作: '安排下一轮沟通',
    项目阶段: '方案交流',
    '项目总金额（万）': '100',
    '网络化预计金额（万）': '50',
    '格物预计金额（万）': '',
    '大数据预计金额（万）': '',
    '万悟预计金额（万）': '',
    合同编号: '',
    '签约金额（万）': '',
  };
}
async function enter(page: BrowserPage) {
  await page.goto('/projects/new');
  await chooseActor(page, ids.reporter);
  await expect(
    page.getByRole('navigation').getByRole('link', { name: '项目导入', exact: true }),
  ).toHaveCount(0);
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Excel 项目导入', exact: true })).toBeVisible();
}
async function upload(page: BrowserPage, rows: Record<string, string>[]) {
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '项目数据.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook(rows),
  });
  await expect(page.getByRole('button', { name: '下一步：字段映射', exact: true })).toBeEnabled();
}
async function preview(page: BrowserPage) {
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('heading', { name: '第二步：字段映射', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: '第三步：预检与导入', exact: true }),
  ).toBeVisible();
}
async function finish(page: BrowserPage, count: number) {
  const response = page.waitForResponse(
    (r) =>
      r.url().includes('/imports/') &&
      r.url().endsWith('/confirm') &&
      r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: `确认导入 ${count} 行并结束任务`, exact: true }).click();
  const result = (await (await response).json()).data as ImportBatch;
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  return result;
}

test('从创建页进入三步导入，修正合同后一次完成，下一次必须重新上传', async ({ page, request }) => {
  const name = `合同导入-${randomUUID()}`,
    contract = `HT-${randomUUID()}`;
  const row = { ...(await sourceRow(request, name)), 项目阶段: '计收' };
  await enter(page);
  await upload(page, [row]);
  await preview(page);
  await expect(page.getByText('合同编号不能为空；签约金额不能为空', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '查看／修正第 3 行', exact: true }).click();
  const editor = page.getByRole('dialog', { name: '修正第 3 行', exact: true });
  await editor.getByLabel('合同编号', { exact: true }).fill(contract);
  await editor.getByLabel('签约金额（万）', { exact: true }).fill('80.50');
  await editor.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await expect(editor).toHaveCount(0);
  const result = await finish(page, 1);
  expect(result.importedRows).toBe(1);
  expect(await get<ImportTask | null>(request, ids.reporter, '/imports/task')).toBeNull();
  const detail = await get<ProjectDetail>(request, ids.reporter, `/${result.rows[0].projectId}`);
  expect(detail.contractNo).toBe(contract);
  expect(Number(detail.signedAmount)).toBe(80.5);
  expect(detail.responsiblePersons).toHaveLength(2);
  const progress = await get<{ items: ProjectProgress[] }>(
    request,
    ids.reporter,
    `/${detail.id}/progress`,
  );
  expect(progress.items).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ progressContent: '已完成首次交流', nextAction: '安排下一轮沟通' }),
    ]),
  );
  await page.getByRole('button', { name: '返回项目创建', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await expect(page.getByLabel('Excel 文件', { exact: true })).toHaveValue('');
  await expect(
    page.getByRole('heading', { name: '存在未完成的导入任务', exact: true }),
  ).toHaveCount(0);
  await expect(page.getByText('我的导入计划', { exact: true })).toHaveCount(0);
});

test('自动字段匹配、必选标识、逐级返回、阶段恢复、首条去重和一次性选择导入', async ({
  page,
  request,
}, testInfo) => {
  const base = await sourceRow(request, `车间温度-${randomUUID()}`);
  const { 项目名称, ...fields } = base;
  const row = { ...fields, 商机名称: 项目名称 };
  const rows = [
    row,
    { ...row, '项目总金额（万）': '999' },
    { ...row, 商机名称: `大坝水位-${randomUUID()}`, 客户名称: '' },
    { ...row, 商机名称: `道路照明-${randomUUID()}` },
  ];
  await enter(page);
  await upload(page, rows);
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：项目名称', exact: true })).toHaveValue(
    '商机名称',
  );
  const mapping = page.getByRole('region', { name: '字段映射设置', exact: true });
  await expect(mapping.getByText('必填', { exact: true }).first()).toBeVisible();
  await expect(mapping.getByText('条件必填', { exact: true }).first()).toBeAttached();
  await expect(mapping.getByText('选填', { exact: true }).first()).toBeAttached();
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await expect(page.getByText(/上次停留在第 2 步/)).toBeVisible();
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  await expect(page.getByRole('heading', { name: '第二步：字段映射', exact: true })).toBeVisible();
  await expect(page.getByLabel('Excel 文件', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByText(/重复项目：仅保留文件中首条/)).toBeVisible();
  const task = await get<ImportTask>(request, ids.reporter, '/imports/task');
  await page.getByRole('button', { name: '上一步', exact: true }).click();
  await expect(page.getByRole('heading', { name: '第二步：字段映射', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  expect((await get<ImportTask>(request, ids.reporter, '/imports/task')).batch!.batchId).toBe(
    task.batch!.batchId,
  );
  await page.getByRole('button', { name: '查看／修正第 5 行', exact: true }).click();
  const editor = page.getByRole('dialog', { name: '修正第 5 行', exact: true });
  await editor.getByLabel('客户名称', { exact: true }).fill('修正客户');
  await editor.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await expect(editor).toHaveCount(0);
  await page.getByRole('checkbox', { name: '选择第 6 行', exact: true }).uncheck();
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await expect(page.getByText(/上次停留在第 3 步/)).toBeVisible();
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  await expect(page.getByRole('checkbox', { name: '选择第 6 行', exact: true })).not.toBeChecked();
  await page.screenshot({
    path: testInfo.outputPath('import-preview-wizard.png'),
    fullPage: false,
  });
  const done = await finish(page, 2);
  expect(done.importedRows).toBe(2);
  expect(done.skippedRows).toBe(2);
  const retry = await request.post(
    `http://127.0.0.1:18080/api/v1/projects/imports/${done.batchId}/confirm`,
    { headers: { 'X-NDI-Actor-Id': ids.reporter }, data: { selectedRowNumbers: [6] } },
  );
  expect(retry.status()).toBe(409);
});

test('库内高相似项目须弹窗确认和差异说明，未处理前不能导入', async ({
  page,
  request,
}, testInfo) => {
  const prefix = `精密制造-${randomUUID()}`;
  await post(request, ids.reporter, '', {
    provinceId: ids.province,
    cityId: ids.city,
    projectName: `${prefix}温度采集一期项目`,
    customerName: '客户',
    industryCode: 'C25',
    customerNeed: '一期建设',
    totalAmount: '100',
    responsiblePersonNames: ['负责人'],
    categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50' }],
    initialProgress: '首次交流',
    nextAction: '跟进',
  });
  await enter(page);
  await upload(page, [await sourceRow(request, `${prefix}温度采集二期项目`)]);
  await preview(page);
  await expect(page.getByText('待确认相似项目', { exact: true })).toBeVisible();
  await expect(
    page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }),
  ).toBeDisabled();
  await page.getByRole('button', { name: '处理相似项目', exact: true }).click();
  const modal = page.getByRole('dialog', { name: '第 3 行相似项目确认', exact: true });
  await expect(modal.getByText(`${prefix}温度采集一期项目`, { exact: true })).toBeVisible();
  await modal
    .getByLabel('差异说明（必填，至少 5 字）', { exact: true })
    .fill('二期新增另一条生产线，与一期范围不同');
  await modal.getByRole('checkbox', { name: '确认不是同一项目', exact: true }).check();
  await page.screenshot({ path: testInfo.outputPath('import-name-review.png'), fullPage: false });
  await modal.getByRole('button', { name: '确认并记录', exact: true }).click();
  await expect(modal).toHaveCount(0);
  const result = await finish(page, 1);
  expect(result.importedRows).toBe(1);
  expect(result.rows[0].nameReview!.confirmed).toBeTruthy();
});

test('首步也可恢复，百行预检按窗口滚动，从头导入放弃原任务', async ({ page, request }) => {
  test.setTimeout(90_000);
  const base = await sourceRow(request, '临时');
  await enter(page);
  await upload(
    page,
    Array.from({ length: 90 }, (_, i) => ({ ...base, 项目名称: `${randomUUID()}-${i}` })),
  );
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await expect(page.getByText(/上次停留在第 1 步/)).toBeVisible();
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  await expect(page.getByText('已上传：')).toBeVisible();
  await preview(page);
  const table = page.getByRole('table', { name: '项目导入预检结果', exact: true });
  expect(await table.getByRole('row').count()).toBeLessThan(30);
  const viewport = page.getByLabel('预检结果，可滚动', { exact: true });
  await viewport.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
  });
  await expect(page.getByRole('checkbox', { name: '选择第 92 行', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await page.getByRole('button', { name: '从头导入', exact: true }).click();
  await expect(page.getByLabel('Excel 文件', { exact: true })).toHaveValue('');
  expect(await get<ImportTask | null>(request, ids.reporter, '/imports/task')).toBeNull();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});

test('迟到的首次任务读取不会覆盖用户已经恢复的步骤', async ({ page, request }) => {
  await enter(page);
  await upload(page, [await sourceRow(request, `恢复竞态-${randomUUID()}`)]);
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  let count = 0;
  let release!: () => void;
  let delivered!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  const done = new Promise<void>((resolve) => {
    delivered = resolve;
  });
  await page.route('**/api/v1/projects/imports/task', async (route) => {
    if (route.request().method() !== 'GET' || ++count !== 1) {
      await route.continue();
      return;
    }
    const response = await route.fetch();
    await gate;
    await route.fulfill({ response });
    delivered();
  });
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  release();
  await done;
  await page.evaluate(
    () =>
      new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
      ),
  );
  await expect(page.getByRole('heading', { name: '第二步：字段映射', exact: true })).toBeVisible();
  await expect(
    page.getByRole('heading', { name: '存在未完成的导入任务', exact: true }),
  ).toHaveCount(0);
  await page.unroute('**/api/v1/projects/imports/task');
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await page.getByRole('button', { name: '从头导入', exact: true }).click();
});

test('分类按金额自动识别，零金额计入且不再映射类别，唯一关闭入口仍可暂存', async ({
  page,
  request,
}, testInfo) => {
  const name = `金额识别-${randomUUID()}`;
  const row = {
    ...(await sourceRow(request, name)),
    三化类别: '旧类别内容忽略',
    '网络化预计金额（万）': '0',
    '格物预计金额（万）': '25.50',
  };
  await enter(page);
  await expect(page.getByRole('button', { name: '关闭并暂存', exact: true })).toHaveCount(0);
  await upload(page, [row]);
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：三化类别', exact: true })).toHaveCount(0);
  const network = page.getByRole('combobox', { name: '映射：网络化预计金额（万）', exact: true });
  const gewu = page.getByRole('combobox', { name: '映射：格物预计金额（万）', exact: true });
  for (const field of [
    '网络化预计金额（万）',
    '格物预计金额（万）',
    '大数据预计金额（万）',
    '万悟预计金额（万）',
  ])
    await page.getByRole('combobox', { name: `映射：${field}`, exact: true }).selectOption('');
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await network.selectOption('网络化预计金额（万）');
  await gewu.selectOption('格物预计金额（万）');
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  const task = await get<ImportTask>(request, ids.reporter, '/imports/task');
  expect(JSON.parse(task.batch!.rows[0].sourceCellsJson!)).not.toHaveProperty('三化类别');
  await page.screenshot({
    path: testInfo.outputPath('amount-derived-import.png'),
    fullPage: false,
  });
  const result = await finish(page, 1);
  const detail = await get<ProjectDetail>(request, ids.reporter, `/${result.rows[0].projectId}`);
  expect(detail.categoryAmounts.map((c) => c.categoryCode).sort()).toEqual(['GEWU', 'NETWORK']);
  expect(
    Number(detail.categoryAmounts.find((c) => c.categoryCode === 'NETWORK')!.expectedAmount),
  ).toBe(0);
});

test('导入字段与模板统一，项目创建和维护只保留备注', async ({ page, request }, testInfo) => {
  await page.goto('/projects/new');
  await chooseActor(page, ids.reporter);
  await expect(page.getByText('其他项目资料（选填）', { exact: true })).toHaveCount(0);
  await expect(page.getByLabel('原始项目建设内容', { exact: true })).toHaveCount(0);
  await expect(page.getByLabel('产品推广内容', { exact: true })).toHaveCount(0);
  await expect(page.getByLabel('备注（选填）', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  const { 项目负责人: omitted, ...legacy } = await sourceRow(request, `旧人员-${randomUUID()}`);
  void omitted;
  await upload(page, [
    { ...legacy, 省分负责人: '旧省分人员', 地市客户经理: '旧地市人员', 产品单位支撑专家: '旧专家' },
  ]);
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：项目负责人', exact: true })).toHaveValue(
    '',
  );
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '上一步', exact: true }).click();
  await upload(page, [await sourceRow(request, `字段一致-${randomUUID()}`)]);
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  for (const field of [
    '省分负责人',
    '地市客户经理',
    '产品单位支撑专家',
    '产品推广内容',
    '重要度',
    '支撑行业专家',
  ])
    await expect(page.getByRole('combobox', { name: `映射：${field}`, exact: true })).toHaveCount(
      0,
    );
  await expect(page.getByRole('combobox', { name: '映射：客户需求', exact: true })).toHaveValue(
    '项目建设内容',
  );
  await expect(
    page.getByRole('combobox', { name: '映射：拟招标月份', exact: true }),
  ).toBeAttached();
  const people = page.getByRole('combobox', { name: '映射：项目负责人', exact: true });
  await people.selectOption('');
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await people.selectOption('项目负责人');
  for (const field of ['首条项目进展', '下一步动作']) {
    const mapping = page.getByRole('combobox', { name: `映射：${field}`, exact: true });
    await expect(mapping).toHaveValue(field);
    await mapping.selectOption('');
    await expect(
      page.getByRole('button', { name: '下一步：开始预检', exact: true }),
    ).toBeDisabled();
    await mapping.selectOption(field);
  }
  await page.screenshot({
    path: testInfo.outputPath('current-import-fields.png'),
    fullPage: false,
  });
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await page.getByRole('button', { name: '从头导入', exact: true }).click();
  const created = await createProject(request, `备注维护-${randomUUID()}`);
  await page.goto(`/projects/${created.id}`);
  await page.getByRole('button', { name: '编辑', exact: true }).click();
  const modal = page.getByRole('dialog', { name: '编辑项目普通信息', exact: true });
  await expect(modal.getByLabel('原始项目建设内容', { exact: true })).toHaveCount(0);
  await expect(modal.getByLabel('产品推广内容', { exact: true })).toHaveCount(0);
  await modal.getByLabel('备注（选填）', { exact: true }).fill('保留备注的验证');
  await modal.getByRole('button', { name: '保存普通信息', exact: true }).click();
  await expect(modal).toHaveCount(0);
  await expect(page.getByText('保留备注的验证', { exact: true })).toBeVisible();
});
