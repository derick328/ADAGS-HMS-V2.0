import type { ReactNode } from 'react';

/** A single page-level heading, with optional guidance and existing actions. */
export function PageHeader({ title, description, children, level = 1 }: { title: ReactNode; description?: ReactNode; children?: ReactNode; level?: 1 | 2 }) {
  const Heading = level === 1 ? 'h1' : 'h2';
  return <header className="page-header">
    <div className="page-header-copy"><Heading>{title}</Heading>{description && <p>{description}</p>}</div>
    {children && <div className="page-header-actions">{children}</div>}
  </header>;
}
