import { forwardRef, useId } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import styles from './Radio.module.css';

/**
 * ADAGS radio primitive. Tokens only — see STYLE_GUIDE.md §2, §6, §8.
 * A real native `<input type="radio">` throughout — `appearance: none` only
 * replaces the OS circle with a themed one; grouping via a shared `name`,
 * arrow-key navigation within that group, and native form submission are
 * all untouched.
 *
 * @example Basic group
 * ```tsx
 * <Radio name="triage-priority" value="routine" label="Routine" checked={priority === 'routine'} onChange={handleChange} />
 * <Radio name="triage-priority" value="urgent" label="Urgent" checked={priority === 'urgent'} onChange={handleChange} />
 * ```
 *
 * @example Uncontrolled, with a default selection
 * ```tsx
 * <Radio name="payer" value="cash" label="Cash" defaultChecked />
 * <Radio name="payer" value="nhif" label="NHIF" />
 * ```
 *
 * @example Validation error (react-hook-form)
 * ```tsx
 * <Radio name="consent" value="yes" label="Yes" errorText={errors.consent?.message} {...register('consent')} />
 * ```
 */

export type RadioSize = 'sm' | 'md' | 'lg';

// `size` is omitted from the native attributes: HTML's numeric `size` is
// meaningless on a radio, and this prop is our sm/md/lg scale instead.
export interface RadioProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /** Sizing of the circle (spacing-scale dimensions, not new tokens). @default 'md' */
  size?: RadioSize;
  label?: ReactNode;
  /** Supporting text shown below the field when there's no error. */
  helperText?: ReactNode;
  /** Shown instead of helperText; drives aria-invalid and aria-describedby. */
  errorText?: ReactNode;
}

export const Radio = forwardRef<HTMLInputElement, RadioProps>(function Radio(
  { size = 'md', label, helperText, errorText, id, className, 'aria-describedby': ariaDescribedBy, ...rest },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;

  const hasError = Boolean(errorText);
  const message = errorText ?? helperText;
  const messageId = message ? `${inputId}-message` : undefined;
  const describedBy = [ariaDescribedBy, messageId].filter(Boolean).join(' ') || undefined;

  const fieldClasses = [styles.field, className].filter(Boolean).join(' ');
  const circleClasses = [styles.circle, styles[size], hasError ? styles.error : ''].filter(Boolean).join(' ');
  const messageClasses = [styles.message, hasError ? styles.messageError : ''].filter(Boolean).join(' ');

  return (
    <div className={fieldClasses}>
      <label className={styles.root} htmlFor={inputId}>
        <input
          ref={ref}
          type="radio"
          id={inputId}
          className={circleClasses}
          aria-invalid={hasError || undefined}
          aria-describedby={describedBy}
          {...rest}
        />
        {label && <span className={styles.labelText}>{label}</span>}
      </label>
      {message && (
        <span id={messageId} className={messageClasses}>
          {message}
        </span>
      )}
    </div>
  );
});
