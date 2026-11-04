import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, ids, get } from './support/projects';
import type { ReferenceData } from '../src/features/projects/types';
import { importWorkbook } from './support/importWorkbook';

for (const module of [
  { title: '项目', path: '/projects/new', root: '/api/v1/projects', actor: ids.reporter },
  { title: '排产', path: '/scheduling/new', root: '/api/v1/production/plans', actor: ids.reporter },
  { title: '计收', path: '/revenue/new', root: '/api/v1/revenue/entries', actor: ids.admin },
])
  test(`${module.title}导入支持状态筛选、每页10条及跨页保留已选行`, async ({
    page,
    request,
  }, testInfo) => {
    const ref = await get<ReferenceData>(request, ids.reporter, '/reference-data');
    const rows: Record<string, string>[] = Array.from({ length: 14 }, (_, index) => {
      const name = randomUUID();
      const invalid = index >= 12;
      if (module.title === '项目')
        return {
          省分: ref.provinces.find((p) => p.id === ids.province)!.name,
          地市: ref.cities.find((c) => c.id === ids.city)!.name,
          客户名称: '分页客户',
          客户所属行业: ref.industries.find((i) => i.code === 'C25')!.label,
          项目名称: invalid ? '' : name,
          客户需求: '筛选验证',
          项目负责人: '负责人',
          项目阶段: '方案交流',
          '项目总金额（万）': '100',
          '网络化预计金额（万）': '10',
        };
      if (module.title === '排产')
        return {
          接口人: '文本接口人',
          排产月份: '2026-11',
          省分: ref.provinces.find((p) => p.id === ids.province)!.name,
          地市: ref.cities.find((c) => c.id === ids.city)!.name,
          项目名称: name,
          客户名称: '分页客户',
          三化类别: '网络化',
          '工业专网排产（万元）': invalid ? '' : '10',
        };
      return {
        月账期: '202611',
        省份名称: '数科',
        '地市/组织名称': '',
        项目名称: name,
        合同编号: name,
        条款编号: '001',
        自然客户名称: invalid ? '' : '分页自然客户',
        条款金额: '100',
        '产品本月计收不含税额/标品含税收入': '10',
        审签日期: '--',
      };
    });
    await page.goto(module.path);
    await chooseActor(page, module.actor);
    await page.getByRole('link', { name: `${module.title}导入`, exact: true }).click();
    await page.getByLabel('Excel 文件', { exact: true }).setInputFiles({
      name: '筛选分页.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: importWorkbook(rows),
    });
    await page.getByRole('button', { name: '下一步：字段映射', exact: true }).click();
    await page.getByRole('button', { name: '下一步：开始预检', exact: true }).click();
    const table = page.getByRole('table', { name: `${module.title}导入预检结果`, exact: true });
    const filters = page.getByRole('group', { name: '预检状态筛选', exact: true });
    const pager = page.getByRole('navigation', {
      name: `${module.title}导入预检分页`,
      exact: true,
    });
    await expect(table.locator('tbody tr')).toHaveCount(10);
    await expect(pager).toContainText('第 1 / 2 页');
    const first = table.locator('tbody tr').first().getByRole('checkbox');
    const firstLabel = await first.getAttribute('aria-label');
    await expect(first).toBeChecked();
    await first.uncheck();
    await pager.getByRole('button', { name: '下一页', exact: true }).click();
    await expect(table.locator('tbody tr')).toHaveCount(4);
    await expect(pager).toContainText('第 2 / 2 页');
    await filters.getByRole('button', { name: '预检通过', exact: true }).click();
    await expect(table.locator('tbody tr')).toHaveCount(10);
    await expect(pager).toContainText('共 12 条');
    await expect(page.getByRole('checkbox', { name: firstLabel!, exact: true })).not.toBeChecked();
    await pager.getByRole('button', { name: '下一页', exact: true }).click();
    await expect(table.locator('tbody tr')).toHaveCount(2);
    await filters.getByRole('button', { name: '失败', exact: true }).click();
    await expect(table.locator('tbody tr')).toHaveCount(2);
    await expect(pager).toContainText('第 1 / 1 页');
    await expect(pager.getByRole('button', { name: '下一页', exact: true })).toBeDisabled();
    await expect(table.getByRole('checkbox').first()).toBeDisabled();
    await page.screenshot({
      path: testInfo.outputPath(`${module.title}-filtered-preview.png`),
      fullPage: true,
    });
    await filters.getByRole('button', { name: '全部', exact: true }).click();
    await expect(page.getByRole('checkbox', { name: firstLabel!, exact: true })).not.toBeChecked();
    // 隔离验收只检查预检，按版本丢弃任务，避免影响其他用例。
    const headers = { 'X-NDI-Actor-Id': module.actor };
    const response = await request.get('http://127.0.0.1:18080' + module.root + '/imports/task', {
      headers,
    });
    const task = (await response.json()).data;
    const discarded = await request.delete(
      `http://127.0.0.1:18080${module.root}/imports/task?expectedTaskId=${task.id}&expectedVersion=${task.version}`,
      { headers },
    );
    expect(discarded.ok()).toBeTruthy();
  });
