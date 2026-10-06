import { PageHeader } from '../../shared/components/PageHeader';
import { EmptyState } from '../../shared/components/EmptyState';
import { Settings } from 'lucide-react';

export function ComingSoon({ title }: { title: string }) {
  return <div>
    <PageHeader title={title} />
    <div className="workspace-panel"><EmptyState icon={Settings} title="This workspace is not available yet" description="This feature is planned for a future release." /></div>
  </div>;
}
