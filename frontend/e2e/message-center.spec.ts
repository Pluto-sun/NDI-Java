import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, ids, post } from './support/projects';

test('审批与消息按业务切换，当前分类刷新保留且没有多个列表堆叠', async ({
  page,
  request,
}, testInfo) => {
  const name = `消息排版-${randomUUID()}`;
  const project = await createProject(request, name);
  await post(request, ids.reporter, `/${project.id}/major-changes`, {
    changeType: 'TOTAL_AMOUNT',
    initiatorLevel: 'CITY',
    approverId: ids.leader,
    reason: '布局验收',
    totalAmount: '120.00',
  });
  await page.goto('/approvals');
  await chooseActor(page, ids.leader);
  const approvalTabs = page.getByRole('group', { name: '审批业务分类', exact: true });
  await expect(page.getByRole('row').filter({ hasText: name })).toBeVisible();
  await expect(page.getByRole('heading', { name: '计收审核待办', exact: true })).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('project-approvals.png'), fullPage: true });
  await approvalTabs.getByRole('button', { name: '计收审核', exact: true }).click();
  await expect(page.getByRole('heading', { name: '计收审核待办', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '项目审批列表', exact: true })).toHaveCount(0);
  await page.reload();
  await expect(approvalTabs.getByRole('button', { name: '计收审核', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.goto('/notifications');
  const tabs = page.getByRole('group', { name: '消息分类', exact: true });
  await expect(page.getByRole('region', { name: '审批通知列表', exact: true })).toBeVisible();
  const row = page.locator('.message-item').first();
  await expect(row.locator('.message-state')).toHaveText('未读');
  await expect(row.locator('time')).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath('approval-notifications.png'),
    fullPage: true,
  });
  await tabs.getByRole('button', { name: '项目督办', exact: true }).click();
  await expect(page.getByRole('heading', { name: '项目超时督办', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '审批通知列表', exact: true })).toHaveCount(0);
  await tabs.getByRole('button', { name: '计收通知', exact: true }).click();
  await expect(page.getByRole('heading', { name: '计收通知', exact: true })).toBeVisible();
  await page.reload();
  await expect(tabs.getByRole('button', { name: '计收通知', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});
