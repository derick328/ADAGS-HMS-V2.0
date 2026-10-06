import { Check, Minus } from 'lucide-react';
import { forwardRef, useEffect, useId, useRef } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import styles from './Checkbox.module.css';

/**
 * ADAGS checkbox primitive. Tokens only — see STYLE_GUIDE.md §2, §6, §8.
 * A real native `<input type="checkbox">` throughout — `appearance: none`
 * only replaces the OS box with a themed one; Space-to-toggle, form
 * participation, and screen-reader semantics are all untouched.
 *
 * @example Basic labeled checkbox
 * ```tsx
 * <Checkbox label="I confirm the patient's identity" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />
 * ```
 *
 * @example Validation error (react-hook-form)
 * ```tsx
 * <Checkbox label="I accept the consent form" errorText={errors.consent?.message} {...register('consent')} />
 * ```
 *
 * @example Indeterminate "select all" checkbox
 * ```tsx
 * <Checkbox label="Select all" checked={allSelected} indeterminate={someSelected && !allSelected} onChange={toggleAll} />
 * ```
 */

export type CheckboxSize = 'sm' | 'md' | 'lg';

const ICON_SIZE = 16;

// `size` is omitted from the native attributes: HTML's numeric `size` is
// meaningless on a checkbox, and this prop is our sm/md/lg scale instead.
export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /** Sizing of the box (spacing-scale dimensions, not new tokens). @default 'md' */
  size?: CheckboxSize;
  label?: ReactNode;
  /** Supporting text shown below the field when there's no error. */
  helperText?: ReactNode;
  /** Shown instead of helperText; drives aria-invalid and aria-describedby. */
  errorText?: ReactNode;
  /** Mixed/partial state. Set as a DOM property via ref — HTML has no
   * `indeterminate` attribute, so this can't be expressed declaratively. */
  indeterminate?: boolean;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  {
    size = 'md',
    label,
    helperText,
    errorText,
    indeterminate = false,
    id,
    required,
    className,
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  forwardedRef,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  // `T | null` initial value selects the MutableRefObject overload of useRef
  // (writable `current`); `useRef<HTMLInputElement>(null)` would give a
  // read-only RefObject and the assignment below wouldn't type-check.
  const internalRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (internalRef.current) {
      internalRef.current.indeterminate = indeterminate;
    }
  }, [indeterminate]);

  const setRefs = (node: HTMLInputElement | null) => {
    internalRef.current = node;
    if (typeof forwardedRef === 'function') {
      forwardedRef(node);
    } else if (forwardedRef) {
      forwardedRef.current = node;
    }
  };

  const hasError = Boolean(errorText);
  const message = errorText ?? helperText;
  const messageId = message ? `${inputId}-message` : undefined;
  const describedBy = [ariaDescribedBy, messageId].filter(Boolean).join(' ') || undefined;

  const fieldClasses = [styles.field, className].filter(Boolean).join(' ');
  const boxClasses = [styles.box, styles[size], hasError ? styles.error : ''].filter(Boolean).join(' ');
  const messageClasses = [styles.message, hasError ? styles.messageError : ''].filter(Boolean).join(' ');

  return (
    <div className={fieldClasses}>
      <label className={styles.root} htmlFor={inputId}>
        <span className={styles.control}><input
          ref={setRefs}
          type="checkbox"
          id={inputId}
          className={boxClasses}
          required={required}
          aria-invalid={hasError || undefined}
          aria-describedby={describedBy}
          {...rest}
        />
        <Check size={ICON_SIZE} strokeWidth={2} className={styles.checkIcon} aria-hidden="true" />
        <Minus size={ICON_SIZE} strokeWidth={2} className={styles.indeterminateIcon} aria-hidden="true" /></span>
        {label && (
          <span className={styles.labelText}>
            {label}
            {required && (
              <span className={styles.requiredMark} aria-hidden="true">
                *
              </span>
            )}
          </span>
        )}
      </label>
      {message && (
        <span id={messageId} className={messageClasses}>
          {message}
        </span>
      )}
    </div>
  );
});
