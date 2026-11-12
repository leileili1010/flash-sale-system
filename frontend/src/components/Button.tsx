import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router';

export type ButtonVariant = 'primary' | 'hero' | 'ghostLight' | 'outline' | 'dark' | 'muted' | 'warn' | 'success';
export type ButtonSize = 'md' | 'lg';

const base =
  'inline-flex items-center justify-center gap-2.5 rounded-full font-bold transition ' +
  'disabled:cursor-not-allowed disabled:opacity-100';

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-cta text-white shadow-cta hover:brightness-105 active:brightness-95',
  hero: 'bg-cta-light text-navy-950 shadow-[0_8px_22px_rgb(255_111_170/0.45)] hover:brightness-105',
  ghostLight: 'border-[1.5px] border-white/75 text-white hover:bg-white/10',
  outline: 'border-[1.5px] border-ink text-ink hover:bg-ink/5',
  dark: 'bg-ink text-white hover:bg-navy-900',
  muted: 'bg-track text-muted',
  warn: 'bg-warn-bg text-warn-ink',
  success: 'bg-[linear-gradient(180deg,#1BB57A,#0E8F5E)] text-white shadow-[0_6px_16px_rgb(14_143_94/0.3)] hover:brightness-105',
};

const sizes: Record<ButtonSize, string> = {
  md: 'min-h-12 px-6 text-base',
  lg: 'min-h-15 px-8 text-lg',
};

export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', extra = '') {
  return [base, variants[variant], sizes[size], extra].filter(Boolean).join(' ');
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  icon?: ReactNode;
}

export function Button({ variant, size, icon, className = '', children, type = 'button', ...props }: ButtonProps) {
  return (
    <button type={type} className={buttonClasses(variant, size, className)} {...props}>
      {icon}
      {children}
    </button>
  );
}

interface ButtonLinkProps extends LinkProps {
  variant?: ButtonVariant;
  size?: ButtonSize;
  icon?: ReactNode;
}

export function ButtonLink({ variant, size, icon, className = '', children, ...props }: ButtonLinkProps) {
  return (
    <Link className={buttonClasses(variant, size, typeof className === 'string' ? className : '')} {...props}>
      {icon}
      {children}
    </Link>
  );
}
