import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, ids, post } from './support/projects';

test('审批记录保留已办结果、支持状态筛选及重试，管理员查看不获得审批权', async ({
  page,
  request,
}, testInfo) => {
  const name = `记录-${randomUUID()}`;
  const project = await createProject(request, name);
  const approval = await post<{ id: string }>(
    request,
    ids.reporter,
    `/${project.id}/major-changes`,
    {
      changeType: 'PROJECT_NAME',
      initiatorLevel: 'CITY',
      approverId: ids.leader,
      reason: '审批记录复现',
      projectName: `${name}-调整`,
    },
  );
  await post(request, ids.leader, `/approvals/${approval.id}/decision`, {
    approved: false,
    opinion: '记录验收驳回',
  });
  await page.goto('/approvals');
  await chooseActor(page, ids.leader);
  await page.getByRole('button', { name: '可见审批记录', exact: true }).click();
  await page.getByRole('combobox', { name: '审批记录状态', exact: true }).selectOption('REJECTED');
  const row = page.getByRole('row').filter({ hasText: name });
  await expect(row).toContainText('已驳回');
  await expect(row).toContainText('记录验收驳回');
  await page.route('**/approvals/records?*', (route) => route.fulfill({ status: 503, body: '{}' }));
  await page.getByRole('combobox', { name: '审批记录状态', exact: true }).selectOption('APPROVED');
  await expect(page.getByRole('button', { name: '重新加载审批列表', exact: true })).toBeVisible();
  await page.unroute('**/approvals/records?*');
  await page.getByRole('button', { name: '重新加载审批列表', exact: true }).click();
  await page.getByRole('combobox', { name: '审批记录状态', exact: true }).selectOption('REJECTED');
  await row.getByRole('link', { name: '查看申请', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/approvals/${approval.id}$`));
  await expect(page.getByRole('heading', { name: '申请详情', exact: true })).toBeVisible();
  await expect(page.getByText('记录验收驳回', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, ids.admin);
  await expect(page.getByText('记录验收驳回', { exact: true })).toBeVisible();
  const pending = await post<{ id: string }>(
    request,
    ids.reporter,
    `/${project.id}/major-changes`,
    {
      changeType: 'PROJECT_NAME',
      initiatorLevel: 'CITY',
      approverId: ids.leader,
      reason: '验证管理员只读',
      projectName: `${name}-再次调整`,
    },
  );
  await page.goto(`/approvals/${pending.id}`);
  await expect(page.getByRole('heading', { name: '申请详情', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '审批通过', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '驳回申请', exact: true })).toHaveCount(0);
  const denied = await request.post(
    `http://127.0.0.1:18080/api/v1/projects/approvals/${pending.id}/decision`,
    {
      headers: { 'X-NDI-Actor-Id': ids.admin },
      data: { approved: true },
    },
  );
  expect(denied.status()).toBe(403);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath('approval-records-readonly.png'),
    fullPage: true,
  });
});
