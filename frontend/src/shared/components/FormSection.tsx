import type { ReactNode } from 'react';

/** Semantic clinical grouping, with an optional responsive field grid. */
export function FormSection({ title, children, columns = 1, tone = 'default', className = '' }: {
  title: ReactNode; children: ReactNode; columns?: 1 | 2 | 3; tone?: 'default' | 'critical'; className?: string;
}) {
  return <fieldset className={`form-section form-section--${tone} ${className}`}>
    <legend>{title}</legend>
    <div className={`form-section-body form-section-columns-${columns}`}>{children}</div>
  </fieldset>;
}
