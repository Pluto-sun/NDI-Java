import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { chooseActor, createProject, get, ids, post } from './support/projects';

test('普通操作审计含操作人、分页、审批与撤销事实，并支持错误恢复', async ({
  page,
  request,
}, testInfo) => {
  const project = await createProject(request, `审计-${randomUUID()}`);
  for (let index = 0; index < 21; index++)
    await post(request, ids.reporter, `/${project.id}/progress`, {
      progressContent: `审计进展-${index}`,
      nextAction: '继续跟进',
    });
  const submitted = await post<{ id: string }>(
    request,
    ids.reporter,
    `/${project.id}/major-changes`,
    {
      changeType: 'PROJECT_NAME',
      initiatorLevel: 'CITY',
      approverId: ids.leader,
      reason: '核对审计',
      projectName: `待审-${randomUUID()}`,
    },
  );
  await post(request, ids.reporter, `/approvals/${submitted.id}/withdraw`, {});
  const reporter = await get<{ displayName: string }>(request, ids.reporter, '/me');
  await page.goto(`/projects/${project.id}`);
  await chooseActor(page, ids.reporter);
  const panel = page.getByRole('region', { name: '项目操作审计', exact: true });
  await expect(panel.getByRole('cell', { name: '撤销审批申请', exact: true })).toBeVisible();
  await expect(panel.getByRole('cell', { name: '提交重大变更申请', exact: true })).toBeVisible();
  await expect(panel.getByRole('cell', { name: reporter.displayName, exact: true })).toHaveCount(
    20,
  );
  await panel.getByRole('button', { name: '下一页操作', exact: true }).click();
  await expect(panel.getByRole('cell', { name: '创建项目', exact: true })).toBeVisible();
  await expect(panel.getByRole('cell', { name: reporter.displayName, exact: true })).toHaveCount(4);
  await page.route(`**/api/v1/projects/${project.id}/operation-audit?*`, (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: '操作审计暂不可用' }),
    }),
  );
  await panel.getByRole('button', { name: '上一页操作', exact: true }).click();
  await expect(panel.getByRole('alert')).toContainText('操作审计暂不可用');
  await page.unroute(`**/api/v1/projects/${project.id}/operation-audit?*`);
  await panel.getByRole('button', { name: '重试操作审计', exact: true }).click();
  await expect(panel.getByRole('cell', { name: '撤销审批申请', exact: true })).toBeVisible();
  await expect(panel.getByRole('alert')).toHaveCount(0);
  expect(
    await panel
      .locator('.table-scroll')
      .evaluate((element) => element.scrollWidth <= element.clientWidth),
  ).toBeTruthy();
  await panel.screenshot({ path: testInfo.outputPath('operation-audit.png') });
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
});
