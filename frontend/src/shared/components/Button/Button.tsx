import type { LucideIcon } from 'lucide-react';
import { Loader2 } from 'lucide-react';
import { forwardRef } from 'react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import styles from './Button.module.css';

/**
 * ADAGS button primitive. Tokens only — see STYLE_GUIDE.md §2, §6, §8.
 *
 * @example Basic action
 * ```tsx
 * <Button onClick={handleSave}>Save changes</Button>
 * ```
 *
 * @example Secondary with a leading icon
 * ```tsx
 * <Button variant="secondary" leadingIcon={Plus} onClick={openForm}>
 *   Add patient
 * </Button>
 * ```
 *
 * @example Submitting form, loading state
 * ```tsx
 * <Button type="submit" loading={isSubmitting} leadingIcon={LogIn}>
 *   Sign in
 * </Button>
 * ```
 */

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

const ICON_SIZE = 16;

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Visual style. @default 'primary' */
  variant?: ButtonVariant;
  /** Sizing (padding + font-size, no raw heights). @default 'md' */
  size?: ButtonSize;
  /** Shows a spinner in place of the label, keeps width, forces disabled and aria-busy. */
  loading?: boolean;
  /** Rendered before the label, e.g. `Plus`. */
  leadingIcon?: LucideIcon;
  /** Rendered after the label, e.g. `ChevronRight`. */
  trailingIcon?: LucideIcon;
  children: ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    leadingIcon: LeadingIcon,
    trailingIcon: TrailingIcon,
    disabled,
    className,
    children,
    ...rest
  },
  ref,
) {
  const classes = [styles.root, styles[variant], styles[size], className].filter(Boolean).join(' ');
  const contentClasses = [styles.content, loading ? styles.contentHidden : ''].filter(Boolean).join(' ');

  return (
    <button
      ref={ref}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      <span className={contentClasses}>
        {LeadingIcon && <LeadingIcon size={ICON_SIZE} strokeWidth={2} className={styles.icon} aria-hidden="true" />}
        {children}
        {TrailingIcon && <TrailingIcon size={ICON_SIZE} strokeWidth={2} className={styles.icon} aria-hidden="true" />}
      </span>
      {loading && (
        <span className={styles.spinnerWrap} aria-hidden="true">
          <Loader2 size={ICON_SIZE} strokeWidth={2} className={styles.spinner} />
        </span>
      )}
    </button>
  );
});
