-- Point the seed catalog at the product artwork served by the frontend (frontend/public/images/products).
-- Products without artwork yet keep a path the frontend renders with a drawn placeholder.

UPDATE products SET image_url = '/images/products/mochi-moss.png'       WHERE sku = 'FF-MOSS-001';
UPDATE products SET image_url = '/images/products/pip-thistle.png'      WHERE sku = 'FF-THIS-002';
UPDATE products SET image_url = '/images/products/bramble-bun.png'      WHERE sku = 'FF-BRAM-003';
UPDATE products SET image_url = '/images/products/juniper-snooze.png'   WHERE sku = 'FF-JUNI-004';
UPDATE products SET image_url = '/images/products/lantern-lou.png'      WHERE sku = 'NM-LANT-001';
UPDATE products SET image_url = '/images/products/dumpling-dot.png'     WHERE sku = 'NM-DUMP-002';
UPDATE products SET image_url = '/images/products/neon-noodle.png'      WHERE sku = 'NM-NOOD-003';
UPDATE products SET image_url = '/images/products/coral-kip.png'        WHERE sku = 'SB-CORA-001';
UPDATE products SET image_url = '/images/products/tide-pebble.png'      WHERE sku = 'SB-TIDE-002';
UPDATE products SET image_url = '/images/products/mochi-moss-grand.png' WHERE sku = 'FF-MOSS-400';
