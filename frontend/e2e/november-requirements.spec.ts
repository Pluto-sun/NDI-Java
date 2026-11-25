import { requiredRevenueFields } from './support/revenue';
import { verifyStickyAction } from './support/stickyActions';
import { randomUUID } from 'node:crypto';
import { test, expect, type Page } from '@playwright/test';
import { chooseActor, get, ids, post } from './support/projects';
import { importWorkbook } from './support/importWorkbook';
import type { ReferenceData } from '../src/features/projects/types';

const group = '77777777-7777-4777-8777-777777777777';
const reviewer = '55555555-5555-4555-8555-555555555555';
const server = 'http://127.0.0.1:18080/api/v1';

async function upload(page: Page, rows: Record<string, string>[]) {
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '隔离验收.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook(rows),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
}

async function confirm(page: Page, count: number) {
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await page.getByRole('button', { name: `确认导入 ${count} 行并结束任务`, exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
}

test('接口人按省份创建商机，项目导入客户可空并转换负责人及签约主体', async ({ page, request }) => {
  const reference = await get<ReferenceData>(request, ids.admin, '/reference-data');
  const local = reference.cities.find((city) => city.id === ids.city)!;
  const outside = reference.cities.find((city) => city.provinceId !== ids.province)!;
  const input = {
    provinceId: ids.province,
    cityId: ids.city,
    projectName: `设备协同-${randomUUID()}`,
    customerNeed: '客户需求验收',
    responsiblePersonNames: ['项目成员'],
    totalAmount: '5',
    categoryAmounts: [{ categoryCode: 'GEWU', expectedAmount: '5' }],
    initialProgress: '完成调研',
    nextAction: '交流方案',
  };
  const project = await post<{ id: string; industryCode: string | null }>(
    request,
    reviewer,
    '',
    input,
  );
  expect(project.industryCode).toBeNull();
  const denied = await request.post(`${server}/projects`, {
    headers: { 'X-NDI-Actor-Id': reviewer },
    data: {
      ...input,
      projectName: `跨省-${randomUUID()}`,
      provinceId: outside.provinceId,
      cityId: outside.id,
    },
  });
  expect(denied.status()).toBe(403);
  await page.goto('/projects/new');
  await chooseActor(page, reviewer);
  const province = page.getByRole('combobox', { name: '归属省分', exact: true });
  await expect(province).toHaveValue(ids.province);
  await expect(province.locator('option')).toHaveCount(2);
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  const name = `工业采集-${randomUUID()}`;
  await upload(page, [
    {
      省分: reference.provinces.find((item) => item.id === ids.province)!.name,
      地市: local.name,
      项目名称: name,
      项目负责人: '省分负责人：成员甲;经理:成员乙；成员丙',
      客户需求: '导入需求',
      项目阶段: '方案交流',
      首条项目进展: '导入验收',
      下一步动作: '跟进交流',
      '项目总金额（万）': '5',
      '格物预计金额（万）': '5',
      是否数科总投总签: '是',
      拟招标时间: '2026/07/20',
    },
  ]);
  await expect(page.getByRole('combobox', { name: '映射：签约主体', exact: true })).toHaveValue(
    '是否数科总投总签',
  );
  await expect(page.getByRole('combobox', { name: '映射：拟招标月份', exact: true })).toHaveValue(
    '拟招标时间',
  );
  await confirm(page, 1);
  const list = await get<{ items: { id: string }[] }>(
    request,
    reviewer,
    `?keyword=${encodeURIComponent(name)}`,
  );
  expect(list.items).toHaveLength(1);
  const detail = await get<{
    signingSubjectCode: string;
    industryCode: string | null;
    responsiblePersons: { name: string }[];
  }>(request, reviewer, `/${list.items[0].id}`);
  expect(detail.industryCode).toBeNull();
  expect(detail.signingSubjectCode).toBe('DS_SIGN');
  expect(detail.responsiblePersons.map((person) => person.name)).toEqual([
    '成员甲',
    '成员乙',
    '成员丙',
  ]);
});

test('集团可在不同省份新增和导入项目', async ({ page, request }) => {
  const reference = await get<ReferenceData>(request, group, '/reference-data');
  const outside = reference.cities.find(
    (city) => city.provinceId !== ids.province && !city.legacyMunicipalityCity,
  )!;
  const selectedProvince = reference.provinces.find((item) => item.id === outside.provinceId)!;
  const name = '集团商机-' + randomUUID();
  const result = await post<{ id: string }>(request, group, '', {
    provinceId: outside.provinceId,
    cityId: outside.id,
    projectName: name,
    customerNeed: '集团新增需求',
    responsiblePersonNames: ['项目负责人'],
    totalAmount: '5',
    categoryAmounts: [{ categoryCode: 'GEWU', expectedAmount: '5' }],
    initialProgress: '已调研',
    nextAction: '准备材料',
  });
  expect(result.id).toBeTruthy();
  await page.goto('/projects/new');
  await chooseActor(page, group);
  const provinceSelect = page.getByRole('combobox', { name: '归属省分', exact: true });
  await provinceSelect.selectOption(outside.provinceId);
  await provinceSelect.selectOption(ids.province);
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  const excel = (province: string, city: string) => ({
    省分: province,
    地市: city,
    项目名称: '集团导入-' + randomUUID(),
    项目负责人: '负责人',
    首条项目进展: '已交流',
    下一步动作: '继续跟进',
    项目建设内容: '集团导入需求',
    项目阶段: '方案交流',
    '项目总金额（万）': '5',
    '格物预计金额（万）': '5',
  });
  await upload(page, [
    excel(selectedProvince.name, outside.name),
    excel(
      reference.provinces.find((p) => p.id === ids.province)!.name,
      reference.cities.find((c) => c.id === ids.city)!.name,
    ),
  ]);
  await confirm(page, 2);
});

test('数字化导入保持未认定，可独立按地市或组织筛选', async ({ page }, testInfo) => {
  await page.goto('/revenue/import');
  await chooseActor(page, group);
  const organization = `部署服务中心-${randomUUID().slice(0, 8)}`;
  const row = {
    ...requiredRevenueFields,
    月账期: '202611',
    省份名称: '具体组织',
    '地市/组织名称': organization,
    合同编号: `CN-${randomUUID()}`,
    项目名称: '来源计收验收',
    三化分类: '数字化',
    条款金额: '100',
    '产品本月计收不含税额/标品含税收入': '20',
    认定金额: '50',
    通报项目名称: '生效通报项目',
    '对应省分（集成）': '本地演示省',
  };
  await upload(page, [
    row,
    { ...row, 合同编号: row.合同编号 + '-partial', 通报项目名称: '' },
    { ...row, 合同编号: row.合同编号 + '-zero', 认定金额: '0' },
    { ...row, 合同编号: row.合同编号 + '-negative', 认定金额: '-1' },
  ]);
  await confirm(page, 4);
  await page.goto('/revenue');
  await page.getByLabel('地市／组织', { exact: true }).fill(organization);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '省份／具体组织', exact: true })).toHaveValue('');
  const table = page.getByRole('table', { name: '计收明细清单', exact: true });
  await expect(table.locator('tbody tr')).toHaveCount(4);
  await expect(table.locator('tbody tr').filter({ hasText: '未认定' })).toHaveCount(4);
  await page.screenshot({
    path: testInfo.outputPath('revenue-organization-filter.png'),
    fullPage: true,
  });
});

