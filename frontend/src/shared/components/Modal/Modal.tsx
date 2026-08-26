import { X } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useCallback, useEffect, useId, useRef } from 'react';
import type { KeyboardEvent, MouseEvent, ReactNode, RefObject } from 'react';
import { createPortal } from 'react-dom';
import styles from './Modal.module.css';

/**
 * ADAGS modal primitive — an overlay dialog rendered through a portal, above
 * everything, with a focus trap, scroll lock, and an accessible backdrop.
 * Tokens only (STYLE_GUIDE.md §1, §2, §8).
 *
 * It is fully controlled: pass `open` and an `onClose` that flips it back to
 * false. Nothing renders while `open` is false. `onClose` is the single
 * "user wants out" signal — it fires for the close button, a backdrop click
 * (unless `closeOnBackdropClick={false}`), and Escape (unless
 * `closeOnEscape={false}`).
 *
 * Accessibility
 * - `role="dialog"`, `aria-modal="true"`; `aria-labelledby` points at the
 *   rendered title and `aria-describedby` at the description — each only when
 *   that content exists.
 * - Focus moves into the dialog on open (`initialFocusRef`, else the first
 *   focusable, else the panel) and is trapped with Tab / Shift+Tab. On close it
 *   returns to whatever was focused when the modal opened.
 * - Background content is made `inert` + `aria-hidden` while the modal is open,
 *   and page scroll is locked (with scrollbar-width compensation so the page
 *   does not shift).
 * - If disabling every close route would trap the user, the close button is
 *   forced back on (and a dev warning is logged).
 * - All motion is removed under `prefers-reduced-motion`.
 *
 * ---
 *
 * @example 1 — confirmation dialog with a footer
 * ```tsx
 * const [open, setOpen] = useState(false);
 * <Button variant="danger" onClick={() => setOpen(true)}>Void invoice</Button>
 * <Modal
 *   open={open}
 *   onClose={() => setOpen(false)}
 *   title="Void this invoice?"
 *   description="The invoice stays in the sequence with a voided status. This cannot be undone."
 *   leadingIcon={AlertTriangle}
 *   footer={
 *     <>
 *       <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
 *       <Button variant="danger" onClick={confirmVoid}>Void invoice</Button>
 *     </>
 *   }
 * />
 * ```
 *
 * @example 2 — a form in a modal, larger size, autofocus a field
 * ```tsx
 * const firstFieldRef = useRef<HTMLInputElement>(null);
 * <Modal
 *   open={open}
 *   onClose={close}
 *   size="lg"
 *   title="Add next of kin"
 *   initialFocusRef={firstFieldRef}
 *   footer={<Button type="submit" form="nok-form">Save</Button>}
 * >
 *   <form id="nok-form" onSubmit={onSubmit}>
 *     <Input ref={firstFieldRef} label="Full name" />
 *     <Input label="Phone number" />
 *   </form>
 * </Modal>
 * ```
 *
 * @example 3 — non-dismissible step (must act), close routes disabled
 * ```tsx
 * <Modal
 *   open={mustAcknowledge}
 *   onClose={noop}
 *   title="Allergy on file"
 *   description="This patient has a recorded allergy. Confirm you have read it before prescribing."
 *   showClose={false}
 *   closeOnBackdropClick={false}
 *   closeOnEscape={false}
 *   footer={<Button onClick={acknowledge}>I have read the allergy record</Button>}
 * />
 * ```
 */

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl';

export interface ModalProps {
  /** Mounts the modal (portal + backdrop) when true; fully unmounts when false. */
  open: boolean;
  /** Requested close — backdrop click, Escape, or the header close button. Flip `open` to false here. */
  onClose: () => void;

  /** Width tier — max-width + panel padding. @default 'md' */
  size?: ModalSize;

