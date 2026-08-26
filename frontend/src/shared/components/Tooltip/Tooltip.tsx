import {
  cloneElement,
  isValidElement,
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';
import type { HTMLAttributes, ReactElement, ReactNode, Ref } from 'react';
import { createPortal } from 'react-dom';
import styles from './Tooltip.module.css';

/**
 * ADAGS tooltip primitive. Tokens only — see STYLE_GUIDE.md §1, §2, §3, §6, §8.
 *
 * Wraps a single focusable element and describes it with a portalled
 * `role="tooltip"` node. It appears on hover AND keyboard focus, never hover
 * alone; it is `pointer-events: none`, so it can never sit between the user
 * and the trigger. Escape dismisses it; so does activating the trigger.
 *
 * Positioning: the requested `placement` flips to the opposite side when it
 * would collide with the viewport, then the tooltip is clamped to an 8px
 * margin. Only the computed x / y / arrow offsets are written to the node (as
 * CSS custom properties, the same imperative escape hatch Modal uses for the
 * scroll lock) — every style rule lives in the CSS module.
 *
 * Open state is uncontrolled by default (`defaultOpen`); pass `open` +
 * `onOpenChange` to control it. `delay` tunes the hover open / close timing;
 * keyboard focus always opens immediately. `disabled` suppresses the tooltip
 * without touching the trigger's own behaviour. All motion is removed under
 * `prefers-reduced-motion`.
 *
 * The child must be one element that can take a `ref` and DOM event handlers
 * (a real `<button>`, `<a>`, or a `forwardRef` component that spreads props).
 *
 * @example 1 — icon-only button that needs a name on hover and focus
 * ```tsx
 * <Tooltip content="Print label">
 *   <button type="button" className="btn-ghost" aria-label="Print label">
 *     <Printer size={16} aria-hidden="true" />
 *   </button>
 * </Tooltip>
 * ```
 *
 * @example 2 — explain a disabled-looking action, placed to the right
 * ```tsx
 * <Tooltip placement="right" content="Controlled medicines can't be dispensed here.">
 *   <button type="button" className="btn-secondary">Dispense</button>
 * </Tooltip>
 * ```
 *
 * @example 3 — controlled, e.g. driven by a form validation step
 * ```tsx
 * const [open, setOpen] = useState(false);
 * <Tooltip open={open} onOpenChange={setOpen} size="sm" content="Enter a phone number">
 *   <input aria-invalid={open} onBlur={() => setOpen(hasError)} />
 * </Tooltip>
 * ```
 */

export type TooltipPlacement = 'top' | 'right' | 'bottom' | 'left';
export type TooltipSize = 'sm' | 'md';

export interface TooltipProps {
  /** Tooltip body. Non-interactive — rendered in a `role="tooltip"` element. */
  content: ReactNode;
  /** The single focusable element the tooltip describes. */
  children: ReactElement;
  /** Preferred side; flips to the opposite side on collision. @default 'top' */
  placement?: TooltipPlacement;
  /** @default 'md' */
  size?: TooltipSize;
  /** Controlled visibility. Pair with `onOpenChange`. */
  open?: boolean;
  /** Uncontrolled initial visibility. @default false */
  defaultOpen?: boolean;
  /** Fired on every open / close the component wants. */
  onOpenChange?: (open: boolean) => void;
  /** ms. A bare number sets the open delay. @default `{ open: 300, close: 100 }` */
  delay?: number | { open?: number; close?: number };
  /** Tooltip never shows; the trigger stays fully interactive. @default false */
  disabled?: boolean;
  /** Hide the arrow. @default false */
  hideArrow?: boolean;
  /** Trigger↔tooltip gap in px. @default 8 */
  offset?: number;
  /** id for the tooltip element; the trigger's `aria-describedby` points here. */
  id?: string;
  /** Extra class on the tooltip element. */
  className?: string;
  /** Portal target. @default document.body */
  container?: HTMLElement | null;
}

const DEFAULT_OPEN_DELAY = 300;
const DEFAULT_CLOSE_DELAY = 100;
const DEFAULT_OFFSET = 8; // px — mirrors --space-2
const VIEWPORT_PADDING = 8; // px — keep-within-viewport margin
const ARROW_INSET = 12; // px — min distance from a tooltip corner to the arrow centre

const OPPOSITE: Record<TooltipPlacement, TooltipPlacement> = {
  top: 'bottom',
  bottom: 'top',
  left: 'right',
  right: 'left',
};

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);

function normalizeDelay(delay: TooltipProps['delay']): { openDelay: number; closeDelay: number } {
  if (typeof delay === 'number') return { openDelay: delay, closeDelay: DEFAULT_CLOSE_DELAY };
  return {
    openDelay: delay?.open ?? DEFAULT_OPEN_DELAY,
    closeDelay: delay?.close ?? DEFAULT_CLOSE_DELAY,
  };
}

