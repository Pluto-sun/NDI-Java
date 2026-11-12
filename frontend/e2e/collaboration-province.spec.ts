import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { chooseActor, get, ids } from './support/projects';
import { importWorkbook } from './support/importWorkbook';
import type { ProjectDetail, ReferenceData } from '../src/features/projects/types';

test('协作省份可单选清空，新增与导入只在详情展示协作信息', async ({ page, request }) => {
  const reference = await get<ReferenceData>(request, ids.reporter, '/reference-data');
  const jiangxi = reference.provinces.find((province) => province.name === '江西省')!;
  const name = `协作项目-${randomUUID()}`;
  await page.goto('/projects/new');
  await chooseActor(page, ids.reporter);
  const collaboration = page.getByRole('combobox', { name: '协作省份', exact: true });
  await collaboration.selectOption(jiangxi.id);
  await collaboration.selectOption('');
  await expect(collaboration).toHaveValue('');
  await collaboration.selectOption(jiangxi.id);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(name);
  await page.getByRole('spinbutton', { name: '项目总金额（万元）', exact: true }).fill('100');
  await page.getByRole('textbox', { name: '客户需求', exact: true }).fill('跨省协作项目验收');
  await page.getByRole('textbox', { name: '项目负责人', exact: true }).fill('验收负责人');
  await page.getByRole('checkbox', { name: '5G工业专网', exact: true }).check();
  await page.getByRole('spinbutton', { name: '预计金额（万元）', exact: true }).fill('50');
  await page.getByRole('textbox', { name: '项目进展', exact: true }).fill('需求已确认');
  await page.getByRole('textbox', { name: '下一步动作', exact: true }).fill('交流方案');
  await page.getByRole('button', { name: '创建项目', exact: true }).click();
  await expect(page).toHaveURL(/\/projects\/[0-9a-f-]{36}$/);
  const detail = await get<ProjectDetail>(
    request,
    ids.reporter,
    `/${page.url().split('/').at(-1)}`,
  );
  expect(detail.provinceId).toBe(ids.province);
  expect(detail.collaborationProvinceId).toBe(jiangxi.id);
  await expect(page.getByText('协作省份', { exact: true }).locator('..')).toContainText('江西省');
  await page.goto('/projects?keyword=' + encodeURIComponent(name));
  const listRow = page.getByRole('row').filter({ hasText: name });
  await expect(listRow).toContainText('本地演示省');
  await expect(listRow).toContainText('本地演示市');
  await expect(listRow).not.toContainText('江西省');
  await page.goto('/projects/new');
  await page.getByRole('link', { name: '项目导入', exact: true }).click();
  const importedName = `数据传输工程-${randomUUID()}`;
  const row = {
    省分: '本地演示省',
    地市: '本地演示市',
    项目名称: importedName,
    项目负责人: '导入负责人',
    客户需求: '导入协作项目',
    项目阶段: '方案交流',
    '项目总金额（万）': '100',
    '网络化预计金额（万）': '50',
    协作省份: '江西',
  };
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '协作省份.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: importWorkbook([
      row,
      { ...row, 项目名称: '接口校验-' + randomUUID(), 协作省份: '无效地区' },
    ]),
  });
  await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '映射：协作省份', exact: true })).toHaveValue(
    '协作省份',
  );
  await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
  await expect(page.getByText(/协作省份未匹配已启用的省份/)).toBeVisible();
  await page.getByRole('button', { name: '确认导入 1 行并结束任务', exact: true }).click();
  await expect(page.getByRole('heading', { name: '导入任务已完成', exact: true })).toBeVisible();
  const projects = await get<{ items: { id: string }[] }>(
    request,
    ids.reporter,
    `?keyword=${encodeURIComponent(importedName)}`,
  );
  expect(projects.items).toHaveLength(1);
  await page.goto('/projects/' + projects.items[0].id);
  await expect(page.getByText('协作省份', { exact: true }).locator('..')).toContainText('江西省');
});
