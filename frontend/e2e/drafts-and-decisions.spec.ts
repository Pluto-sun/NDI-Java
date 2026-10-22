import { randomUUID } from 'node:crypto';
import { test, expect, type Page } from '@playwright/test';
import { ids, chooseActor, createProject, get, post } from './support/projects';

async function confirmDialog(page: Page, accept: boolean, action: () => Promise<unknown>) {
  page.once('dialog', (dialog) => (accept ? dialog.accept() : dialog.dismiss()));
  await action();
}

async function switchActor(page: Page, actor: string) {
  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, actor);
}

test('较晚返回的初始化请求不覆盖驳回校验提示', async ({ page, request }) => {
  const name = `延迟-${randomUUID()}`;
  const project = await createProject(request, name);
  const approval = await post<{ id: string }>(
    request,
    ids.reporter,
    `/${project.id}/major-changes`,
    {
      changeType: 'PROJECT_NAME',
      initiatorLevel: 'CITY',
      approverId: ids.leader,
      reason: '验证过期响应隔离',
      projectName: `修改-${name}`,
    },
  );
  let release: (() => void) | undefined;
  let finished: (() => void) | undefined;
  const released = new Promise<void>((resolve) => {
    finished = resolve;
  });
  let count = 0;
  await page.route(`**/api/v1/projects/approvals/${approval.id}`, async (route) => {
    if (route.request().method() !== 'GET' || ++count !== 1) return route.continue();
    const response = await route.fetch();
    await new Promise<void>((resolve) => {
      release = resolve;
    });
    await route.fulfill({ response });
    finished!();
  });
  await page.goto(`/approvals/${approval.id}`);
  await chooseActor(page, ids.leader);
  await expect.poll(() => !!release).toBe(true);
  await page.getByRole('button', { name: '驳回申请', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('驳回必须填写审核意见。');
  release!();
  await released;
  await page.evaluate(
    () =>
      new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
      ),
  );
  await expect(page.getByRole('alert')).toHaveText('驳回必须填写审核意见。');
  expect(
    (await get<{ status: string }>(request, ids.reporter, `/approvals/${approval.id}`)).status,
  ).toBe('PENDING');
});

test('未填完整项目保存草稿、刷新载入、更新、本人隔离与确认删除', async ({ page, request }) => {
  const title = `草稿-${randomUUID()}`;
  await page.goto('/projects/new');
  await chooseActor(page, ids.reporter);
  const name = page.getByRole('textbox', { name: '项目名称', exact: true });
  await name.fill(title);
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await expect(page.getByText('草稿已保存，不会进入项目统计。', { exact: true })).toBeVisible();
  const mine = await get<{ items: { id: string; title: string }[] }>(
    request,
    ids.reporter,
    '/drafts',
  );
  const saved = mine.items.find((item) => item.title === title)!;
  expect(saved).toBeDefined();
  expect(
    (
      await get<{ totalElements: number }>(
        request,
        ids.reporter,
        `?projectName=${encodeURIComponent(title)}`,
      )
    ).totalElements,
  ).toBe(0);
  const others = await get<{ items: { id: string }[] }>(request, ids.leader, '/drafts');
  expect(others.items.some((item) => item.id === saved.id)).toBe(false);
  await page.reload();
  const row = page.locator('.draft-item').filter({ hasText: title });
  await expect(row).toBeVisible();
  await name.fill('尚未保存的内容');
  await confirmDialog(page, false, () =>
    row.getByRole('button', { name: '继续填写', exact: true }).click(),
  );
  await expect(name).toHaveValue('尚未保存的内容');
  await confirmDialog(page, true, () =>
    row.getByRole('button', { name: '继续填写', exact: true }).click(),
  );
  await expect(name).toHaveValue(title);
  await page.getByRole('textbox', { name: '客户需求', exact: true }).fill('更新后的草稿需求');
  await page.getByRole('button', { name: '更新草稿', exact: true }).click();
  await expect(page.getByText('草稿已保存，不会进入项目统计。', { exact: true })).toBeVisible();
  await page.reload();
  await confirmDialog(page, true, () =>
    row.getByRole('button', { name: '继续填写', exact: true }).click(),
  );
  await expect(page.getByRole('textbox', { name: '客户需求', exact: true })).toHaveValue(
    '更新后的草稿需求',
  );
  await confirmDialog(page, false, () =>
    row.getByRole('button', { name: '删除', exact: true }).click(),
  );
  await expect(row).toBeVisible();
  await confirmDialog(page, true, () =>
    row.getByRole('button', { name: '删除', exact: true }).click(),
  );
  await expect(row).toHaveCount(0);
  const remaining = await get<{ items: { id: string }[] }>(request, ids.reporter, '/drafts');
  expect(remaining.items.some((item) => item.id === saved.id)).toBe(false);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('撤销与驳回均不落主档，驳回意见必填且需新建申请', async ({ page, request }) => {
  const name = `决策-${randomUUID()}`;
  const project = await createProject(request, name);
  const before = await get<{ projectName: string; versionNo: number }>(
    request,
    ids.reporter,
    `/${project.id}`,
  );
  expect(typeof before.versionNo).toBe('number');
  await page.goto(`/projects/${project.id}`);
  await chooseActor(page, ids.reporter);
  async function submit() {
    await page.getByRole('button', { name: '发起重大变更', exact: true }).click();
    const form = page.getByRole('form', { name: '重大变更申请', exact: true });
    await form.getByRole('textbox', { name: /^调整后项目名称/ }).fill(`更名-${name}`);
    await form
      .getByRole('combobox', { name: '重大变更审批人', exact: true })
      .selectOption(ids.leader);
    await form.getByRole('textbox', { name: '变更原因', exact: true }).fill('隔离验收撤销和驳回');
    await form.getByRole('button', { name: '提交重大变更申请', exact: true }).click();
    await page.getByRole('status').getByRole('link', { name: '查看申请', exact: true }).click();
    await expect(page).toHaveURL(/\/approvals\/[0-9a-f-]{36}$/);
    return page.url().split('/').at(-1)!;
  }
  const withdrawnId = await submit();
  await confirmDialog(page, false, () =>
    page.getByRole('button', { name: '撤销申请', exact: true }).click(),
  );
  await expect(page.getByRole('button', { name: '撤销申请', exact: true })).toBeVisible();
  await confirmDialog(page, true, () =>
    page.getByRole('button', { name: '撤销申请', exact: true }).click(),
  );
  await expect(page.getByText('申请已撤销。', { exact: true })).toBeVisible();
  expect(
    (await get<{ status: string }>(request, ids.reporter, `/approvals/${withdrawnId}`)).status,
  ).toBe('WITHDRAWN');
  await page.goto(`/projects/${project.id}`);
  const rejectedId = await submit();
  expect(rejectedId).not.toBe(withdrawnId);
  await switchActor(page, ids.leader);
  await page.getByRole('button', { name: '驳回申请', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('驳回必须填写审核意见。');
  expect(
    (await get<{ status: string }>(request, ids.reporter, `/approvals/${rejectedId}`)).status,
  ).toBe('PENDING');
  await page
    .getByRole('textbox', { name: '审核意见（通过时可选，驳回时必填）', exact: true })
    .fill('客户名称尚未正式确认');
  await page.getByRole('button', { name: '驳回申请', exact: true }).click();
  await expect(page.getByText('申请已驳回，项目正式数据未变化。', { exact: true })).toBeVisible();
  expect(await get(request, ids.reporter, `/approvals/${rejectedId}`)).toMatchObject({
    status: 'REJECTED',
    reviewOpinion: '客户名称尚未正式确认',
  });
  await expect(page.getByRole('button', { name: '审批通过', exact: true })).toHaveCount(0);
  const after = await get<{ projectName: string; versionNo: number }>(
    request,
    ids.reporter,
    `/${project.id}`,
  );
  expect(after.projectName).toBe(before.projectName);
  expect(after.versionNo).toBe(before.versionNo);
  await switchActor(page, ids.reporter);
  await page.goto(`/projects/${project.id}`);
  const nextId = await submit();
  expect(nextId).not.toBe(rejectedId);
  await confirmDialog(page, true, () =>
    page.getByRole('button', { name: '撤销申请', exact: true }).click(),
  );
  await expect(page.getByText('申请已撤销。', { exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});
