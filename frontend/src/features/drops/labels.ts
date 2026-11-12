import type { ProductType } from '../../api/types';

const productTypes: Record<ProductType, string> = {
  BLIND_BOX: 'Blind Box',
  FIGURE: 'Figure',
  PLUSH: 'Plush',
};

export function productTypeLabel(type: ProductType): string {
  return productTypes[type];
}

/**
 * Drop titles often repeat the series ("Night Market: Neon Noodle Midnight Drop"). The series is
 * shown separately in the UI, so drop the redundant prefix.
 */
export function displayTitle(title: string, series: string): string {
  const prefix = `${series}:`;
  return title.startsWith(prefix) ? title.slice(prefix.length).trim() : title;
}
