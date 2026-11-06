import { UserRound } from 'lucide-react';

export function WorkspaceUser({ name, role }: { name?: string; role: string }) {
  return <div className="workspace-user">
    <span className="workspace-user-avatar"><UserRound size={18} aria-hidden="true" /></span>
    <span className="workspace-user-copy"><strong>{name || 'Staff member'}</strong><small>{role}</small></span>
  </div>;
}
