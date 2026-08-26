import type { LucideIcon } from 'lucide-react';
import { ChevronDown } from 'lucide-react';
import { forwardRef, useId } from 'react';
import type { ReactNode, SelectHTMLAttributes } from 'react';
import styles from './Select.module.css';

/**
 * ADAGS select primitive. Tokens only — see STYLE_GUIDE.md §2, §6, §8.
 * A real native `<select>` throughout — `appearance: none` only replaces the
 * OS arrow with a themed chevron; the popup, keyboard nav, and FormData
 * compatibility are all untouched.
 *
 * @example Basic labeled field
 * ```tsx
 * <Select
 *   label="Ward"
 *   placeholder="Select a ward"
 *   options={[{ value: 'a1', label: 'Ward A1' }, { value: 'b2', label: 'Ward B2' }]}
 * />
 * ```
 *
 * @example Validation error (react-hook-form)
 * ```tsx
 * <Select label="Payer type" errorText={errors.payerType?.message} options={payerOptions} {...register('payerType')} />
 * ```
 *
 * @example Raw children, for an <optgroup>
 * ```tsx
 * <Select label="Item">
 *   <optgroup label="Medications">
 *     <option value="amx">Amoxicillin</option>
 *   </optgroup>
 * </Select>
 * ```
 */

export type SelectVariant = 'default' | 'error' | 'success';
export type SelectSize = 'sm' | 'md' | 'lg';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

const ICON_SIZE = 16;

// `size` is omitted from the native attributes: HTML's numeric `size` turns a
// <select> into a multi-row listbox, which is out of scope for this dropdown
// primitive; this prop is our sm/md/lg scale instead.
export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  /** Visual style. @default 'default'. Also auto-applied whenever `errorText` is set. */
  variant?: SelectVariant;
  /** Sizing (padding + font-size, no raw heights). @default 'md' */
  size?: SelectSize;
  label?: ReactNode;
  /** Supporting text shown below the field when there's no error. */
  helperText?: ReactNode;
  /** Shown instead of helperText; drives aria-invalid and aria-describedby. */
  errorText?: ReactNode;
  /** Rendered before the field, e.g. `Building2`. */
  leadingIcon?: LucideIcon;
  /** Renders as a disabled first option with an empty value. */
  placeholder?: string;
  /** Declarative shortcut for simple option lists. Takes priority over `children`. */
  options?: SelectOption[];
  /** Raw `<option>`/`<optgroup>` children — used when `options` isn't given. */
  children?: ReactNode;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  {
    variant = 'default',
    size = 'md',
    label,
    helperText,
    errorText,
    leadingIcon: LeadingIcon,
    placeholder,
    options,
    children,
    id,
    required,
    className,
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  ref,
) {
  const generatedId = useId();
  const selectId = id ?? generatedId;

  const effectiveVariant: SelectVariant = errorText ? 'error' : variant;
  const message = errorText ?? helperText;
  const messageId = message ? `${selectId}-message` : undefined;
  const describedBy = [ariaDescribedBy, messageId].filter(Boolean).join(' ') || undefined;

  const variantClass = effectiveVariant !== 'default' ? styles[effectiveVariant] : '';

  const fieldClasses = [styles.field, className].filter(Boolean).join(' ');
  const wrapClasses = [styles.wrap, styles[size], LeadingIcon ? styles.hasLeadingIcon : '', variantClass]
    .filter(Boolean)
    .join(' ');
  const messageClasses = [
    styles.message,
    effectiveVariant === 'error' ? styles.messageError : '',
    effectiveVariant === 'success' ? styles.messageSuccess : '',
  ]
    .filter(Boolean)
    .join(' ');
  const chevronClasses = [styles.chevron, variantClass].filter(Boolean).join(' ');

  return (
    <div className={fieldClasses}>
      {label && (
        <label htmlFor={selectId} className={styles.label}>
          {label}
          {required && (
            <span className={styles.requiredMark} aria-hidden="true">
              *
            </span>
          )}
        </label>
      )}
      <div className={wrapClasses}>
        {LeadingIcon && <LeadingIcon size={ICON_SIZE} strokeWidth={2} className={styles.leadingIcon} aria-hidden="true" />}
        <select
          ref={ref}
          id={selectId}
          className={styles.select}
          required={required}
          aria-invalid={effectiveVariant === 'error' || undefined}
          aria-describedby={describedBy}
          {...rest}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options
            ? options.map((option) => (
                <option key={option.value} value={option.value} disabled={option.disabled}>
                  {option.label}
                </option>
              ))
            : children}
        </select>
        <ChevronDown size={ICON_SIZE} strokeWidth={2} className={chevronClasses} aria-hidden="true" />
      </div>
      {message && (
        <span id={messageId} className={messageClasses}>
          {message}
        </span>
      )}
    </div>
  );
});
