import { randomInt, randomUUID } from 'node:crypto';
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
async function enter(page: BrowserPage, actor: string = ids.reporter) {
  await page.goto('/projects/new');
  await chooseActor(page, actor);
  await expect(
    page.getByRole('navigation').getByRole('link', { name: '项目导入', exact: true }),
  ).toHaveCount(0);
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Excel 项目导入', exact: true })).toBeVisible();
  await discardPriorImport(page);
}
async function discardPriorImport(page: BrowserPage) {
  const fresh = page.getByRole('button', { name: '从头导入', exact: true });
  await expect(fresh.or(page.getByLabel('Excel 文件', { exact: true }))).toBeVisible();
  if (await fresh.isVisible()) await fresh.click();
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
  const button = page.getByRole('button', { name: `确认导入 ${count} 行并结束任务`, exact: true });
  await expect(button).toBeEnabled();
  const [response] = await Promise.all([
    page.waitForResponse(
      (result) =>
        result.url().includes('/imports/') &&
        result.url().endsWith('/confirm') &&
        result.request().method() === 'POST',
      { timeout: 30_000 },
    ),
    button.click(),
  ]);
  expect(response.ok(), await response.text()).toBeTruthy();
  const result = (await response.json()).data as ImportBatch;
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
  await expect(editor.getByRole('combobox', { name: '项目阶段', exact: true })).toHaveValue(
    '待计收',
  );
  await editor.getByLabel('合同编号', { exact: true }).fill(contract);
  await editor.getByLabel('签约金额（万）', { exact: true }).fill('80.50');
  await page.route('**/projects/imports/*/rows/3', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 1200));
    await route.continue();
  });
  await editor.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '正在处理' })).toContainText(
    '正在保存修正并重新预检…',
  );
  await page.keyboard.press('Enter');
  await expect(editor).toHaveCount(0);
  await page.getByRole('checkbox', { name: '选择第 3 行', exact: true }).check();
  const result = await finish(page, 1);
  expect(result.importedRows).toBe(1);
  expect(await get<ImportTask | null>(request, ids.reporter, '/imports/task')).toBeNull();
  const detail = await get<ProjectDetail>(request, ids.reporter, `/${result.rows[0].projectId}`);
  expect(detail.currentStageCode).toBe('COLLECTION');
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
  await upload(page, [
    {
      ...(await sourceRow(request, '新阶段名称-' + randomUUID())),
      项目阶段: '待计收',
      合同编号: 'HT-' + randomUUID(),
      '签约金额（万）': '50',
    },
  ]);
  await preview(page);
  const newLabel = await finish(page, 1);
  const imported = await get<ProjectDetail>(
    request,
    ids.reporter,
    `/${newLabel.rows[0].projectId}`,
  );
  expect(imported.currentStageCode).toBe('COLLECTION');
});

test('预检修正使用字典选项并联动省市', async ({ page, request }) => {
  const ref = await get<ReferenceData>(request, ids.reporter, '/reference-data');
  const row = await sourceRow(request, `修正枚举-${randomUUID()}`);
  const scenario = ref.applicationScenarios[0];
  await enter(page);
  await upload(page, [
    {
      ...row,
      省分: '无效省分',
      协作省份: '无效省份',
      客户所属行业: '无效行业',
      项目阶段: '无效阶段',
      签约主体: '无效主体',
      应用场景: '无效场景',
      '项目进展（2026年10月1日）': '十月进展',
      '项目进展（2026年2月1日）': '二月进展',
    },
  ]);
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  const mappingFields = page.getByRole('region', { name: '字段映射设置' }).locator('tbody th');
  await expect(mappingFields.first()).toBeVisible();
  const mappingOrder = await mappingFields.allTextContents();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await page.getByRole('button', { name: '查看／修正第 3 行', exact: true }).click();
  const editor = page.getByRole('dialog', { name: '修正第 3 行', exact: true });
  const labels = editor.locator('.import-row-fields > label');
  await expect(labels.first()).toBeVisible();
  const fieldLabels = await labels.evaluateAll((items) =>
    items.map((label) => label.firstChild?.textContent?.trim()),
  );
  expect(mappingOrder.length).toBeGreaterThan(5);
  expect(fieldLabels).toEqual([
    ...mappingOrder,
    '项目进展（2026年2月1日）',
    '项目进展（2026年10月1日）',
  ]);
  const field = (name: string) => editor.getByRole('combobox', { name, exact: true });
  await field('省分').selectOption(row.省分);
  await expect(field('地市')).toHaveValue('');
  await field('地市').selectOption(row.地市);
  await field('协作省份').selectOption('');
  await field('客户所属行业').selectOption('');
  await field('项目阶段').selectOption('方案交流');
  await field('签约主体').selectOption('数科签约');
  await editor.getByRole('listbox', { name: '应用场景', exact: true }).selectOption(scenario.code);
  await editor.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await page.getByRole('checkbox', { name: '选择第 3 行', exact: true }).check();
  const result = await finish(page, 1);
  expect(result.importedRows).toBe(1);
});