test('集团新增和批量登记格物项目，重名失败且地市禁止维护辖区外', async ({
  page,
  request,
}, testInfo) => {
  const reference = await get<ReferenceData>(request, ids.admin, '/reference-data');
  const local = reference.cities.find((city) => city.id === ids.city)!;
  const provinceName = reference.provinces.find((item) => item.id === ids.province)!.name;
  const outside = reference.cities.find((city) => city.provinceId !== ids.province)!;
  const name = `联网部署-${randomUUID().slice(0, 8)}`;
  const fields = {
    接口人: '部署联系人',
    客户名称: '格物部署客户',
    项目名称: name,
    制造类型: '流程制造',
    国民经济行业分类: '食品制造业',
    所属行业: '食品',
    部署版本: '极简版',
    上一周期是否已接设备: '是',
  };
  const denied = await request.post(`${server}/capability/gewu-deployments`, {
    headers: { 'X-NDI-Actor-Id': ids.reporter },
    data: { provinceId: outside.provinceId, cityId: outside.id, fields },
  });
  expect(denied.status()).toBe(403);
  await page.goto('/capability/gewu');
  await chooseActor(page, group);
  await page.getByRole('button', { name: '新增部署', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '新增格物部署', exact: true });
  await dialog.getByRole('combobox', { name: '省份', exact: true }).selectOption(ids.province);
  await dialog.getByRole('combobox', { name: '地市', exact: true }).selectOption(ids.city);
  for (const [label, value] of Object.entries(fields)) {
    if (label === '上一周期是否已接设备')
      await dialog.getByRole('combobox', { name: label, exact: true }).selectOption(value);
    else await dialog.getByLabel(label, { exact: true }).fill(value);
  }
  await dialog.getByRole('button', { name: '保存部署', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('link', { name: 'Excel 导入', exact: true }).click();
  const excel = {
    ...fields,
    省份: provinceName,
    地市: local.name,
    部署版本: '公有云',
    设备接入日期: '2026/2/10',
    物模型总数: '48',
    接入设备总数量: '1125',
    是否最新工具包: '不涉及',
  };
  const importedName = name + '-导入';
  await upload(page, [
    excel,
    { ...excel, 项目名称: importedName },
    { ...excel, 项目名称: importedName },
  ]);
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByText(/系统内已存在相同业务唯一键/)).toBeVisible();
  await expect(page.getByText(/与文件内前序记录重复/)).toBeVisible();
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  await page.goto(`/capability/gewu?search=${encodeURIComponent(name)}`);
  const table = page.getByRole('table', { name: '已部署格物项目清单', exact: true });
  await expect(table.locator('tbody tr')).toHaveCount(2);
  await verifyStickyAction(
    page.locator('.gewu-scroll'),
    table.getByRole('link', { name: '详情', exact: true }).first(),
  );
  await expect(table.locator('tbody tr').filter({ hasText: '公有云' })).toContainText('2026-02-10');
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('gewu-deployment-list.png'), fullPage: true });
  await expect(table.getByRole('button', { name: '删除', exact: true })).toHaveCount(0);
  const deployments = await request.get(
    `${server}/capability/gewu-deployments?search=${encodeURIComponent(name)}`,
    {
      headers: { 'X-NDI-Actor-Id': group },
    },
  );
  const [record] = (await deployments.json()).data.items;
  const endpoint = `${server}/capability/gewu-deployments/${record.id}?version=${record.version}`;
  expect((await request.delete(endpoint, { headers: { 'X-NDI-Actor-Id': group } })).status()).toBe(
    403,
  );

  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, ids.admin);
  await expect(table.getByRole('button', { name: '删除', exact: true })).toHaveCount(2);
  await table.getByRole('button', { name: '删除', exact: true }).first().click();
  await page
    .getByRole('dialog', { name: '删除部署记录' })
    .getByRole('button', { name: '删除部署', exact: true })
    .click();
  await expect(table.locator('tbody tr')).toHaveCount(1);
});
