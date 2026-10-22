import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { ids, get, createProject, chooseActor } from './support/projects';

test('一份申请修改名称总额与分类金额，由指定省分审批后共同生效', async ({
  page,
  request,
}, testInfo) => {
  const projectName = `组合变更验收-${randomUUID()}`;
  const newName = `核准-${projectName}`;
  const project = await createProject(request, projectName);
  await page.goto(`/projects/${project.id}`);
  await chooseActor(page, ids.reporter);
  await page.getByRole('button', { name: '发起重大变更', exact: true }).click();
  const form = page.getByRole('form', { name: '重大变更申请', exact: true });
  await form.getByRole('checkbox', { name: '变更项目总金额', exact: true }).check();
  await form.getByRole('checkbox', { name: '变更网络化金额', exact: true }).check();
  await form.getByRole('checkbox', { name: '变更格物金额', exact: true }).check();
  await form.getByRole('textbox', { name: /^调整后项目名称/ }).fill(newName);
  await form.getByRole('spinbutton', { name: /^调整后项目总金额/ }).fill('150.00');
  await form.getByRole('spinbutton', { name: /^调整后网络化金额/ }).fill('80.00');
  await form.getByRole('spinbutton', { name: /^调整后格物金额/ }).fill('20.00');
  await form
    .getByRole('combobox', { name: '重大变更审批人', exact: true })
    .selectOption(ids.leader);
  await form
    .getByRole('textbox', { name: '变更原因', exact: true })
    .fill('客户确认新的项目范围及三化预算');
  await form.getByRole('button', { name: '提交重大变更申请', exact: true }).click();
  await page.getByRole('link', { name: '查看申请', exact: true }).click();
  const table = page.getByRole('table', { name: '重大变更字段对照', exact: true });
  await expect(table.getByRole('row')).toHaveCount(5);
  await table
    .getByRole('columnheader', { name: '申请目标值', exact: true })
    .scrollIntoViewIfNeeded();
  await expect(table.getByRole('columnheader', { name: '申请目标值', exact: true })).toBeInViewport(
    { ratio: 1 },
  );
  await expect(table.getByRole('row').filter({ hasText: '网络化金额' })).toContainText(
    '50.00 万元',
  );
  await expect(table.getByRole('row').filter({ hasText: '网络化金额' })).toContainText(
    '80.00 万元',
  );
  const before = await get<{ projectName: string; totalAmount: number }>(
    request,
    ids.reporter,
    `/${project.id}`,
  );
  expect(before.projectName).toBe(projectName);
  expect(before.totalAmount).toBe(100);
  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, ids.leader);
  await page.getByRole('button', { name: '审批通过', exact: true }).click();
  await expect(page.getByText('审批已通过，项目正式数据已更新。', { exact: true })).toBeVisible();
  const after = await get<{
    projectName: string;
    totalAmount: number;
    categoryAmounts: { categoryCode: string; expectedAmount: number }[];
  }>(request, ids.reporter, `/${project.id}`);
  expect(after.projectName).toBe(newName);
  expect(after.totalAmount).toBe(150);
  expect(
    after.categoryAmounts.find((item) => item.categoryCode === 'NETWORK')?.expectedAmount,
  ).toBe(80);
  expect(after.categoryAmounts.find((item) => item.categoryCode === 'GEWU')?.expectedAmount).toBe(
    20,
  );
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('major-change-approved.png'), fullPage: true });
});