test('首条进展和下一步动作均可不映射，导入后不生成空进展', async ({ page, request }) => {
  const source = await sourceRow(request, `无进展导入-${randomUUID()}`);
  const { 首条项目进展: omittedProgress, 下一步动作: omittedAction, ...row } = source;
  void omittedProgress;
  void omittedAction;
  await enter(page);
  await upload(page, [row]);
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  for (const field of ['首条项目进展', '下一步动作'])
    await expect(page.getByRole('combobox', { name: `映射：${field}`, exact: true })).toHaveValue(
      '',
    );
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: '第三步：预检与导入', exact: true }),
  ).toBeVisible();
  const done = await finish(page, 1);
  const progress = await get<{ items: ProjectProgress[] }>(
    request,
    ids.reporter,
    `/${done.rows[0].projectId}/progress`,
  );
  expect(progress.items).toHaveLength(0);
});

test('自动字段匹配、必选标识、逐级返回、阶段恢复、首条去重和一次性选择导入', async ({
  page,
  request,
}, testInfo) => {
  test.setTimeout(75_000);
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
  await page.getByRole('checkbox', { name: '选择第 5 行', exact: true }).check();
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
  const candidate = page.getByRole('checkbox', { name: '选择第 3 行', exact: true });
  await expect(candidate).not.toBeChecked();
  await expect(candidate).toBeDisabled();
  await page.getByRole('button', { name: '全选可导入', exact: true }).click();
  await expect(candidate).not.toBeChecked();
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
  await expect(candidate).not.toBeChecked();
  await candidate.check();
  const result = await finish(page, 1);
  expect(result.importedRows).toBe(1);
  expect(result.rows[0].nameReview!.confirmed).toBeTruthy();
});

