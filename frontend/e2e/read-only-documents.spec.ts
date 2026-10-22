import { createHash, randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { chooseActor, createProject, get, ids } from './support/projects';

test('全国只读角色查看和下载真实资料，但无上传删除及业务维护权限', async ({ page, request }) => {
  const project = await createProject(request, `只读资料-${randomUUID()}`);
  const bytes = Buffer.from('%PDF-1.7\n只读角色隔离验收\n%%EOF');
  await page.clock.setFixedTime(new Date('2026-10-31T12:00:00Z'));
  await page.goto(`/projects/${project.id}`);
  await chooseActor(page, ids.reporter);
  await page
    .getByLabel('选择文件', { exact: true })
    .setInputFiles({ name: '只读验收.pdf', mimeType: 'application/pdf', buffer: bytes });
  await page.getByRole('button', { name: '上传资料', exact: true }).click();
  await expect(page.getByText('资料上传完成，可用于阶段推进。', { exact: true })).toBeVisible();
  const documents = await get<{ id: string }[]>(request, ids.reporter, `/${project.id}/documents`);
  const documentId = documents[0].id;
  const group = '77777777-7777-4777-8777-777777777777';
  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, group);
  await expect(page.getByLabel('月份', { exact: true })).toHaveValue('2026-09');
  await expect(page.getByRole('heading', { name: '项目资料', exact: true })).toBeVisible();
  await expect(page.getByText('只读验收.pdf', { exact: true })).toBeVisible();
  await expect(
    page.getByText('当前账号或项目状态为只读，可查看和下载已有资料。', { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: '上传资料', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^删除资料/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '追加进展', exact: true })).toHaveCount(0);
  await expect(page.getByRole('form', { name: '重大变更申请', exact: true })).toHaveCount(0);
  const signEvent = page.waitForResponse((response) =>
    response.url().endsWith(`/documents/${documentId}/download-url`),
  );
  await page.getByRole('button', { name: '下载', exact: true }).click();
  const signed = await signEvent;
  expect(signed.ok(), await signed.text()).toBeTruthy();
  const download = await request.get((await signed.json()).data.downloadUrl);
  expect(download.ok()).toBeTruthy();
  expect(await download.body()).toEqual(bytes);
  const root = `http://127.0.0.1:18080/api/v1/projects/${project.id}/documents`;
  const headers = { 'X-NDI-Actor-Id': group };
  expect((await request.delete(`${root}/${documentId}`, { headers })).status()).toBe(403);
  expect(
    (
      await request.post(`${root}/uploads`, {
        headers,
        data: {
          purpose: 'GENERAL',
          originalName: '越权.pdf',
          mediaType: 'application/pdf',
          byteSize: bytes.length,
          sha256Hex: createHash('sha256').update(bytes).digest('hex'),
        },
      })
    ).status(),
  ).toBe(403);
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
});
