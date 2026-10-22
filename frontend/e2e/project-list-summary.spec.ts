import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, ids, post } from './support/projects';

test('项目列表展示归属、类别、签约主体、负责人和唯一所属月份，表格可键盘横向滚动', async ({
  page,
  request,
}, testInfo) => {
  const name = `清单摘要-${randomUUID()}`;
  const project = await post<{ id: string; attributionMonth: string; provinceLeaderName: string }>(
    request,
    ids.reporter,
    '',
    {
      provinceId: ids.province,
      cityId: ids.city,
      projectName: name,
      customerName: '清单客户',
      industryCode: 'C25',
      customerNeed: '验证清单摘要',
      totalAmount: 100,
      provinceLeaderUserId: ids.leader,
      cityAccountManagerName: '客户经理',
      signingSubjectCode: 'DS_SIGN',
      categoryAmounts: [
        { categoryCode: 'NETWORK', expectedAmount: 60 },
        { categoryCode: 'GEWU', expectedAmount: 30 },
      ],
      initialProgress: '首次沟通',
      nextAction: '继续跟进',
      confirmNew: true,
      duplicateReason: '独立清单验收',
    },
  );
  expect(project.provinceLeaderName).toBe('演示省分负责人');
  await page.goto('/projects');
  await chooseActor(page, ids.reporter);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(name);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  const table = page.getByRole('table', { name: '项目清单', exact: true });
  const row = table.getByRole('row').filter({ hasText: name });
  await expect(row).toHaveCount(1);
  for (const text of [
    '本地演示省',
    '本地演示市',
    '网络化',
    '格物',
    '数科签约',
    '演示省分负责人',
    project.attributionMonth.slice(0, 7),
  ]) {
    await expect(row).toContainText(text);
  }
  const scroller = page.getByRole('region', { name: '项目清单表格，可横向滚动', exact: true });
  await scroller.focus();
  await page.keyboard.press('ArrowRight');
  await expect.poll(() => scroller.evaluate((element) => element.scrollLeft)).toBeGreaterThan(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('project-list-summary.png'), fullPage: true });
});
