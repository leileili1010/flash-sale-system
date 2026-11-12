import { expect, test } from '@playwright/test';

test('an admin schedules a drop as a draft, publishes it, and shoppers can see it', async ({ page }, testInfo) => {
  const title = `E2E Pip Thistle Encore ${testInfo.project.name} ${Date.now()}`;

  await page.goto('/login?next=%2Fadmin');
  await page.getByLabel('Email').fill('admin@fluffle.dev');
  await page.getByLabel('Password').fill('admin-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Admin console' })).toBeVisible();

  await page.getByRole('tab', { name: 'New drop' }).click();
  const pip = await page.getByRole('option', { name: /Pip Thistle/ }).getAttribute('value');
  await page.getByLabel('Product').selectOption(pip!);
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Units').fill('15');
  await page.getByLabel(/Publish now/).uncheck();
  await page.screenshot({ path: testInfo.outputPath('admin-new-drop.png'), fullPage: true });
  await page.getByRole('button', { name: 'Save draft' }).click();

  await expect(page.getByText(`Draft “${title}” saved.`)).toBeVisible();
  const row = page.getByRole('row').filter({ hasText: title });
  await expect(row.getByText('DRAFT')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('admin-drops.png'), fullPage: true });

  // Drafts are invisible to shoppers until published.
  const shopper = await page.context().browser()!.newPage();
  await shopper.goto('/drops?phase=upcoming');
  await expect(shopper.getByRole('heading', { name: 'All drops' })).toBeVisible();
  await expect(shopper.getByText(title)).toHaveCount(0);

  await row.getByRole('button', { name: 'Publish' }).click();
  await expect(page.getByText(`“${title}” is now published.`)).toBeVisible();

  await shopper.reload();
  await expect(shopper.getByText(title)).toBeVisible();
  await shopper.close();

  await page.getByRole('tab', { name: 'Inventory health' }).click();
  await expect(page.getByRole('row').filter({ hasText: title })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('admin-inventory.png'), fullPage: true });

  // Clean up: cancelling the not-yet-started drop removes it from the shop again.
  await page.getByRole('tab', { name: 'Drops' }).click();
  const published = page.getByRole('row').filter({ hasText: title });
  await published.getByRole('button', { name: 'Cancel' }).click();
  await published.getByRole('button', { name: 'Confirm cancel' }).click();
  await expect(published.getByText('CANCELLED')).toBeVisible();
});