  /** Accessible heading. Rendered as the dialog's `<h2>`, wired to `aria-labelledby`. */
  title?: ReactNode;
  /** Supporting line under the title, wired to `aria-describedby`. */
  description?: ReactNode;
  /** Body content. Scrolls within the panel if it overflows the viewport. */
  children?: ReactNode;
  /** Footer row, typically actions. Divided from the body by a hairline border. */
  footer?: ReactNode;
  /** Lucide icon before the title (20px, heading size per §5). */
  leadingIcon?: LucideIcon;

  /** Header close (×) button. @default true */
  showClose?: boolean;
  /** Accessible label for the close button. @default 'Close' */
  closeLabel?: string;
  /** Close when the backdrop is clicked. @default true */
  closeOnBackdropClick?: boolean;
  /** Close when Escape is pressed. @default true */
  closeOnEscape?: boolean;

  /** Element focused on open. Falls back to the first focusable in the dialog, then the panel. */
  initialFocusRef?: RefObject<HTMLElement>;
  /** Portal target. @default document.body */
  container?: HTMLElement | null;

  /** Extra class on the dialog panel. */
  className?: string;
  /** id for the dialog element (labelledby / describedby ids derive from it). Auto-generated otherwise. */
  id?: string;
}

const HEADING_ICON_SIZE = 20;

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'area[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'iframe',
  'audio[controls]',
  'video[controls]',
  '[contenteditable]:not([contenteditable="false"])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

function getFocusable(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
    (el) => el.offsetWidth > 0 || el.offsetHeight > 0 || el === document.activeElement,
  );
}

/* ---- page scroll lock ------------------------------------------------------
 * Module-level so nested modals share one lock. `document.body.style` is set
 * imperatively — that is not a JSX inline style, and there is no token-driven
 * way to freeze the page. Scrollbar width is compensated so the page under the
 * backdrop does not jump when the bar disappears. */

let lockCount = 0;
let restoreOverflow = '';
let restorePaddingRight = '';

function lockScroll(): void {
  if (lockCount === 0) {
    const { body } = document;
    const barWidth = window.innerWidth - document.documentElement.clientWidth;
    restoreOverflow = body.style.overflow;
    restorePaddingRight = body.style.paddingRight;
    body.style.overflow = 'hidden';
    if (barWidth > 0) {
      const current = parseFloat(window.getComputedStyle(body).paddingRight) || 0;
      body.style.paddingRight = `${current + barWidth}px`;
    }
  }
  lockCount += 1;
}

function unlockScroll(): void {
  lockCount = Math.max(0, lockCount - 1);
  if (lockCount === 0) {
    document.body.style.overflow = restoreOverflow;
    document.body.style.paddingRight = restorePaddingRight;
  }
}

