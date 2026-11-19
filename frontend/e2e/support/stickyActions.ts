import { expect, type Locator } from '@playwright/test';

export async function verifyStickyAction(scroller: Locator, action: Locator) {
  const cell = action.locator('xpath=ancestor::td');
  await expect(cell).toHaveClass(/sticky-list-action/);
  for (const fraction of [0, 0.5, 1]) {
    const position = await scroller.evaluate((element, ratio) => {
      element.scrollLeft = (element.scrollWidth - element.clientWidth) * ratio;
      return element.scrollLeft;
    }, fraction);
    if (fraction > 0) expect(position).toBeGreaterThan(0);
    const region = (await scroller.boundingBox())!;
    const bounds = (await cell.boundingBox())!;
    expect(Math.abs(bounds.x + bounds.width - region.x - region.width)).toBeLessThanOrEqual(2);
    await action.click({ trial: true });
    expect(await scroller.evaluate((element) => element.scrollLeft)).toBe(position);
  }
}
