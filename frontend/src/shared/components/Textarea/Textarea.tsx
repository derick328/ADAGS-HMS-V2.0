import type { LucideIcon } from 'lucide-react';
import { AlertCircle, Check } from 'lucide-react';
import { forwardRef, useId, useState } from 'react';
import type { ChangeEvent, ReactNode, TextareaHTMLAttributes } from 'react';
import styles from './Textarea.module.css';

/**
 * ADAGS textarea primitive. Tokens only — see STYLE_GUIDE.md §2, §6, §8.
 * Uncontrolled or controlled: `value`/`onChange` (or `defaultValue`) pass
 * straight through, the component holds no state of its own beyond a
 * display-only character count for the uncontrolled case.
 *
 * @example Basic labeled field
 * ```tsx
 * <Textarea label="Clinical notes" placeholder="Describe the presenting complaint" rows={5} />
 * ```
 *
 * @example Validation error (react-hook-form)
 * ```tsx
 * <Textarea label="Reason for referral" errorText={errors.reason?.message} {...register('reason')} />
 * ```
 *
 * @example Length-limited with a visible counter
 * ```tsx
 * <Textarea label="Discharge summary" maxLength={500} showCharacterCount rows={6} />
 * ```
 */

export type TextareaVariant = 'default' | 'error' | 'success';
export type TextareaSize = 'sm' | 'md' | 'lg';
export type TextareaResize = 'none' | 'vertical' | 'both';

const ICON_SIZE = 16;

const RESIZE_CLASS: Record<TextareaResize, string> = {
  none: styles.resizeNone,
  vertical: styles.resizeVertical,
  both: styles.resizeBoth,
};

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  /** Visual style. @default 'default'. Also auto-applied whenever `errorText` is set. */
  variant?: TextareaVariant;
  /** Sizing (padding + font-size, no raw heights). @default 'md' */
  size?: TextareaSize;
  label?: ReactNode;
  /** Supporting text shown below the field when there's no error. */
  helperText?: ReactNode;
  /** Shown instead of helperText; drives aria-invalid and aria-describedby. */
  errorText?: ReactNode;
  /** Rendered before the field, top-aligned with the first line. */
  leadingIcon?: LucideIcon;
  /** Rendered after the field. Defaults to a status icon (Check / AlertCircle)
   * for the success/error variants so status is never colour alone — pass an
   * explicit icon to override that default. */
  trailingIcon?: LucideIcon;
  /** Native `resize` behaviour. @default 'vertical' — never bare 'horizontal',
   * it breaks the surrounding layout. */
  resize?: TextareaResize;
  /** Shows a "current/max" counter. Only renders when `maxLength` is also set. */
  showCharacterCount?: boolean;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  {
    variant = 'default',
    size = 'md',
    label,
    helperText,
    errorText,
    leadingIcon: LeadingIcon,
    trailingIcon: TrailingIcon,
    resize = 'vertical',
    showCharacterCount = false,
    id,
    required,
    rows = 3,
    maxLength,
    value,
    defaultValue,
    onChange,
    className,
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  ref,
) {
  const generatedId = useId();
  const textareaId = id ?? generatedId;

  // Purely a display counter — never fed back into the textarea's value, so
  // the component stays usable as either controlled or uncontrolled.
  const [uncontrolledLength, setUncontrolledLength] = useState(() =>
    typeof value === 'string' ? value.length : typeof defaultValue === 'string' ? defaultValue.length : 0,
  );

  const handleChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    if (value === undefined) {
      setUncontrolledLength(event.target.value.length);
    }
    onChange?.(event);
  };

  const currentLength = typeof value === 'string' ? value.length : uncontrolledLength;
  const countVisible = showCharacterCount && maxLength != null;

  const effectiveVariant: TextareaVariant = errorText ? 'error' : variant;
  const message = errorText ?? helperText;
  const messageId = message ? `${textareaId}-message` : undefined;
  const countId = countVisible ? `${textareaId}-count` : undefined;
  const describedBy = [ariaDescribedBy, messageId, countId].filter(Boolean).join(' ') || undefined;

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
  const textareaClasses = [styles.textarea, RESIZE_CLASS[resize]].filter(Boolean).join(' ');
  const messageClasses = [
    styles.message,
    effectiveVariant === 'error' ? styles.messageError : '',
    effectiveVariant === 'success' ? styles.messageSuccess : '',
  ]
    .filter(Boolean)
    .join(' ');
  const leadingIconClasses = [styles.leadingIcon, variantClass].filter(Boolean).join(' ');
  const trailingIconClasses = [styles.trailingIcon, variantClass].filter(Boolean).join(' ');
  const countClasses = [styles.count, maxLength != null && currentLength >= maxLength ? styles.countAtLimit : '']
    .filter(Boolean)
    .join(' ');

  return (
    <div className={fieldClasses}>
      {label && (
        <label htmlFor={textareaId} className={styles.label}>
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
        <textarea
          ref={ref}
          id={textareaId}
          className={textareaClasses}
          rows={rows}
          maxLength={maxLength}
          value={value}
          defaultValue={defaultValue}
          onChange={handleChange}
          required={required}
          aria-invalid={effectiveVariant === 'error' || undefined}
          aria-describedby={describedBy}
          {...rest}
        />
        {ResolvedTrailingIcon && (
          <ResolvedTrailingIcon size={ICON_SIZE} strokeWidth={2} className={trailingIconClasses} aria-hidden="true" />
        )}
      </div>
      {(message || countVisible) && (
        <div className={styles.footer}>
          {message ? (
            <span id={messageId} className={messageClasses}>
              {message}
            </span>
          ) : (
            <span />
          )}
          {countVisible && (
            <span id={countId} className={countClasses}>
              {currentLength}/{maxLength}
            </span>
          )}
        </div>
      )}
    </div>
  );
});
