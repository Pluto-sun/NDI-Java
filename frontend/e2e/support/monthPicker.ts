import { expect, type Locator } from '@playwright/test';

export async function pickMonth(control: Locator, value: string) {
  await control.click();
  const panel = control.locator('..').getByRole('group');
  if (value === '') {
    await panel.getByRole('button', { name: '清空', exact: true }).click();
    return;
  }
  const [year, month] = value.split('-');
  await panel.getByRole('spinbutton', { name: '年份', exact: true }).fill(year);
  await panel.getByRole('button', { name: Number(month) + '月', exact: true }).click();
  await expect(control).toHaveText(value);
}
