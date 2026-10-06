import type { ReactNode } from 'react';
import { AlertCircle, LoaderCircle } from 'lucide-react';
import { EmptyState } from './EmptyState';
import { PageHeader } from './PageHeader';

export function WorkspacePage({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return <section className="workspace-page">
    <PageHeader title={title} description={description} />
    {children}
  </section>;
}

export function QueryState({ loading, title, description, onRetry }: { loading?: boolean; title: string; description?: string; onRetry?: () => void }) {
  return <div className="workspace-panel" role={loading ? 'status' : 'alert'} aria-busy={loading}>
    <EmptyState icon={loading ? LoaderCircle : AlertCircle} title={title} description={description}
      secondaryAction={onRetry ? { label: 'Try again', onClick: onRetry } : undefined} />
  </div>;
}
