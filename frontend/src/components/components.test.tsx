import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Countdown } from './Countdown';
import { ProductImage } from './ProductImage';
import { StockMeter } from './StockMeter';
import { fireEvent } from '@testing-library/react';

describe('StockMeter', () => {
  it('shows remaining stock and exposes it to assistive technology', () => {
    render(<StockMeter available={37} total={100} />);
    expect(screen.getByText('37')).toBeInTheDocument();
    expect(screen.getByText(/of 100 left/)).toBeInTheDocument();
    const meter = screen.getByRole('meter', { name: 'Stock remaining' });
    expect(meter).toHaveAttribute('aria-valuenow', '37');
  });
});

describe('Countdown', () => {
  it('renders hours, minutes and seconds with a spoken label', () => {
    render(<Countdown remainingMs={(1 * 60 + 58) * 1000} label="Starts in" />);
    const timer = screen.getByRole('timer');
    expect(timer).toHaveAccessibleName('Starts in 0 hours 1 minutes 58 seconds');
    expect(screen.getByText('58')).toBeInTheDocument();
  });
});

describe('ProductImage', () => {
  it('falls back to a drawn placeholder when the image fails to load', () => {
    render(<ProductImage src="/images/products/missing.png" alt="Lantern Lou" />);
    fireEvent.error(screen.getByRole('img', { name: 'Lantern Lou' }));
    const placeholder = screen.getByRole('img', { name: 'Lantern Lou' });
    expect(placeholder.tagName).toBe('DIV');
  });
});
