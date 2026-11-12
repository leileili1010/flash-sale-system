import { expect, test } from '@playwright/test';

test('a new shopper buys a live drop, pays, and sees the order', async ({ page }, testInfo) => {
  const email = `e2e-${testInfo.project.name}-${Date.now()}@example.com`;

  // Register a fresh account so the one-per-customer rule never blocks a rerun.
  await page.goto('/login');
  await page.getByRole('tab', { name: 'Create account' }).click();
  await page.getByLabel('Display name').fill('E2E Shopper');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('e2e-password-123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('E2E Shopper')).toBeVisible();

  // Pick a live drop from the home page.
  const live = page.getByRole('region', { name: /Live now/ });
  await live.getByRole('link', { name: 'Buy now' }).first().click();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  const dropUrl = page.url();
  await page.screenshot({ path: testInfo.outputPath('drop-detail.png'), fullPage: true });

  // Reserve: Redis takes the unit, Kafka carries the order, the consumer persists it.
  await page.getByRole('button', { name: 'Buy now' }).click();
  await expect(page).toHaveURL(/\/checkout\/\d+$/);
  await expect(page.getByRole('heading', { name: 'You got one!' })).toBeVisible();
  await expect(page.getByRole('timer')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('checkout.png'), fullPage: true });

  await page.getByRole('button', { name: /^Pay \$/ }).click();
  await expect(page.getByRole('heading', { name: 'Payment received' })).toBeVisible();

  await page.getByRole('link', { name: 'View my orders' }).click();
  await expect(page.getByRole('heading', { name: 'My orders' })).toBeVisible();
  await expect(page.getByRole('listitem').first().getByText('Paid')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('orders.png'), fullPage: true });

  // Going back to the drop shows the shopper already owns it.
  await page.goto(dropUrl);
  await expect(page.getByRole('link', { name: 'You own this · View order' })).toBeVisible();
});

test('signed-out visitors are sent to sign in before buying', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('region', { name: /Live now/ }).getByRole('link', { name: 'Buy now' }).first().click();
  await page.getByRole('button', { name: 'Sign in to buy' }).click();
  await expect(page).toHaveURL(/\/login\?next=%2Fdrops%2F\d+$/);
});
