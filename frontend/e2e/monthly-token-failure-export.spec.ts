import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { importWorkbook } from './support/importWorkbook';
import { chooseActor, ids } from './support/projects';

test('月度 Token 导出合并失败月份，重新预检后隐藏已通过批次的下载按钮', async ({
  page,
  request,
}) => {
  const name = '月度失败导出-' + randomUUID();
  const created = await request.post('http://127.0.0.1:18080/api/v1/capability/agents-live', {
    headers: { 'X-NDI-Actor-Id': ids.admin },
    data: {
      provinceId: ids.province,
      fields: {
        省分: '本地演示省',
        接口人: '验收',
        省分智能化接口人: '省分接口',
        智能体名称: name,
        智能体功能简述: '月度用量登记',
        环节: '研发设计',
        应用项目名称: name,
        最终客户名称: '客户',
        是否为三方生态迁移: '否',
        是否计收: '未计收',
        是否在计收表中出现: '否',
      },
    },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const { id } = (await created.json()).data;
  await page.goto('/capability/agents-live/import?type=monthly-token');
  await chooseActor(page, ids.admin);
  const exportButton = page.getByRole('button', { name: '导出预检失败原始数据', exact: true });
  const chooseFile = (rows: Record<string, string>[]) =>
    page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
      name: '月度用量.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: importWorkbook(rows, {}, undefined, false),
    });
  await expect(exportButton).toHaveCount(0);
  await page.getByLabel('统计年份', { exact: true }).fill('2026');
  await chooseFile([
    {
      系统ID: id,
      '9月Token量（万）': '格式错误',
      '10月Token量（万）': '1',
      '12月Token量（万）': '0',
    },
    { 系统ID: id, '9月Token量（万）': '', '10月Token量（万）': '2', '12月Token量（万）': '' },
  ]);
  await page.getByRole('button', { name: '开始预检', exact: true }).click();
  const ready = page.getByRole('table', { name: '月度 Token 预检结果' });
  await expect(ready.getByRole('row').filter({ hasText: /2026-09.*失败/ })).toHaveCount(1);
  await expect(ready.getByRole('row').filter({ hasText: /2026-10.*失败/ })).toHaveCount(2);
  await expect(ready.getByRole('row').filter({ hasText: /2026-12.*通过/ })).toHaveCount(1);
  const downloading = page.waitForEvent('download');
  await exportButton.click();
  const download = await downloading;
  expect(download.suggestedFilename()).toBe('月度 Token 导入预检失败原始数据.xlsx');
  expect(await download.failure()).toBeNull();
  await expect(ready).toBeVisible();
  await chooseFile([{ 系统ID: id, '12月Token量（万）': '0' }]);
  await page.getByRole('button', { name: '开始预检', exact: true }).click();
  await expect(ready.getByRole('row').filter({ hasText: /2026-12.*通过/ })).toHaveCount(1);
  await expect(exportButton).toHaveCount(0);
});
