import type { LucideIcon } from 'lucide-react';
import { forwardRef } from 'react';
import type { HTMLAttributes, KeyboardEvent, MouseEvent, ReactNode } from 'react';
import styles from './Card.module.css';

/**
 * ADAGS card primitive. Tokens only — see STYLE_GUIDE.md §2, §5, §8.
 * Always a plain container (`div`/`article`/`section`, never a real button
 * or link) — `variant="interactive"` is the only case that adds
 * `role="button"`, keyboard support, and hover/active/focus styling.
 * `trailingAction` renders inside that same clickable region on an
 * interactive card, so keep it decorative (an icon, a `Badge`) rather than
 * a focusable element — a real nested `<button>` inside a `role="button"`
 * card is invalid and creates overlapping interactive targets.
 *
 * @example Static content card
 * ```tsx
 * <Card header="Today's admissions" footer={<Button size="sm">View all</Button>}>
 *   <p>14 patients admitted since midnight.</p>
 * </Card>
 * ```
 *
 * @example Clickable card (keyboard-operable)
 * ```tsx
 * <Card variant="interactive" onClick={openPatient} leadingIcon={User} header="Amina Hassan" trailingAction={<ChevronRight size={16} />}>
 *   Ward A1 · Admitted 2 days ago
 * </Card>
 * ```
 *
 * @example Elevated summary tile
 * ```tsx
 * <Card variant="elevated" padding="lg">
 *   <Stat label="Outstanding balance" value="240,000/=" />
 * </Card>
 * ```
 */

export type CardVariant = 'default' | 'elevated' | 'outlined' | 'interactive';
export type CardPadding = 'sm' | 'md' | 'lg';
export type CardElement = 'div' | 'article' | 'section';

const ICON_SIZE = 20;

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Visual style. @default 'default'. Only 'interactive' gets button-like behaviour. */
  variant?: CardVariant;
  /** Outer padding (STYLE_GUIDE §4's own rhythm: sm=panel, md=card, lg=spacious). @default 'md' */
  padding?: CardPadding;
  /** Semantic tag for the container. @default 'div' */
  as?: CardElement;
  header?: ReactNode;
  footer?: ReactNode;
  /** Rendered at 20px (heading-context icon size, STYLE_GUIDE §5), beside the header. */
  leadingIcon?: LucideIcon;
  /** Decorative only on an interactive card — see the primitive's top-level note. */
  trailingAction?: ReactNode;
  /** The card body. */
  children?: ReactNode;
}

export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  {
    variant = 'default',
    padding = 'md',
    as: Tag = 'div',
    header,
    footer,
    leadingIcon: LeadingIcon,
    trailingAction,
    children,
    className,
    role,
    tabIndex,
    onKeyDown,
    onClick,
    ...rest
  },
  ref,
) {
  const isInteractive = variant === 'interactive';

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    onKeyDown?.(event);
    if (!isInteractive || event.defaultPrevented) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick?.(event as unknown as MouseEvent<HTMLDivElement>);
    }
  };

  const classes = [styles.root, styles[variant], styles[padding], className].filter(Boolean).join(' ');

  return (
    <Tag
      ref={ref as never}
      className={classes}
      role={isInteractive ? (role ?? 'button') : role}
      tabIndex={isInteractive ? (tabIndex ?? 0) : tabIndex}
      onKeyDown={isInteractive ? handleKeyDown : onKeyDown}
      onClick={onClick}
      {...rest}
    >
      {(LeadingIcon || header || trailingAction) && (
        <div className={styles.head}>
          {LeadingIcon && (
            <LeadingIcon size={ICON_SIZE} strokeWidth={2} className={styles.leadingIcon} aria-hidden="true" />
          )}
          {header && <div className={styles.headerContent}>{header}</div>}
          {trailingAction && <div className={styles.trailingAction}>{trailingAction}</div>}
        </div>
      )}
      {children && <div className={styles.body}>{children}</div>}
      {footer && <div className={styles.footer}>{footer}</div>}
    </Tag>
  );
});
