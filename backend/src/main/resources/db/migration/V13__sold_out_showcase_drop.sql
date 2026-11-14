-- The demo's historical Coral Kip drop is shown as a sold-out past release rather than one that
-- ended with every unit unsold. No-op where the demo drop does not exist.
UPDATE drops d
SET available_stock = 0, locked_stock = 0, sold_stock = d.total_stock
FROM products p
WHERE p.id = d.product_id
  AND p.sku = 'SB-CORA-001'
  AND d.title = 'Sea Breeze: Coral Kip Plush Preview'
  AND d.end_time < now();
