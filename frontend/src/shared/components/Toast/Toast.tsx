import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { HTMLAttributes, KeyboardEvent, ReactNode, TransitionEvent } from 'react';
import { Button } from '../Button';
import type { ButtonProps } from '../Button';
import styles from './Toast.module.css';

/**
 * ADAGS toast primitive — a transient, floating status message. Tokens only
 * (STYLE_GUIDE.md §2, §6, §8).
 *
 * It is self-contained: there is no global toast manager. Render one wherever
 * you need it (a corner stack, inline under a form) and give it an `onClose`
 * that removes it from your own list.
 *
 * Lifecycle
 * - **Uncontrolled** (omit `state`): mounts `entering`, animates to `visible`,
 *   and — if `duration` is a positive number — auto-dismisses after that many
 *   ms. Hovering or focusing the toast pauses the timer; leaving resumes it
 *   with the time that was left. The close button and `Escape` (while focus is
 *   inside) dismiss manually at any time. `onClose` fires once, after the exit
 *   transition finishes.
 * - **Controlled** (pass `state`): purely presentational — it renders the
 *   phase you give it and never runs timers. The close button / `Escape` call
 *   `onClose`; you move it to `exiting` and unmount it.
 *
 * Accessibility: the root is `role="status"` for `success` / `info` and
 * `role="alert"` for `warning` / `danger` (override with `role`). The close
 * control is a real button with an `aria-label`; `action` is a real `<Button>`.
 * All motion is removed under `prefers-reduced-motion`.
 *
 * @example 1 — fire-and-forget success, auto-dismiss after 4s
 * ```tsx
 * const [toasts, setToasts] = useState<{ id: number }[]>([]);
 * // ...on success:
 * setToasts((t) => [...t, { id: Date.now() }]);
 *
 * {toasts.map(({ id }) => (
 *   <Toast
 *     key={id}
 *     variant="success"
 *     title="Patient checked in"
 *     description="Amina Hassan added to the triage queue."
 *     duration={4000}
 *     onClose={() => setToasts((t) => t.filter((x) => x.id !== id))}
 *   />
 * ))}
 * ```
 *
 * @example 2 — sticky error with an action (no auto-dismiss)
 * ```tsx
 * <Toast
 *   variant="danger"
 *   title="Couldn’t save the vitals"
 *   description="The ward is offline. The reading is queued and will sync automatically."
 *   action={{ label: 'Retry now', onClick: retry }}
 *   onClose={dismiss}
 * />
 * ```
 *
 * @example 3 — controlled by your own stack manager
 * ```tsx
 * <Toast
 *   state={entry.phase}            // 'entering' | 'visible' | 'exiting'
 *   variant="info"
 *   size="sm"
 *   title="3 lab results ready"
 *   onClose={() => manager.remove(entry.id)}
 * />
 * ```
 */

export type ToastVariant = 'success' | 'info' | 'warning' | 'danger';
export type ToastSize = 'sm' | 'md' | 'lg';
export type ToastState = 'entering' | 'visible' | 'exiting';

/** A `Button` config without `children` — `label` carries the text instead. */
export interface ToastAction extends Omit<ButtonProps, 'children'> {
  /** The action button's visible text. */
  label: string;
}

export interface ToastProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  /** Status colour + default icon + ARIA role. @default 'info' */
  variant?: ToastVariant;
  /** Padding + font-size (no raw heights). @default 'md' */
  size?: ToastSize;
  /** The headline. Sentence case, no exclamation marks (STYLE_GUIDE §7). */
  title: string;
  /** Optional supporting line. */
  description?: ReactNode;
  /** Override the per-variant default icon; pass `null` to render no icon. */
  leadingIcon?: LucideIcon | null;
  /** A single optional action → `<Button variant="secondary" size="sm">`. */
  action?: ToastAction;
  /** Show the dismiss button. @default true */
  showClose?: boolean;
  /** Accessible label for the dismiss button. @default 'Dismiss' */
  closeLabel?: string;
  /** Auto-dismiss after this many ms. `null` / `0` / omitted = sticky. */
  duration?: number | null;
  /** Pause the auto-dismiss timer while the toast is hovered or focused. @default true */
  pauseOnInteraction?: boolean;
  /** ARIA role. @default 'alert' for warning/danger, 'status' for success/info */
  role?: 'status' | 'alert';
  /** Drive the lifecycle yourself (presentational mode). Omit to self-manage. */
  state?: ToastState;
  /** Called once the toast has fully left (after the exit transition). */
  onClose?: () => void;
}

const ICON_SIZE: Record<ToastSize, number> = { sm: 16, md: 16, lg: 20 };

const DEFAULT_ICON: Record<ToastVariant, LucideIcon> = {
  success: CheckCircle2,
  info: Info,
  warning: AlertTriangle,
  danger: AlertCircle,
};

function ActionButton({ action }: { action: ToastAction }) {
  const { label, ...rest } = action;
  return (
    <Button variant="secondary" size="sm" {...rest}>
      {label}
    </Button>
  );
}