export function Modal({
  open,
  onClose,
  size = 'md',
  title,
  description,
  children,
  footer,
  leadingIcon: LeadingIcon,
  showClose = true,
  closeLabel = 'Close',
  closeOnBackdropClick = true,
  closeOnEscape = true,
  initialFocusRef,
  container,
  className,
  id,
}: ModalProps) {
  const generatedId = useId();
  const dialogId = id ?? `modal-${generatedId}`;
  const titleId = `${dialogId}-title`;
  const descriptionId = `${dialogId}-description`;

  const panelRef = useRef<HTMLDivElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);

  const hasHeader = Boolean(title || description || LeadingIcon);
  const hasBody = children != null && children !== false;

  // Never leave the user with no way out. If every close route is disabled,
  // force the close button back on.
  const noEscape = !closeOnBackdropClick && !closeOnEscape && !showClose;
  const resolvedShowClose = showClose || noEscape;
  if (noEscape && import.meta.env?.DEV) {
    // eslint-disable-next-line no-console
    console.warn(
      '[Modal] closeOnBackdropClick, closeOnEscape and showClose were all disabled — ' +
        'the close button has been re-enabled so the dialog stays accessible.',
    );
  }

  // Capture the trigger, lock scroll, hide the background — while open.
  useEffect(() => {
    if (!open) return;

    const target = container ?? document.body;
    previouslyFocused.current = (document.activeElement as HTMLElement) ?? null;
    lockScroll();

    const hidden: HTMLElement[] = [];
    for (const child of Array.from(target.children)) {
      if (!(child instanceof HTMLElement)) continue;
      if (child.contains(panelRef.current)) continue;
      if (child.hasAttribute('aria-hidden') && child.getAttribute('aria-hidden') === 'true') continue;
      child.setAttribute('aria-hidden', 'true');
      child.setAttribute('inert', '');
      hidden.push(child);
    }

    return () => {
      unlockScroll();
      for (const child of hidden) {
        child.removeAttribute('aria-hidden');
        child.removeAttribute('inert');
      }
      previouslyFocused.current?.focus?.();
      previouslyFocused.current = null;
    };
  }, [open, container]);

  // Move focus into the dialog once it is on screen.
  useEffect(() => {
    if (!open) return;
    const panel = panelRef.current;
    if (!panel) return;

    const explicit = initialFocusRef?.current;
    const fallback = getFocusable(panel)[0];
    (explicit ?? fallback ?? panel).focus();
  }, [open, initialFocusRef]);

  const requestClose = useCallback(() => {
    onClose();
  }, [onClose]);

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape' && closeOnEscape && !event.defaultPrevented) {
      event.stopPropagation();
      requestClose();
      return;
    }

    if (event.key !== 'Tab') return;
    const panel = panelRef.current;
    if (!panel) return;

    const focusable = getFocusable(panel);
    if (focusable.length === 0) {
      // Nothing tabbable — keep focus on the panel.
      event.preventDefault();
      panel.focus();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;

    if (event.shiftKey && (active === first || active === panel)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  };

  const handleBackdropMouseDown = (event: MouseEvent<HTMLDivElement>) => {
    // Only a press that both starts and ends on the backdrop itself counts —
    // a drag that began inside the panel must not dismiss.
    if (event.target !== event.currentTarget) return;
    if (closeOnBackdropClick) requestClose();
  };

  if (!open) return null;

  const panelClasses = [
    styles.panel,
    styles[size],
    hasHeader ? styles.hasHeader : '',
    hasBody ? styles.hasBody : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const headerClasses = [styles.header, LeadingIcon ? styles.withIcon : ''].filter(Boolean).join(' ');

  const overlay = (
    // eslint-disable-next-line jsx-a11y/no-static-element-interactions
    <div className={styles.backdrop} onMouseDown={handleBackdropMouseDown}>
      <div
        ref={panelRef}
        id={dialogId}
        className={panelClasses}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-describedby={description ? descriptionId : undefined}
        aria-label={title ? undefined : closeLabel}
        tabIndex={-1}
        onKeyDown={handleKeyDown}
      >
        {hasHeader && (
          <div className={headerClasses}>
            {LeadingIcon ? (
              <LeadingIcon
                size={HEADING_ICON_SIZE}
                strokeWidth={2}
                className={styles.icon}
                aria-hidden="true"
              />
            ) : (
              <span className={styles.icon} aria-hidden="true" />
            )}

            <div className={styles.heading}>
              {title ? (
                <h2 id={titleId} className={styles.title}>
                  {title}
                </h2>
              ) : null}
              {description ? (
                <p id={descriptionId} className={styles.description}>
                  {description}
                </p>
              ) : null}
            </div>

            {resolvedShowClose ? (
              <button
                type="button"
                className={styles.close}
                aria-label={closeLabel}
                onClick={requestClose}
              >
                <X size={HEADING_ICON_SIZE} strokeWidth={2} aria-hidden="true" />
              </button>
            ) : (
              <span aria-hidden="true" />
            )}
          </div>
        )}

        {hasBody ? <div className={styles.body}>{children}</div> : null}

        {footer != null && footer !== false ? (
          <div className={styles.footer}>{footer}</div>
        ) : null}
      </div>
    </div>
  );

  return createPortal(overlay, container ?? document.body);
}
