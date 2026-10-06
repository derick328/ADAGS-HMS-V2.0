import type { ReactNode } from 'react';

/** Keep every column available on smaller screens, including keyboard scrolling. */
export function TableRegion({ children, label = 'Data table' }: { children: ReactNode; label?: string }) {
  return <div className="table-region" role="region" aria-label={label} tabIndex={0}>{children}</div>;
}
