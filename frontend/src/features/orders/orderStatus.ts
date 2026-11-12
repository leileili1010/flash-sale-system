import type { Order } from '../../api/types';
import type { BadgeTone } from '../../components/Badge';

interface StatusPresentation {
  label: string;
  tone: BadgeTone;
  detail: string;
}

export function presentStatus(order: Order): StatusPresentation {
  switch (order.status) {
    case 'QUEUED':
      return { label: 'Creating order', tone: 'chip', detail: 'Your unit is reserved; the order is being created.' };
    case 'PENDING_PAYMENT':
      return { label: 'Pending payment', tone: 'pink', detail: 'Complete payment before the timer runs out.' };
    case 'PAID':
      return { label: 'Paid', tone: 'success', detail: 'It is yours. Thank you!' };
    case 'CLOSED':
      return { label: 'Expired', tone: 'warn', detail: 'Payment window passed; the unit went back to stock.' };
    case 'FAILED':
      return {
        label: 'Not completed',
        tone: 'neutral',
        detail: order.failureReason === 'DUPLICATE_PURCHASE'
          ? 'You already hold an order for this drop. Not charged.'
          : 'Sold out before your order was confirmed. Not charged.',
      };
  }
}
