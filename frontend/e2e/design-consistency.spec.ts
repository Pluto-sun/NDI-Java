import { expect, test } from '@playwright/test';
import { chooseActor, ids } from './support/projects';

test('项目创建卡片对齐，主要操作和返回按钮统一，侧栏保留创建入口', async ({ page }, info) => {
  await page.goto('/projects');
  await chooseActor(page, ids.reporter);
  const nav = page.getByRole('navigation', { name: '模块导航' });
  await expect(page.getByRole('main').getByRole('link', { name: '新建项目' })).toHaveCount(0);
  await nav.getByRole('link', { name: '项目创建', exact: true }).click();
  const main = page.getByRole('main');
  const drafts = main.getByRole('region', { name: '我的项目草稿' });
  const basics = main.getByRole('form', { name: '基本信息' });
  await expect(drafts).toBeVisible();
  await expect(basics).toBeVisible();
  const boxes = await Promise.all([drafts.boundingBox(), basics.boundingBox()]);
  expect(boxes[0]!.x).toBe(boxes[1]!.x);
  expect(boxes[0]!.width).toBe(boxes[1]!.width);
  if (page.viewportSize()!.width > 620) {
    const owner = await basics.getByRole('textbox', { name: '项目负责人' }).boundingBox();
    const subject = await basics.getByRole('combobox', { name: '签约主体' }).boundingBox();
    expect(owner!.y).toBeCloseTo(subject!.y, 0);
    expect(owner!.width).toBeCloseTo(subject!.width, 0);
  }

  const create = basics.getByRole('button', { name: '创建项目' });
  const save = basics.getByRole('button', { name: '保存草稿' });
  const back = main.getByRole('link', { name: '返回列表' });
  await expect(create).toHaveCSS('background-color', 'rgb(216, 39, 53)');
  await expect(save).toHaveCSS('background-color', 'rgb(255, 255, 255)');
  for (const action of [create, save, back]) await expect(action).toHaveCSS('height', '36px');
  await page.screenshot({ path: info.outputPath('project-create.png'), fullPage: true });
  await back.click();
  await expect(page).toHaveURL('/projects');
});

const routes = [
  '/workbench',
  '/workbench/revenue',
  '/workbench/scheduling',
  '/workbench/capability',
  '/projects',
  '/projects/new',
  '/projects/import',
  '/revenue',
  '/revenue/new',
  '/revenue/import',
  '/scheduling',
  '/scheduling/new',
  '/scheduling/import',
  '/capability/gewu',
  '/capability/gewu/import',
  '/capability/wangwu',
  '/capability/wangwu/import',
  '/capability/agents-live',
  '/capability/agents-live/import',
  '/capability/agents-development',
  '/capability/agents-development/import',
  '/approvals',
  '/notifications',
  '/account',
  '/admin/accounts',
  '/admin/reviewer-provinces',
  '/admin/inactivity',
  '/admin/backups',
  '/admin/historical-snapshots',
  '/admin/historical-snapshots/import',
];

test('各模块常用页面在桌面与窄屏保持内容宽度', async ({ page }) => {
  test.setTimeout(120_000);
  await page.goto('/workbench');
  await chooseActor(page, ids.admin);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  for (const route of routes) {
    await page.goto(route);
    await expect(page.locator('.topbar h1'), route).toBeVisible();
    const content = page.locator('#main-content');
    await expect(content, route).not.toBeEmpty();
    await expect(content.getByText('正在加载…', { exact: true }), route).toHaveCount(0);
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth), {
        message: route,
      })
      .toBeLessThanOrEqual(1);
  }
  expect(errors).toEqual([]);
});
