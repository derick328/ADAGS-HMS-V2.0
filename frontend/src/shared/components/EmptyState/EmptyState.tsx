import type { LucideIcon } from 'lucide-react';
import type { HTMLAttributes, JSX, ReactNode } from 'react';
import { Button } from '../Button';
import type { ButtonProps, ButtonSize } from '../Button';
import styles from './EmptyState.module.css';

/**
 * ADAGS empty-state primitive — the panel shown *in place of* content that
 * isn't there: an empty queue, a search with no hits, a list before its first
 * row. Tokens only (STYLE_GUIDE.md §2, §4, §5, §7).
 *
 * It renders transparent — no border, no background, no shadow — so it drops
 * into a `Card`, an empty `<tbody>`, or a workspace panel without a doubled
 * edge. Wrap it in `<Card>` if you want it boxed.
 *
 * Copy rule (STYLE_GUIDE §7): the `title` says what the emptiness *means* in
 * context — "No patients waiting for triage", never "No results". Sentence
 * case, no exclamation marks.
 *
 * Accessibility: `title` is a real heading (`<h2>` by default; set
 * `headingLevel` to fit the page outline). Actions are real `<Button>`s. For
 * an empty state that appears as the *result* of a user action (a search that
 * returned nothing), pass `role="status"` so it's announced.
 *
 * Actions are not re-implemented here: `primaryAction` / `secondaryAction`
 * take a `{ label, ...ButtonProps }` object and render as `<Button>` — pass
 * `onClick`, `leadingIcon`, `loading`, `disabled`, `type`, anything `Button`
 * accepts. `variant` and `size` are supplied for you (override via the spread
 * if you must).
 *
 * @example 1 — an empty queue (icon + title + one action)
 * ```tsx
 * <EmptyState
 *   icon={Inbox}
 *   title="No patients waiting for triage"
 *   description="New arrivals will appear here as reception checks them in."
 *   primaryAction={{ label: 'Register a walk-in', leadingIcon: Plus, onClick: openIntake }}
 * />
 * ```
 *
 * @example 2 — a search with no hits (compact, drops into a results panel)
 * ```tsx
 * <EmptyState
 *   variant="compact"
 *   role="status"
 *   icon={SearchX}
 *   title="No patients match “{query}”"
 *   description="Check the spelling, or search by phone number instead."
 *   secondaryAction={{ label: 'Clear search', onClick: resetQuery }}
 * />
 * ```
 *
 * @example 3 — a first-run workspace splash (full, custom illustration)
 * ```tsx
 * <EmptyState
 *   variant="full"
 *   headingLevel={2}
 *   illustration={<WardIllustration />}
 *   title="Your ward has no admitted patients"
 *   description="Once a doctor admits a patient to this ward, their bed and vitals show up here."
 *   primaryAction={{ label: 'View admission requests', onClick: goToRequests }}
 *   secondaryAction={{ label: 'Read the ward guide', variant: 'ghost', onClick: openDocs }}
 * />
 * ```
 */

export type EmptyStateVariant = 'default' | 'compact' | 'full';

/** A `Button` config without its `children` — `label` carries the text instead. */
export interface EmptyStateAction extends Omit<ButtonProps, 'children'> {
  /** The button's visible text. */
  label: string;
}

export interface EmptyStateProps extends HTMLAttributes<HTMLDivElement> {
  /** Density / prominence. @default 'default' */
  variant?: EmptyStateVariant;
  /** The headline — what the emptiness means in context (STYLE_GUIDE §7). */
  title: string;
  /** Optional supporting line: what to do about it, or why it's empty. */
  description?: ReactNode;
  /** A lucide-react icon, rendered decoratively at the §5 empty-state size. */
  icon?: LucideIcon;
  /**
   * A custom illustration (an `<svg>`, `<img>`, or element). Occupies the icon
   * slot and wins over `icon` if both are set. You own its accessible
   * semantics — mark it `aria-hidden` or give it a label.
   */
  illustration?: ReactNode;
  /** Primary call to action → `<Button variant="primary">`. */
  primaryAction?: EmptyStateAction;
  /** Secondary call to action → `<Button variant="secondary">`. */
  secondaryAction?: EmptyStateAction;
  /** Heading tag for `title`, so the primitive fits the page outline. @default 2 */
  headingLevel?: 2 | 3 | 4 | 5 | 6;
}

const ICON_SIZE: Record<EmptyStateVariant, number> = {
  compact: 20,
  default: 24,
  full: 24,
};

const BUTTON_SIZE: Record<EmptyStateVariant, ButtonSize> = {
  compact: 'sm',
  default: 'md',
  full: 'md',
};

function ActionButton({
  action,
  variant,
  size,
}: {
  action: EmptyStateAction;
  variant: ButtonProps['variant'];
  size: ButtonSize;
}) {
  const { label, ...rest } = action;
  return (
    <Button variant={variant} size={size} {...rest}>
      {label}
    </Button>
  );
}

export function EmptyState({
  variant = 'default',
  title,
  description,
  icon: Icon,
  illustration,
  primaryAction,
  secondaryAction,
  headingLevel = 2,
  className,
  ...rest
}: EmptyStateProps) {
  const Heading = `h${headingLevel}` as keyof JSX.IntrinsicElements;
  const hasMedia = Boolean(illustration) || Boolean(Icon);
  const hasActions = Boolean(primaryAction) || Boolean(secondaryAction);
  const buttonSize = BUTTON_SIZE[variant];

  return (
    <div className={[styles.root, styles[variant], className].filter(Boolean).join(' ')} {...rest}>
      {hasMedia && (
        <div className={styles.media}>
          {illustration ??
            (Icon && (
              <Icon size={ICON_SIZE[variant]} strokeWidth={2} className={styles.icon} aria-hidden="true" />
            ))}
        </div>
      )}

      <div className={styles.text}>
        <Heading className={styles.title}>{title}</Heading>
        {description ? <p className={styles.description}>{description}</p> : null}
      </div>

      {hasActions && (
        <div className={styles.actions}>
          {secondaryAction && <ActionButton action={secondaryAction} variant="secondary" size={buttonSize} />}
          {primaryAction && <ActionButton action={primaryAction} variant="primary" size={buttonSize} />}
        </div>
      )}
    </div>
  );
}
