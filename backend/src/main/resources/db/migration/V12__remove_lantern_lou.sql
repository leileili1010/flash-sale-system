-- Trim the Night Market series to Dumpling Dot and Neon Noodle. Lantern Lou was never scheduled,
-- so it has no drops; the guard keeps the migration safe if one was created by hand.
DELETE FROM products p
WHERE p.sku = 'NM-LANT-001'
  AND NOT EXISTS (SELECT 1 FROM drops d WHERE d.product_id = p.id);
