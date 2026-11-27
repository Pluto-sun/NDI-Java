import { expect, type Locator, type Page } from '@playwright/test';

export async function chooseListOptions(
  page: Page,
  label: string,
  values: string[],
  clear = false,
) {
  const trigger = page.getByRole('button', { name: label, exact: true });
  await trigger.click();
  const panel = page.getByRole('group', { name: label, exact: true });
  if (clear && (await panel.getByRole('button', { name: '清空已选' }).isEnabled()))
    await panel.getByRole('button', { name: '清空已选' }).click();
  const search = panel.getByRole('combobox', { name: '搜索' + label });
  for (const value of values) {
    await search.fill(value);
    const choice = panel.getByRole('checkbox', { name: value, exact: true });
    await expect(choice).toBeVisible();
    await choice.check();
  }
  await search.press('Escape');
  await expect(trigger).toHaveAttribute('aria-expanded', 'false');
}

export async function submitRecognition(dialog: Locator) {
  await dialog.getByRole('button', { name: '提交认定申请', exact: true }).click();
  await dialog.getByRole('button', { name: '确认提交', exact: true }).click();
}
