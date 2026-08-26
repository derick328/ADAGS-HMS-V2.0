import type { HTMLAttributes } from 'react';
import styles from './Skeleton.module.css';

/**
 * ADAGS skeleton primitive — a low-attention placeholder for content that is
 * still loading. Tokens only (STYLE_GUIDE.md §2, §4, §8). The motion is a
 * gentle opacity pulse, never a moving gradient (§1 bans gradients on
 * surfaces), and it stills completely under `prefers-reduced-motion`.
 *
 * Accessibility: by default the skeleton is `aria-hidden` and has no role —
 * it is decorative, and the surrounding region is expected to own the
 * loading semantics (`aria-busy="true"`, a visible "Loading…" label, …).
 * Pass `label` to make a standalone skeleton announce itself as a polite
 * `role="status"` live region instead. Anything you pass — `className`,
 * `aria-*`, `data-*` — is forwarded and overrides these defaults.
 *
 * @example 1 — a few lines of body text; the parent owns the loading state
 * ```tsx
 * <div aria-busy={isLoading}>
 *   {isLoading ? <Skeleton lines={3} /> : <p>{note.body}</p>}
 * </div>
 * ```
 *
 * @example 2 — an avatar + name row (parent supplies the flex layout)
 * ```tsx
 * <div className={styles.row}>
 *   <Skeleton variant="circular" size="md" />
 *   <Skeleton variant="text" width="1/2" />
 * </div>
 * ```
 *
 * @example 3 — a standalone block that should announce itself
 * ```tsx
 * <Skeleton variant="rectangular" size="lg" label="Loading chart" />
 * ```
 */

export type SkeletonVariant = 'text' | 'circular' | 'rectangular';
export type SkeletonSize = 'sm' | 'md' | 'lg';
export type SkeletonWidth = 'full' | '3/4' | '1/2' | '1/4';

const WIDTH_CLASS: Record<SkeletonWidth, string> = {
  full: styles.wFull,
  '3/4': styles.wThreeQuarters,
  '1/2': styles.wHalf,
  '1/4': styles.wQuarter,
};

export interface SkeletonProps extends HTMLAttributes<HTMLDivElement> {
  /** Placeholder shape. @default 'text' */
  variant?: SkeletonVariant;
  /**
   * Scale step. `text` → line height (from the type scale); `circular` →
   * diameter; `rectangular` → block height. @default 'md'
   */
  size?: SkeletonSize;
  /**
   * Fraction of the container to fill. Ignored for `circular` (its `size`
   * drives both axes) and for every line except the last when `lines > 1`.
   * @default 'full'
   */
  width?: SkeletonWidth;
  /**
   * `text` only — render N stacked lines with the last one shortened, the
   * way a ragged paragraph looks. Values below 2 render a single line.
   * @default 1
   */
  lines?: number;
  /**
   * Accessible label. When set, the skeleton becomes a polite
   * `role="status"` live region announcing this text. When omitted, the
   * skeleton is `aria-hidden` and the surrounding region is expected to own
   * the loading semantics.
   */
  label?: string;
}

export function Skeleton({
  variant = 'text',
  size = 'md',
  width = 'full',
  lines = 1,
  label,
  className,
  ...rest
}: SkeletonProps) {
  const a11y = label
    ? ({ role: 'status', 'aria-live': 'polite', 'aria-label': label } as const)
    : ({ 'aria-hidden': true } as const);

  // Multi-line text: the root is a flex column of bars, last one shortened.
  if (variant === 'text' && lines > 1) {
    const count = Math.floor(lines);
    return (
      <div className={[styles.stack, className].filter(Boolean).join(' ')} {...a11y} {...rest}>
        {Array.from({ length: count }, (_, index) => (
          <span
            key={`skeleton-line-${index}`}
            className={[
              styles.bar,
              styles.text,
              styles[size],
              index === count - 1 ? styles.wLastLine : styles.wFull,
            ].join(' ')}
          />
        ))}
      </div>
    );
  }

  const classes = [
    styles.bar,
    styles[variant],
    styles[size],
    variant === 'circular' ? undefined : WIDTH_CLASS[width],
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return <div className={classes} {...a11y} {...rest} />;
}
