import { randomUUID } from 'node:crypto';
import { expect, test, type Locator } from '@playwright/test';
import { chooseActor, ids } from './support/projects';
import { verifyStickyAction } from './support/stickyActions';

async function verifyTwoLines(rows: Locator) {
  await expect
    .poll(async () => {
      const dimensions = await rows.evaluateAll((elements) =>
        elements.map((row) => ({
          height: row.getBoundingClientRect().height,
          truncated: [...row.querySelectorAll<HTMLElement>('.cell-text-two')].some(
            (cell) => cell.scrollHeight > cell.clientHeight,
          ),
        })),
      );
      return {
        count: dimensions.length,
        distinctHeights: new Set(dimensions.map((item) => item.height)).size,
        truncated: dimensions.some((item) => item.truncated),
      };
    })
    .toEqual({ count: 3, distinctHeights: 1, truncated: true });
}

const admin = { 'X-NDI-Actor-Id': ids.admin };
const rows = ['', '中长度内容用于跨越一行', '很长且需要查看更多的字段'.repeat(20)];

for (const [kind, fields, name] of [
  ['wangwu', { 部署地点: '企业', 部署进度: '已部署' }, '具体名称'],
  [
    'agents-live',
    {
      省分智能化接口人: '省分接口',
      环节: '研发设计',
      应用项目名称: '应用项目',
      最终客户名称: '客户',
      智能体功能简述: '验收功能',
      是否为三方生态迁移: '否',
      是否计收: '未计收',
      是否在计收表中出现: '否',
    },
    '智能体名称',
  ],
  ['agents-development', { 环节: '研发设计', 状态: '开发中' }, '智能体名称'],
] as const) {
  test(`${kind} 列表行高统一且操作固定`, async ({ page, request }, testInfo) => {
    const tag = randomUUID().slice(0, 8);
    for (const row of rows) {
      const response = await request.post('http://127.0.0.1:18080/api/v1/capability/' + kind, {
        headers: admin,
        data: {
          provinceId: ids.province,
          fields: { ...fields, 接口人: tag, 省分: '本地演示省', [name]: tag + row },
        },
      });
      expect(response.ok(), await response.text()).toBeTruthy();
    }
    await page.goto('/capability/' + kind + '?reviewer=' + tag);
    await chooseActor(page, ids.admin);
    const table = page.getByRole('region', { name: '能力指标明细' });
    const matched = table.locator('tbody tr').filter({ hasText: tag });
    await verifyTwoLines(matched);
    const longName = matched.locator('.cell-text-two').filter({ hasText: tag + rows[2] });
    await longName.focus();
    await expect(longName).toBeFocused();
    await expect(page.locator('.cell-tooltip')).toHaveText(tag + rows[2]);
    await longName.press('Escape');
    const edit = matched.first().getByRole('button', { name: '编辑', exact: true });
    await verifyStickyAction(table, edit);
    await page.screenshot({ path: testInfo.outputPath('capability-grid.png') });
    await edit.click();
    await expect(page.getByRole('dialog', { name: '编辑记录' })).toBeVisible();
  });
}

test('格物部署列表行高统一且操作固定', async ({ page, request }, testInfo) => {
  const tag = randomUUID().slice(0, 8);
  for (const row of rows) {
    const response = await request.post(
      'http://127.0.0.1:18080/api/v1/capability/gewu-deployments',
      {
        headers: admin,
        data: {
          provinceId: ids.province,
          cityId: ids.city,
          fields: {
            项目名称: tag + row,
            客户名称: '客户',
            接口人: tag,
            省份: '本地演示省',
            地市: '本地演示市',
            制造类型: '离散制造',
            国民经济行业分类: '制造业',
            所属行业: '装备制造',
            部署版本: '1.0',
            上一周期是否已接设备: '否',
          },
        },
      },
    );
    expect(response.ok(), await response.text()).toBeTruthy();
  }
  await page.goto('/capability/gewu');
  await chooseActor(page, ids.admin);
  await page.getByLabel('关键词', { exact: true }).fill(tag);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  const table = page.getByRole('region', { name: '格物部署明细横向滚动区域' });
  const matched = table.locator('tbody tr').filter({ hasText: tag });
  await verifyTwoLines(matched);
  const edit = matched.first().getByRole('button', { name: '编辑', exact: true });
  await verifyStickyAction(table, edit);
  await page.screenshot({ path: testInfo.outputPath('gewu-grid.png') });
  await edit.click();
  await expect(page.getByRole('dialog')).toBeVisible();
});