function assignRef<T>(ref: Ref<T> | undefined, value: T | null) {
  if (typeof ref === 'function') ref(value);
  else if (ref && typeof ref === 'object') (ref as { current: T | null }).current = value;
}

type TriggerProps = HTMLAttributes<HTMLElement> & { 'aria-describedby'?: string };

export function Tooltip({
  content,
  children,
  placement = 'top',
  size = 'md',
  open,
  defaultOpen = false,
  onOpenChange,
  delay,
  disabled = false,
  hideArrow = false,
  offset = DEFAULT_OFFSET,
  id,
  className,
  container,
}: TooltipProps) {
  if (!isValidElement(children)) {
    throw new Error('[Tooltip] `children` must be a single React element that accepts a ref.');
  }

  const generatedId = useId();
  const tooltipId = id ?? `tooltip-${generatedId}`;

  const isControlled = open !== undefined;
  const [internalOpen, setInternalOpen] = useState(defaultOpen);
  const openState = isControlled ? (open as boolean) : internalOpen;

  const hasContent = content != null && content !== false && content !== '';
  const visible = openState && !disabled && hasContent;

  // The trigger node is state, not a ref, so the listener effect below re-runs
  // when it mounts. `setTriggerNode` is passed as the child's ref callback.
  const [triggerNode, setTriggerNode] = useState<HTMLElement | null>(null);
  const tipRef = useRef<HTMLDivElement | null>(null);
  const hovered = useRef(false);
  const focused = useRef(false);
  const suppressed = useRef(false);
  const openTimer = useRef<number | undefined>(undefined);
  const closeTimer = useRef<number | undefined>(undefined);

  const { openDelay, closeDelay } = normalizeDelay(delay);

  const clearTimers = useCallback(() => {
    if (openTimer.current !== undefined) window.clearTimeout(openTimer.current);
    if (closeTimer.current !== undefined) window.clearTimeout(closeTimer.current);
    openTimer.current = undefined;
    closeTimer.current = undefined;
  }, []);

  const commit = useCallback(
    (next: boolean) => {
      if (!isControlled) setInternalOpen(next);
      onOpenChange?.(next);
    },
    [isControlled, onOpenChange],
  );

  const scheduleOpen = useCallback(
    (immediate: boolean) => {
      if (disabled || suppressed.current) return;
      clearTimers();
      const wait = immediate ? 0 : openDelay;
      if (wait <= 0) {
        commit(true);
        return;
      }
      openTimer.current = window.setTimeout(() => commit(true), wait);
    },
    [disabled, clearTimers, openDelay, commit],
  );

  const scheduleClose = useCallback(() => {
    clearTimers();
    if (closeDelay <= 0) {
      commit(false);
      return;
    }
    closeTimer.current = window.setTimeout(() => commit(false), closeDelay);
  }, [clearTimers, closeDelay, commit]);

  useEffect(() => () => clearTimers(), [clearTimers]);

  const updatePosition = useCallback(() => {
    const trigger = triggerNode;
    const tip = tipRef.current;
    if (!trigger || !tip) return;

    const a = trigger.getBoundingClientRect();
    const b = tip.getBoundingClientRect();
    const vw = document.documentElement.clientWidth;
    const vh = document.documentElement.clientHeight;

    const fits = (side: TooltipPlacement): boolean => {
      switch (side) {
        case 'top':
          return a.top - offset - b.height >= VIEWPORT_PADDING;
        case 'bottom':
          return a.bottom + offset + b.height <= vh - VIEWPORT_PADDING;
        case 'left':
          return a.left - offset - b.width >= VIEWPORT_PADDING;
        case 'right':
          return a.right + offset + b.width <= vw - VIEWPORT_PADDING;
      }
    };

    let side = placement;
    if (!fits(side) && fits(OPPOSITE[side])) side = OPPOSITE[side];

    let x: number;
    let y: number;
    if (side === 'top' || side === 'bottom') {
      x = clamp(
        a.left + a.width / 2 - b.width / 2,
        VIEWPORT_PADDING,
        Math.max(VIEWPORT_PADDING, vw - VIEWPORT_PADDING - b.width),
      );
      y = side === 'top' ? a.top - offset - b.height : a.bottom + offset;
    } else {
      x = side === 'left' ? a.left - offset - b.width : a.right + offset;
      y = clamp(
        a.top + a.height / 2 - b.height / 2,
        VIEWPORT_PADDING,
        Math.max(VIEWPORT_PADDING, vh - VIEWPORT_PADDING - b.height),
      );
    }

    const arrowMain =
      side === 'top' || side === 'bottom'
        ? clamp(a.left + a.width / 2 - x, ARROW_INSET, Math.max(ARROW_INSET, b.width - ARROW_INSET))
        : clamp(a.top + a.height / 2 - y, ARROW_INSET, Math.max(ARROW_INSET, b.height - ARROW_INSET));

    // Position, resolved side and reveal are written straight to the node —
    // the same imperative escape hatch used for the --tt-* vars — so a content
    // re-render never resets them and no extra React state is needed.
    tip.style.setProperty('--tt-x', `${Math.round(x)}px`);
    tip.style.setProperty('--tt-y', `${Math.round(y)}px`);
    tip.style.setProperty('--tt-arrow', `${Math.round(arrowMain)}px`);
    if (tip.getAttribute('data-placement') !== side) tip.setAttribute('data-placement', side);
    // Only flip the reveal on once — re-setting it every scroll frame would
    // restart the entry animation.
    if (tip.getAttribute('data-positioned') !== 'true') tip.setAttribute('data-positioned', 'true');
  }, [offset, placement, triggerNode]);

  useLayoutEffect(() => {
    if (!visible) return;
    updatePosition();

    const onReflow = () => updatePosition();
    window.addEventListener('scroll', onReflow, true);
    window.addEventListener('resize', onReflow);

    let observer: ResizeObserver | undefined;
    if (typeof ResizeObserver !== 'undefined' && tipRef.current) {
      observer = new ResizeObserver(onReflow);
      observer.observe(tipRef.current);
    }

    return () => {
      window.removeEventListener('scroll', onReflow, true);
      window.removeEventListener('resize', onReflow);
      observer?.disconnect();
    };
  }, [visible, updatePosition]);

  // Trigger interaction is bound with native listeners in an effect rather than
  // cloned-in React props: it keeps every ref read out of the render path, and
  // the child's own onMouseEnter / onKeyDown / … keep firing untouched.
  useEffect(() => {
    if (!triggerNode || disabled) return;

    const onEnter = () => {
      hovered.current = true;
      scheduleOpen(false);
    };
    const onLeave = () => {
      hovered.current = false;
      suppressed.current = false;
      if (!focused.current) scheduleClose();
    };
    const onFocusIn = () => {
      focused.current = true;
      if (!suppressed.current) scheduleOpen(true);
    };
    const onFocusOut = () => {
      focused.current = false;
      suppressed.current = false;
      if (!hovered.current) scheduleClose();
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && (openState || openTimer.current !== undefined)) {
        clearTimers();
        suppressed.current = true;
        commit(false);
        event.stopPropagation();
      }
    };
    const onPointer = () => {
      // The trigger is being activated — get out of the way. Reopens on the
      // next fresh hover / focus-in (suppressed until the pointer leaves / blur).
      clearTimers();
      suppressed.current = true;
      commit(false);
    };

    triggerNode.addEventListener('mouseenter', onEnter);
    triggerNode.addEventListener('mouseleave', onLeave);
    triggerNode.addEventListener('focus', onFocusIn);
    triggerNode.addEventListener('blur', onFocusOut);
    triggerNode.addEventListener('keydown', onKey);
    triggerNode.addEventListener('pointerdown', onPointer);
    return () => {
      triggerNode.removeEventListener('mouseenter', onEnter);
      triggerNode.removeEventListener('mouseleave', onLeave);
      triggerNode.removeEventListener('focus', onFocusIn);
      triggerNode.removeEventListener('blur', onFocusOut);
      triggerNode.removeEventListener('keydown', onKey);
      triggerNode.removeEventListener('pointerdown', onPointer);
    };
  }, [triggerNode, disabled, openState, scheduleOpen, scheduleClose, clearTimers, commit]);

  const child = children as ReactElement<TriggerProps>;
  const childRef = (child as unknown as { ref?: Ref<HTMLElement> }).ref;

  const setTriggerRef = useCallback(
    (node: HTMLElement | null) => {
      setTriggerNode(node);
      assignRef(childRef, node);
    },
    [childRef],
  );

  const describedBy = visible
    ? [child.props['aria-describedby'], tooltipId].filter(Boolean).join(' ')
    : child.props['aria-describedby'];

  const trigger = cloneElement(child, {
    ref: setTriggerRef,
    'aria-describedby': describedBy || undefined,
  } as Partial<TriggerProps> & { ref: Ref<HTMLElement> });

  const tooltipClasses = [styles.tooltip, styles[size], className].filter(Boolean).join(' ');

  return (
    <>
      {trigger}
      {visible &&
        createPortal(
          <div
            ref={tipRef}
            id={tooltipId}
            role="tooltip"
            /* data-placement / data-positioned are set imperatively in
               updatePosition() once measured — kept out of JSX so a re-render
               can't reset them mid-life. */
            className={tooltipClasses}
          >
            {content}
            {!hideArrow && <span className={styles.arrow} aria-hidden="true" />}
          </div>,
          container ?? document.body,
        )}
    </>
  );
}