test('首步也可恢复，九十行预检按10条分页，从头导入放弃原任务', async ({ page, request }) => {
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
  await expect(table.locator('tbody tr')).toHaveCount(10);
  const pager = page.getByRole('navigation', { name: '项目导入预检分页', exact: true });
  for (let index = 0; index < 8; index++)
    await pager.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(pager).toContainText('第 9 / 9 页');
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
  const network = page.getByRole('combobox', {
    name: '映射：5G工业专网预计金额（万）',
    exact: true,
  });
  const gewu = page.getByRole('combobox', {
    name: '映射：格物工业互联网平台预计金额（万）',
    exact: true,
  });
  for (const field of [
    '5G工业专网预计金额（万）',
    '格物工业互联网平台预计金额（万）',
    '工业大数据平台预计金额（万）',
    '万悟工业智能体平台预计金额（万）',
  ])
    await page.getByRole('combobox', { name: `映射：${field}`, exact: true }).selectOption('');
  await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeDisabled();
  await network.selectOption('网络化预计金额（万）');
  await gewu.selectOption('格物预计金额（万）');
  await page.getByRole('button', { name: '关闭Excel 项目导入', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await page.getByRole('button', { name: '继续完成导入', exact: true }).click();
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByRole('heading', { name: '第三步：预检与导入' })).toBeVisible();
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
  await discardPriorImport(page);
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
    await expect(page.getByRole('button', { name: '下一步：开始预检', exact: true })).toBeEnabled();
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

test('直辖市地市须填写实际区县，预检修正可选择区县', async ({ page, request }, testInfo) => {
  const reference = await get<ReferenceData>(request, ids.admin, '/reference-data');
  const municipalities = reference.provinces.filter((province) => province.municipality);
  expect(municipalities.map((province) => province.name)).toEqual(
    expect.arrayContaining(['北京市', '天津市', '上海市', '重庆市']),
  );
  for (const [name, count] of Object.entries({ 北京市: 16, 天津市: 16, 上海市: 16, 重庆市: 37 })) {
    const province = municipalities.find((item) => item.name === name)!;
    const cities = reference.cities.filter((city) => city.provinceId === province.id);
    expect(cities.filter((city) => !city.legacyMunicipalityCity)).toHaveLength(count);
    expect(cities.filter((city) => city.legacyMunicipalityCity)).toHaveLength(1);
  }

  const adminHeaders = { 'X-NDI-Actor-Id': ids.admin };
  const adminUrl = 'http://127.0.0.1:18080/api/v1/admin/users';
  const account = await request.post(adminUrl, {
    headers: adminHeaders,
    data: {
      account: {
        loginName: `137${randomInt(10000000, 100000000)}`,
        displayName: '直辖市验收接口人',
        roleCodes: ['INTERFACE_REVIEWER'],
      },
      password: 'District-E2e-2026!',
    },
  });
  expect(account.ok(), await account.text()).toBeTruthy();
  const reviewer = (await account.json()).data as { id: string; version: number };
  const [municipalityName, districtName] =
    testInfo.project.name === 'small-screen' ? ['上海市', '浦东'] : ['北京市', '昌平'];
  const municipality = municipalities.find((province) => province.name === municipalityName)!;
  const assignment = await request.put(`${adminUrl}/${reviewer.id}/reviewer-provinces`, {
    headers: adminHeaders,
    data: { version: reviewer.version, provinceIds: [municipality.id] },
  });
  expect(assignment.ok(), await assignment.text()).toBeTruthy();
  const legacy = reference.cities.find(
    (city) => city.provinceId === municipality.id && city.legacyMunicipalityCity,
  )!;
  const rejected = await request.post('http://127.0.0.1:18080/api/v1/projects', {
    headers: { 'X-NDI-Actor-Id': reviewer.id },
    data: {
      provinceId: municipality.id,
      cityId: legacy.id,
      projectName: `直辖市创建校验-${randomUUID()}`,
      customerNeed: '验证实际区县',
      responsiblePersonNames: ['验收负责人'],
      totalAmount: '100',
      categoryAmounts: [{ categoryCode: 'NETWORK', expectedAmount: '50' }],
      initialProgress: '已交流',
      nextAction: '跟进',
    },
  });
  expect(rejected.status()).toBe(400);
  expect((await rejected.json()).code).toBe('PROJECT_MUNICIPALITY_DISTRICT_REQUIRED');

  const row = await sourceRow(request, `直辖市区县-${randomUUID()}`);
  await page.goto('/projects/new');
  await chooseActor(page, reviewer.id);
  const district = page.getByRole('combobox', { name: '归属区县', exact: true });
  await expect(district.locator('option')).toHaveCount(17);
  await expect(district.locator('option', { hasText: municipalityName })).toHaveCount(0);
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await upload(page, [{ ...row, 省分: municipalityName, 地市: municipalityName }]);
  await preview(page);
  await expect(page.getByText('直辖市的地市字段须填写实际区县', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '查看／修正第 3 行', exact: true }).click();
  const editor = page.getByRole('dialog', { name: '修正第 3 行', exact: true });
  const city = editor.getByRole('combobox', { name: '地市', exact: true });
  await city.selectOption(districtName);
  await expect(city).toHaveValue(districtName);
  await expect(city.locator('option', { hasText: municipalityName })).toHaveCount(0);
  await editor.getByRole('button', { name: '保存并重新校验', exact: true }).click();
  await page.getByRole('checkbox', { name: '选择第 3 行', exact: true }).check();
  const result = await finish(page, 1);
  await page.getByRole('button', { name: '返回项目创建', exact: true }).click();
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  await upload(page, [
    {
      ...row,
      项目名称: `旧表区县全称-${randomUUID()}`,
      省分: municipalityName,
      地市: testInfo.project.name === 'small-screen' ? '浦东新区' : '昌平区',
    },
  ]);
  await preview(page);
  expect((await finish(page, 1)).importedRows).toBe(1);
  const detail = await get<ProjectDetail>(request, reviewer.id, `/${result.rows[0].projectId}`);
  expect(detail.provinceId).toBe(municipality.id);
  expect(reference.cities.find((item) => item.id === detail.cityId)?.name).toBe(districtName);
});

test('预检失败原始数据可在确认导入前下载', async ({ page, request }) => {
  const row = await sourceRow(request, `原始数据导出-${randomUUID()}`);
  await enter(page);
  await upload(page, [
    { ...row, 项目阶段: '未知阶段', 自定义编号: '000007' },
    { ...row, 项目名称: `通过项目-${randomUUID()}`, 自定义编号: '000009' },
  ]);
  await preview(page);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出预检失败原始数据', exact: true }).click();
  expect((await download).suggestedFilename()).toBe('项目导入预检失败原始数据.xlsx');
  await expect(page.getByRole('heading', { name: '第三步：预检与导入' })).toBeVisible();
  expect((await finish(page, 1)).importedRows).toBe(1);
});
