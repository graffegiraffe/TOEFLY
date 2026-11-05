import { cva, type VariantProps } from 'class-variance-authority';
import type { ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

// Button of docs/07-ux-ui.md §3.1: the gradient primary is identical on web and iOS.
const buttonVariants = cva(
  'inline-flex h-11 items-center justify-center gap-2 rounded-lg px-5 text-body font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring disabled:cursor-not-allowed disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-button-gradient text-white shadow-button hover:bg-button-gradient-hover',
        secondary: 'bg-accent-soft text-accent-on-soft hover:bg-brand-100 dark:hover:bg-accent-soft/80',
        ghost: 'text-ink-muted hover:bg-surface-sunken hover:text-ink',
        destructive: 'bg-danger-solid text-white hover:opacity-90',
      },
      block: { true: 'w-full' },
    },
    defaultVariants: { variant: 'primary' },
  },
);

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof buttonVariants>;

export function Button({ className, variant, block, type = 'button', ...props }: ButtonProps) {
  return <button type={type} className={cn(buttonVariants({ variant, block }), className)} {...props} />;
}

export function buttonClass(variant: ButtonProps['variant'] = 'primary', block = false) {
  return buttonVariants({ variant, block });
}
