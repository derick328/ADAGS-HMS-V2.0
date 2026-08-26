import type { LucideIcon } from 'lucide-react';
import { AlertCircle, Check } from 'lucide-react';
import { forwardRef, useId } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';
import styles from './Input.module.css';

/**
 * ADAGS input primitive. Tokens only — see STYLE_GUIDE.md §2, §6, §8.
 * Uncontrolled or controlled: `value`/`onChange` (or `defaultValue`) pass
 * straight through, the component holds no state of its own.
 *
 * @example Basic labeled field
 * ```tsx
 * <Input label="Full name" placeholder="Enter the patient's full name" />
 * ```
 *
 * @example Validation error (react-hook-form)
 * ```tsx
 * <Input label="Phone number" errorText={errors.phone?.message} {...register('phone')} />
 * ```
 *
 * @example With a leading icon
 * ```tsx
 * <Input label="Username" leadingIcon={User} autoComplete="username" required />
 * ```
 */

export type InputVariant = 'default' | 'error' | 'success';
export type InputSize = 'sm' | 'md' | 'lg';

const ICON_SIZE = 16;

// `size` is omitted from the native attributes: HTML's numeric character-width
// `size` is superseded by CSS width here, and this prop is our sm/md/lg scale.
export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /** Visual style. @default 'default'. Also auto-applied whenever `errorText` is set. */
  variant?: InputVariant;
  /** Sizing (padding + font-size, no raw heights). @default 'md' */
  size?: InputSize;
  label?: ReactNode;
  /** Supporting text shown below the field when there's no error. */
  helperText?: ReactNode;
  /** Shown instead of helperText; drives aria-invalid and aria-describedby. */
  errorText?: ReactNode;
  /** Rendered before the field, e.g. `User`. */
  leadingIcon?: LucideIcon;
  /** Rendered after the field, e.g. `Eye`. Defaults to a status icon (Check /
   * AlertCircle) for the success/error variants so status is never colour
   * alone — pass an explicit icon to override that default. */
  trailingIcon?: LucideIcon;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    variant = 'default',
    size = 'md',
    label,
    helperText,
    errorText,
    leadingIcon: LeadingIcon,
    trailingIcon: TrailingIcon,
    id,
    required,
    className,
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;

  const effectiveVariant: InputVariant = errorText ? 'error' : variant;
  const message = errorText ?? helperText;
  const messageId = message ? `${inputId}-message` : undefined;
  const describedBy = [ariaDescribedBy, messageId].filter(Boolean).join(' ') || undefined;

  const ResolvedTrailingIcon =
    TrailingIcon ??
    (effectiveVariant === 'success' ? Check : effectiveVariant === 'error' ? AlertCircle : undefined);

  const variantClass = effectiveVariant !== 'default' ? styles[effectiveVariant] : '';

  const fieldClasses = [styles.field, className].filter(Boolean).join(' ');
  const wrapClasses = [
    styles.wrap,
    styles[size],
    LeadingIcon ? styles.hasLeadingIcon : '',
    ResolvedTrailingIcon ? styles.hasTrailingIcon : '',
    variantClass,
  ]
    .filter(Boolean)
    .join(' ');
  const messageClasses = [
    styles.message,
    effectiveVariant === 'error' ? styles.messageError : '',
    effectiveVariant === 'success' ? styles.messageSuccess : '',
  ]
    .filter(Boolean)
    .join(' ');
  const leadingIconClasses = [styles.leadingIcon, variantClass].filter(Boolean).join(' ');
  const trailingIconClasses = [styles.trailingIcon, variantClass].filter(Boolean).join(' ');

  return (
    <div className={fieldClasses}>
      {label && (
        <label htmlFor={inputId} className={styles.label}>
          {label}
          {required && (
            <span className={styles.requiredMark} aria-hidden="true">
              *
            </span>
          )}
        </label>
      )}
      <div className={wrapClasses}>
        {LeadingIcon && (
          <LeadingIcon size={ICON_SIZE} strokeWidth={2} className={leadingIconClasses} aria-hidden="true" />
        )}
        <input
          ref={ref}
          id={inputId}
          className={styles.input}
          required={required}
          aria-invalid={effectiveVariant === 'error' || undefined}
          aria-describedby={describedBy}
          {...rest}
        />
        {ResolvedTrailingIcon && (
          <ResolvedTrailingIcon size={ICON_SIZE} strokeWidth={2} className={trailingIconClasses} aria-hidden="true" />
        )}
      </div>
      {message && (
        <span id={messageId} className={messageClasses}>
          {message}
        </span>
      )}
    </div>
  );
});
