import { Stethoscope } from 'lucide-react';

export function WorkspaceBrand({ workspace }: { workspace: string }) {
  return <div className="workspace-brand">
    <span className="workspace-brand-mark"><Stethoscope size={23} aria-hidden="true" /></span>
    <div><strong>ADAGS</strong><span>{workspace}</span></div>
  </div>;
}
