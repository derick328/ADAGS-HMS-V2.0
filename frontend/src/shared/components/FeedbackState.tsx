import { ClipboardList, LoaderCircle } from 'lucide-react';
import { EmptyState } from './EmptyState';

/** Contextual feedback for lists, keeping the page's original message. */
export function FeedbackState({ children, loading = false }: { children: string; loading?: boolean }) {
  return <div className="feedback-state" role="status" aria-busy={loading}>
    <EmptyState variant="compact" icon={loading ? LoaderCircle : ClipboardList} title={children} />
  </div>;
}
