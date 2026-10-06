import { Children, cloneElement, isValidElement, useId } from 'react';
import type { InputHTMLAttributes, ReactNode } from 'react';

type ControlProps = InputHTMLAttributes<HTMLInputElement>;

/** Keeps registration refs/handlers intact, including inputs with adjacent hints. */
export function FormField({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  const generatedId = useId();
  const nodes = Children.toArray(children);
  const controls = nodes.filter((child) => isValidElement<ControlProps>(child)
    && typeof child.type === 'string' && ['input', 'select', 'textarea'].includes(child.type));
  const control = controls.length === 1 && isValidElement<ControlProps>(controls[0]) ? controls[0] : undefined;
  const id = control?.props.id ?? generatedId;
  const errorId = `${id}-error`;
  const labelId = `${id}-label`;
  return <div className="form-field" role={control ? undefined : 'group'} aria-labelledby={control ? undefined : labelId}>
    <label id={labelId} htmlFor={control ? id : undefined}>{label}</label>
    {nodes.map((child) => child === control ? cloneElement(control, {
      id,
      'aria-labelledby': [labelId, control.props['aria-labelledby']].filter(Boolean).join(' '),
      'aria-invalid': error ? true : control.props['aria-invalid'],
      'aria-describedby': [control.props['aria-describedby'], error ? errorId : undefined].filter(Boolean).join(' ') || undefined,
    }) : child)}
    {error && <span id={errorId} className="field-error">{error}</span>}
  </div>;
}
