import { expect, test } from '@playwright/test';
import { chooseActor, ids } from './support/projects';

test('项目筛选固定在视口内，高级六字段同排，表格独立滚动', async ({ page }, info) => {
  await page.goto('/projects');
  await chooseActor(page, ids.admin);
  for (const width of [1440, 1120]) {
    await page.setViewportSize({ width, height: 850 });
    await expect(page.getByRole('table', { name: '项目清单', exact: true })).toBeVisible();
    await page.getByText('更多筛选条件', { exact: true }).click();
    const fields = page.locator(
      '.project-advanced-filters .form-grid > label, .project-advanced-filters .form-grid > .multi-select-field',
    );
    await expect(fields).toHaveCount(6);
    const tops = await fields.evaluateAll((elements) =>
      elements.map((el) => Math.round(el.getBoundingClientRect().top)),
    );
    expect(new Set(tops).size).toBe(1);
    const filter = page.getByRole('form', { name: '项目筛选' });
    const top = (await filter.boundingBox())!.y;
    const scroller = page.locator('.project-list-panel .ledger-table-scroll');
    await scroller.evaluate((el) => {
      const body = el.querySelector('tbody')!;
      const row = body.querySelector('tr');
      if (row) for (let i = 0; i < 40; i++) body.append(row.cloneNode(true));
    });
    await scroller.hover();
    await page.mouse.wheel(0, 2500);
    await expect.poll(() => scroller.evaluate((el) => el.scrollTop)).toBeGreaterThan(0);
    expect((await filter.boundingBox())!.y).toBeCloseTo(top, 0);
    await expect(
      page.getByRole('navigation', { name: '项目列表分页', exact: true }),
    ).toBeInViewport();
    expect(
      await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight + 2),
    ).toBe(true);
    await page.screenshot({ path: info.outputPath('project-list-' + width + '.png') });
    await page.getByText('更多筛选条件', { exact: true }).click();
  }
});
