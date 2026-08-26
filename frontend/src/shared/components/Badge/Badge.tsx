import type { LucideIcon } from 'lucide-react';
import type { HTMLAttributes, ReactNode } from 'react';
import styles from './Badge.module.css';

/**
 * ADAGS badge primitive. Tokens only — see STYLE_GUIDE.md §2, §5, §6.
 * A plain, non-interactive `<span>` — no click handling or ARIA role is
 * added on your behalf. For a badge that reflects a value changing live
 * (e.g. an unread count), pass `role="status"` / `aria-live="polite"`
 * yourself; forcing that on every badge would make static tags noisy for
 * screen readers.
 *
 * @example Status tag
 * ```tsx
 * <Badge variant="success">Active</Badge>
 * ```
 *
 * @example With a leading icon
 * ```tsx
 * <Badge variant="danger" leadingIcon={AlertTriangle}>Overdue</Badge>
 * ```
 *
 * @example Dot indicator, no icon
 * ```tsx
 * <Badge variant="info" dot>3 waiting</Badge>
 * ```
 */

export type BadgeVariant = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';
export type BadgeSize = 'sm' | 'md' | 'lg';

const ICON_SIZE = 16;

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  /** Visual style. @default 'neutral' */
  variant?: BadgeVariant;
  /** Sizing (padding + font-size, no raw heights). @default 'md' */
  size?: BadgeSize;
  /** Rendered before the content. */
  leadingIcon?: LucideIcon;
  /** Rendered after the content. */
  trailingIcon?: LucideIcon;
  /** Renders a small status dot before the content, coloured to match the variant's text. */
  dot?: boolean;
  children?: ReactNode;
}

export function Badge({
  variant = 'neutral',
  size = 'md',
  leadingIcon: LeadingIcon,
  trailingIcon: TrailingIcon,
  dot = false,
  className,
  children,
  ...rest
}: BadgeProps) {
  const classes = [styles.root, styles[variant], styles[size], className].filter(Boolean).join(' ');

  return (
    <span className={classes} {...rest}>
      {dot && <span className={styles.dot} aria-hidden="true" />}
      {LeadingIcon && <LeadingIcon size={ICON_SIZE} strokeWidth={2} className={styles.icon} aria-hidden="true" />}
      {children}
      {TrailingIcon && <TrailingIcon size={ICON_SIZE} strokeWidth={2} className={styles.icon} aria-hidden="true" />}
    </span>
  );
}
