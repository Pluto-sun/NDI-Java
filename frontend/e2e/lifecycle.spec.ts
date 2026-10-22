import { randomUUID } from 'node:crypto';
import { test, expect, type Page } from '@playwright/test';
import { ids, get, chooseActor } from './support/projects';

const reviewer = '55555555-5555-4555-8555-555555555555';
const submittedLink = (page: Page) =>
  page.getByRole('status').getByRole('link', { name: '查看申请', exact: true });

async function switchActor(page: Page, actor: string) {
  await page.getByRole('button', { name: '切换测试账号', exact: true }).click();
  await chooseActor(page, actor);
}

async function approve(page: Page, actor: string) {
  await submittedLink(page).click();
  await expect(page).toHaveURL(/\/approvals\/[0-9a-f-]{36}$/);
  await switchActor(page, actor);
  await page.getByRole('button', { name: '审批通过', exact: true }).click();
  await expect(page.getByText('审批已通过，项目正式数据已更新。', { exact: true })).toBeVisible();
  await switchActor(page, ids.reporter);
}

test('浏览器创建、材料上传、三级推进、回退清合同与终止只读', async ({
  page,
  request,
}, testInfo) => {
  test.setTimeout(120_000);
  const name = `闭环-${randomUUID()}`;
  await page.goto('/projects/new');
  await chooseActor(page, ids.reporter);
  await page.getByRole('textbox', { name: '项目名称', exact: true }).fill(name);
  await page.getByRole('textbox', { name: '客户名称', exact: true }).fill(name);
  await page.getByRole('combobox', { name: '客户所属行业', exact: true }).selectOption('C25');
  await page.getByRole('spinbutton', { name: '项目总金额（万元）', exact: true }).fill('100');
  await page.getByRole('textbox', { name: '客户需求', exact: true }).fill('验证完整项目生命周期');
  await page.getByRole('combobox', { name: '省分负责人', exact: true }).selectOption(ids.leader);
  await page.getByRole('textbox', { name: '地市客户经理', exact: true }).fill('闭环验收经理');
  await page.getByRole('checkbox', { name: '网络化', exact: true }).check();
  await page.getByRole('spinbutton', { name: '预计金额（万元）', exact: true }).fill('50');
  await page.getByRole('textbox', { name: '项目进展', exact: true }).fill('客户需求已明确');
  await page.getByRole('textbox', { name: '下一步动作', exact: true }).fill('提交客户交流方案');
  await page.getByRole('button', { name: '创建项目', exact: true }).click();
  await expect(page).toHaveURL(/\/projects\/[0-9a-f-]{36}$/);
  const projectId = page.url().split('/').at(-1)!;
  const detail = () =>
    get<{ currentStageCode: string; contractNo: string | null; signedAmount: number | null }>(
      request,
      ids.reporter,
      `/${projectId}`,
    );

  async function advance(target: string, purpose: string, upload = true) {
    if (purpose && upload) {
      await page.getByRole('combobox', { name: '资料用途', exact: true }).selectOption(purpose);
      await page.getByLabel('选择文件', { exact: true }).setInputFiles({
        name: `${purpose}.pdf`,
        mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.7\n隔离验收材料\n%%EOF'),
      });
      await page.getByRole('button', { name: '上传资料', exact: true }).click();
      await expect(page.getByText('资料上传完成，可用于阶段推进。', { exact: true })).toBeVisible();
    }
    if (purpose) {
      const label = purpose === 'CUSTOMER_SOLUTION' ? '客户侧交流方案' : '中标通知书';
      await page
        .getByRole('combobox', { name: label, exact: true })
        .selectOption({ label: `${purpose}.pdf` });
    } else {
      await page.getByRole('textbox', { name: '合同编号', exact: true }).fill(`HT-${randomUUID()}`);
      await page.getByRole('spinbutton', { name: '签约金额（万元）', exact: true }).fill('80');
    }
    await page.getByRole('combobox', { name: '指定审批人', exact: true }).selectOption(reviewer);
    const before = await detail();
    const submitted = page.waitForResponse(
      (response) =>
        response.url().endsWith('/stage-advancements') && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: '提交阶段推进申请', exact: true }).click();
    const response = await submitted;
    expect(response.ok(), await response.text()).toBeTruthy();
    const application = (await response.json()).data;
    await expect(submittedLink(page)).toHaveAttribute('href', `/approvals/${application.id}`);
    expect((await detail()).currentStageCode).toBe(before.currentStageCode);
    await approve(page, reviewer);
    expect((await detail()).currentStageCode).toBe(target);
    await page.goto(`/projects/${projectId}`);
  }

  await advance('BIDDING', 'CUSTOMER_SOLUTION');
  await advance('SIGNED', 'AWARD_NOTICE');
  await advance('COLLECTION', '');

  async function changeStage(target: string) {
    const form = page.getByRole('form', { name: '重大变更申请', exact: true });
    await form.getByRole('checkbox', { name: '变更项目名称', exact: true }).uncheck();
    await form.getByRole('checkbox', { name: '变更项目阶段', exact: true }).check();
    await form.getByRole('combobox', { name: /^调整后项目阶段/ }).selectOption(target);
    await form
      .getByRole('combobox', { name: '重大变更审批人', exact: true })
      .selectOption(ids.leader);
    await form.getByRole('textbox', { name: '变更原因', exact: true }).fill('隔离验收阶段边界');
    await form.getByRole('button', { name: '提交重大变更申请', exact: true }).click();
    await approve(page, ids.leader);
    await page.goto(`/projects/${projectId}`);
  }

  expect((await detail()).signedAmount).toBe(80);
  await changeStage('BIDDING');
  expect(await detail()).toMatchObject({
    currentStageCode: 'BIDDING',
    contractNo: null,
    signedAmount: null,
  });
  await advance('SIGNED', 'AWARD_NOTICE', false);
  await advance('COLLECTION', '');
  const contracted = await detail();
  await changeStage('TERMINATED');
  expect(await detail()).toMatchObject({
    currentStageCode: 'TERMINATED',
    contractNo: contracted.contractNo,
    signedAmount: 80,
  });
  await expect(page.getByText('项目已终止，历史进展只读。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '追加进展', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '上传资料', exact: true })).toHaveCount(0);
  await expect(page.getByRole('form', { name: '重大变更申请', exact: true })).toHaveCount(0);
  await expect(page.locator('.project-history table').first().getByRole('row')).toHaveCount(8);
  await expect(page.locator('.project-history table').first()).toContainText('已通过');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy();
  await page.screenshot({ path: testInfo.outputPath('lifecycle-terminated.png'), fullPage: true });
});
