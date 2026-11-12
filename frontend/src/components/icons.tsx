import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Stroke({ size = 18, children, ...props }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>
      {children}
    </svg>
  );
}

export const CartIcon = (p: IconProps) => (
  <Stroke {...p}><circle cx="9" cy="20" r="1.5" /><circle cx="18" cy="20" r="1.5" /><path d="M2 3h3l2.6 12.4a2 2 0 0 0 2 1.6h8.2a2 2 0 0 0 2-1.5L22 8H6" /></Stroke>
);
export const BellIcon = (p: IconProps) => (
  <Stroke {...p}><path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" /></Stroke>
);
export const ClockIcon = (p: IconProps) => (
  <Stroke {...p}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></Stroke>
);
export const ChevronRightIcon = (p: IconProps) => <Stroke strokeWidth={2.5} {...p}><path d="M9 6l6 6-6 6" /></Stroke>;
export const CardIcon = (p: IconProps) => <Stroke {...p}><rect x="2" y="5" width="20" height="14" rx="3" /><path d="M2 10h20" /></Stroke>;
export const UserIcon = (p: IconProps) => <Stroke {...p}><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></Stroke>;
export const CheckIcon = (p: IconProps) => <Stroke strokeWidth={2.5} {...p}><path d="M5 12l5 5L20 7" /></Stroke>;
export const SpinnerIcon = (p: IconProps) => (
  <Stroke strokeWidth={2.5} className="animate-spin" {...p}><path d="M21 12a9 9 0 1 1-6.2-8.6" /></Stroke>
);

export function SparkleIcon({ size = 22, color = '#FFC94D', ...props }: IconProps & { color?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M12 2l2 8 8 2-8 2-2 8-2-8-8-2 8-2z" fill={color} />
    </svg>
  );
}

export function StarIcon({ size = 14, ...props }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M12 2l2.9 6.6 7.1.6-5.4 4.7 1.6 7L12 17.3 5.8 20.9l1.6-7L2 9.2l7.1-.6z" fill="#FFD25E" />
    </svg>
  );
}

export function FlameIcon({ size = 24, ...props }: IconProps) {
  return (
    <svg width={size} height={size + 2} viewBox="0 0 24 26" aria-hidden="true" {...props}>
      <path d="M12 1c1 5 7 7 7 14a7 7 0 0 1-14 0c0-4 2-6 3-8 0 3 1 4 2 5 0-5 1-8 2-11z" fill="#FF6A2B" />
      <path d="M12 13c1 2 3 3 3 6a3 3 0 0 1-6 0c0-2 1-3 1.5-4 .5 1 1 1.5 1.5 1.5 0-1.5 0-2.5 0-3.5z" fill="#FFC23D" />
    </svg>
  );
}

export function RocketIcon({ size = 28, ...props }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M14 3c4-1 7 0 7 0s1 3 0 7l-7 7-7-7z" fill="#8FB4FF" />
      <circle cx="15.5" cy="8.5" r="2" fill="#FFFFFF" />
      <path d="M7 10l-4 1 3 3zM14 17l-1 4-3-3z" fill="#FF6F9C" />
      <path d="M6 15c-2 1-3 4-3 6 2 0 5-1 6-3z" fill="#FFB547" />
    </svg>
  );
}

export function BulbIcon({ size = 22, ...props }: IconProps) {
  return (
    <svg width={size} height={size * 1.27} viewBox="0 0 22 28" aria-hidden="true" {...props}>
      <path d="M11 1a9 9 0 0 0-5 16.5V21h10v-3.5A9 9 0 0 0 11 1z" fill="#FFD25E" />
      <rect x="6" y="22" width="10" height="2.5" rx="1.2" fill="#C9A23A" />
      <rect x="7.5" y="25.5" width="7" height="2" rx="1" fill="#C9A23A" />
    </svg>
  );
}
