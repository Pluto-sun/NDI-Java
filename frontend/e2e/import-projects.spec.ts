import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { test, expect } from '@playwright/test';
import { ids, chooseActor, get } from './support/projects';
import { importWorkbook } from './support/importWorkbook';
import type {
  ReferenceData,
  ProjectUser,
  ImportBatch,
  ProjectDetail,
} from '../src/features/projects/types';

test('工作表映射、失败行修正、首条去重、选择性导入、计划恢复和取消', async ({
  page,
  request,
}, testInfo) => {
  const name = `导入-${randomUUID()}`;
  const reference = await get<ReferenceData>(request, ids.reporter, '/reference-data');
  const leaders = await get<ProjectUser[]>(
    request,
    ids.reporter,
    `/province-leaders?provinceId=${ids.province}`,
  );
  const row = {
    省分: reference.provinces.find((item) => item.id === ids.province)!.name,
    地市: reference.cities.find((item) => item.id === ids.city)!.name,
    客户名称: '导入验收客户',
    客户所属行业: reference.industries.find((item) => item.code === 'C25')!.label,
    商机名称: `${name}-首条`,
    省分负责人: leaders.find((item) => item.id === ids.leader)!.displayName,
    地市客户经理: '导入客户经理',
    项目建设内容: '保留建设原文',
    三化类别: '网络化',
    项目阶段: '方案交流',
    '项目总金额（万）': '100',
    '网络化预计金额（万）': '50',
    '格物预计金额（万）': '',
    '大数据预计金额（万）': '',
    '万悟预计金额（万）': '',
    签约主体: '数科签约',
    产品单位支撑专家: '专家甲,专家乙',
    产品推广内容: '推广原文',
    备注: '导入备注',
    '项目进展（2026年6月1日）': '首次历史交流',
  };
  const buffer = importWorkbook([
    row,
    { ...row, '项目总金额（万）': '999' },
    { ...row, 商机名称: `${name}-修正`, 客户名称: '' },
    { ...row, 商机名称: `${name}-不选` },
  ]);
  await page.goto('/projects/import');
  await chooseActor(page, ids.reporter);
  const templateEvent = page.waitForEvent('download');
  await page.getByRole('button', { name: '下载导入模板', exact: true }).click();
  const template = await templateEvent;
  expect(template.suggestedFilename()).toBe('项目导入模板.xlsx');
  expect((await readFile((await template.path())!)).subarray(0, 2).toString()).toBe('PK');
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: `${name}.xlsx`,
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer,
  });
  await page.getByRole('button', { name: '识别工作表与表头', exact: true }).click();
  await expect(page.getByRole('combobox', { name: '工作表', exact: true })).toHaveValue(
    '验收工作表',
  );
  await expect(page.getByRole('combobox', { name: '表头行', exact: true })).toHaveValue('2');
  await page.getByRole('combobox', { name: '项目名称', exact: true }).selectOption('商机名称');
  const previewEvent = page.waitForResponse(
    (response) =>
      response.url().endsWith('/imports/preview') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '开始预检', exact: true }).click();
  const previewResponse = await previewEvent;
  expect(previewResponse.ok(), await previewResponse.text()).toBeTruthy();
  const preview: ImportBatch = (await previewResponse.json()).data;
  expect(preview.rows.map((item) => item.status)).toEqual(['READY', 'FAILED', 'FAILED', 'READY']);
  expect(
    (
      await get<{ totalElements: number }>(
        request,
        ids.reporter,
        `?projectName=${encodeURIComponent(name)}`,
      )
    ).totalElements,
  ).toBe(0);
  await expect(page.getByText('客户名称不能为空', { exact: true })).toBeVisible();
  const failedEvent = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出失败行', exact: true }).click();
  const failed = await failedEvent;
  expect(failed.suggestedFilename()).toBe('项目导入失败行.xlsx');
  expect((await readFile((await failed.path())!)).subarray(0, 2).toString()).toBe('PK');
  await page.getByRole('button', { name: '修正第 5 行', exact: true }).click();
  const editor = page.locator('.import-row-editor');
  await editor.getByLabel('客户名称', { exact: true }).fill('修正后的客户');
  await editor.getByRole('button', { name: '保存并重新预检', exact: true }).click();
  await expect(editor).toHaveCount(0);
  await expect(page.getByLabel('选择第 5 行', { exact: true })).toBeChecked();
  await page.getByLabel('选择第 6 行', { exact: true }).uncheck();
  page.once('dialog', (dialog) => dialog.dismiss());
  await page.getByRole('button', { name: '确认导入已选 2 行', exact: true }).click();
  expect(
    (await get<ImportBatch>(request, ids.reporter, `/imports/${preview.batchId}`)).status,
  ).toBe('PREVIEWED');
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: '确认导入已选 2 行', exact: true }).click();
  await expect(page.getByText(new RegExp(`批次 ${preview.batchId} · 状态 已完成`))).toBeVisible();
  const completed = await get<ImportBatch>(request, ids.reporter, `/imports/${preview.batchId}`);
  expect(completed).toMatchObject({ importedRows: 2, failedRows: 1, skippedRows: 1 });
  expect(completed.rows.map((item) => item.status)).toEqual([
    'IMPORTED',
    'FAILED',
    'IMPORTED',
    'SKIPPED',
  ]);
  const first = await get<ProjectDetail>(request, ids.reporter, `/${completed.rows[0].projectId}`);
  expect(first).toMatchObject({
    projectName: `${name}-首条`,
    totalAmount: 100,
    signingSubjectCode: 'DS_SIGN',
    constructionContentRaw: '保留建设原文',
    productSupportExpertNames: ['专家甲', '专家乙'],
  });
  expect(
    (
      await get<{ totalElements: number }>(
        request,
        ids.reporter,
        `?projectName=${encodeURIComponent(name)}`,
      )
    ).totalElements,
  ).toBe(2);
  const foreign = await request.get(
    `http://127.0.0.1:18080/api/v1/projects/imports/${preview.batchId}`,
    { headers: { 'X-NDI-Actor-Id': ids.leader } },
  );
  expect(foreign.status()).toBe(404);
  await page.reload();
  await page
    .locator('.import-plan-item')
    .filter({ hasText: `${name}.xlsx` })
    .getByRole('button', { name: '查看计划', exact: true })
    .click();
  await expect(page.getByText(new RegExp(`批次 ${preview.batchId} · 状态 已完成`))).toBeVisible();
  await page.getByRole('link', { name: '查看项目', exact: true }).first().click();
  await expect(page.getByRole('heading', { name: `${name}-首条`, exact: true })).toBeVisible();
  await expect(page.getByText('首次历史交流', { exact: true })).toBeVisible();
  await page.goto('/projects/import');
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '取消验收.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer,
  });
  await page.getByRole('button', { name: '识别工作表与表头', exact: true }).click();
  await page.getByRole('combobox', { name: '项目名称', exact: true }).selectOption('商机名称');
  await page.getByRole('button', { name: '开始预检', exact: true }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: '取消计划', exact: true }).click();
  await expect(page.getByText(/批次 .+ · 状态 已取消/)).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('import-plan-completed.png'), fullPage: true });
  await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
    name: '下一份文件.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer,
  });
  await expect(page.getByText('预检结果', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '取消计划', exact: true })).toHaveCount(0);
});