/** Matches --duration-base; the transition is the real signal, this is the
 *  fallback for when there is none (prefers-reduced-motion). */
const EXIT_FALLBACK_MS = 250;

export function Toast({
  variant = 'info',
  size = 'md',
  title,
  description,
  leadingIcon,
  action,
  showClose = true,
  closeLabel = 'Dismiss',
  duration,
  pauseOnInteraction = true,
  role,
  state,
  onClose,
  className,
  onKeyDown,
  ...rest
}: ToastProps) {
  const controlled = state !== undefined;
  const [internalPhase, setInternalPhase] = useState<ToastState>('entering');
  const phase = controlled ? state : internalPhase;

  const timerRef = useRef<number | undefined>(undefined);
  const remainingRef = useRef<number>(typeof duration === 'number' ? duration : 0);
  const startRef = useRef<number>(0);
  const finishedRef = useRef(false);

  const clearTimer = useCallback(() => {
    if (timerRef.current !== undefined) {
      window.clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
  }, []);

  const beginExit = useCallback(() => {
    clearTimer();
    if (controlled) {
      onClose?.();
      return;
    }
    setInternalPhase((p) => (p === 'exiting' ? p : 'exiting'));
  }, [controlled, onClose, clearTimer]);

  const runTimer = useCallback(
    (ms: number) => {
      clearTimer();
      if (ms <= 0) return;
      startRef.current = Date.now();
      timerRef.current = window.setTimeout(beginExit, ms);
    },
    [beginExit, clearTimer],
  );

  // Uncontrolled: kick the enter transition on the frame after mount.
  useEffect(() => {
    if (controlled) return;
    const id = window.requestAnimationFrame(() => setInternalPhase('visible'));
    return () => window.cancelAnimationFrame(id);
  }, [controlled]);

  // Uncontrolled: start / restart the auto-dismiss timer once visible.
  useEffect(() => {
    if (controlled || phase !== 'visible') return;
    if (typeof duration !== 'number' || duration <= 0) return;
    remainingRef.current = duration;
    runTimer(duration);
    return clearTimer;
  }, [controlled, phase, duration, runTimer, clearTimer]);

  // Uncontrolled: resolve onClose even when there is no exit transition
  // (prefers-reduced-motion removes it, so transitionend never fires).
  useEffect(() => {
    if (controlled || phase !== 'exiting') return;
    const id = window.setTimeout(() => {
      if (!finishedRef.current) {
        finishedRef.current = true;
        onClose?.();
      }
    }, EXIT_FALLBACK_MS + 50);
    return () => window.clearTimeout(id);
  }, [controlled, phase, onClose]);

  useEffect(() => clearTimer, [clearTimer]);

  const pause = () => {
    if (controlled || !pauseOnInteraction || timerRef.current === undefined) return;
    const elapsed = Date.now() - startRef.current;
    remainingRef.current = Math.max(0, remainingRef.current - elapsed);
    clearTimer();
  };

  const resume = () => {
    if (controlled || !pauseOnInteraction || phase !== 'visible') return;
    if (timerRef.current !== undefined) return; // already running
    if (typeof duration === 'number' && duration > 0) {
      runTimer(remainingRef.current > 0 ? remainingRef.current : duration);
    }
  };

  const requestClose = () => {
    if (controlled) {
      onClose?.();
      return;
    }
    beginExit();
  };

  const handleTransitionEnd = (event: TransitionEvent<HTMLDivElement>) => {
    if (controlled || event.target !== event.currentTarget) return;
    if (phase === 'exiting' && !finishedRef.current) {
      finishedRef.current = true;
      onClose?.();
    }
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    onKeyDown?.(event);
    if (showClose && event.key === 'Escape' && !event.defaultPrevented) {
      event.stopPropagation();
      requestClose();
    }
  };

  const resolvedRole = role ?? (variant === 'danger' || variant === 'warning' ? 'alert' : 'status');
  const Icon = leadingIcon === null ? null : (leadingIcon ?? DEFAULT_ICON[variant]);

  const classes = [styles.root, styles[variant], styles[size], styles[phase], className].filter(Boolean).join(' ');

  return (
    <div
      className={classes}
      role={resolvedRole}
      aria-live={resolvedRole === 'alert' ? 'assertive' : 'polite'}
      aria-atomic="true"
      onPointerEnter={pause}
      onPointerLeave={resume}
      onFocus={pause}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) resume();
      }}
      onKeyDown={handleKeyDown}
      onTransitionEnd={handleTransitionEnd}
      {...rest}
    >
      {Icon && (
        <Icon size={ICON_SIZE[size]} strokeWidth={2} className={styles.icon} aria-hidden="true" />
      )}

      <div className={styles.content}>
        <p className={styles.title}>{title}</p>
        {description ? <p className={styles.description}>{description}</p> : null}
        {action ? (
          <div className={styles.actionRow}>
            <ActionButton action={action} />
          </div>
        ) : null}
      </div>

      {showClose && (
        <button type="button" className={styles.close} aria-label={closeLabel} onClick={requestClose}>
          <X size={16} strokeWidth={2} aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
