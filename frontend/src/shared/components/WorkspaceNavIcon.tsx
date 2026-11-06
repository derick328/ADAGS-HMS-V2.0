import { Activity, BookOpen, CalendarDays, ClipboardList, CreditCard, FileClock, FlaskConical, History, LayoutDashboard, Package, PackageCheck, Pill, Search, Settings2, Stethoscope, UserRound, Users, Wallet, type LucideIcon } from 'lucide-react';

const icons: Record<string, LucideIcon> = {
  '/receptionist': LayoutDashboard,
  '/receptionist/patient-intake': UserRound,
  '/receptionist/billing': CreditCard,
  '/receptionist/expected-returns': CalendarDays,
  '/receptionist/patient-records': ClipboardList,
  '/receptionist/price-lookup': Search,
  '/receptionist/shift': Wallet,
  '/receptionist/profile': Settings2,
  '/doctor': Activity,
  '/doctor/patients': Users,
  '/doctor/clinic-follow-ups': CalendarDays,
  '/doctor/admitted': Stethoscope,
  '/doctor/scheduled-surgeries': ClipboardList,
  '/doctor/catalogue': BookOpen,
  '/doctor/request-history': FileClock,
  '/doctor/profile': Settings2,
  '/pharmacy': LayoutDashboard,
  '/pharmacy/dispensing': Pill,
  '/pharmacy/history': History,
  '/pharmacy/stock': Package,
  '/pharmacy/items': PackageCheck,
  '/pharmacy/lab-stock-requests': FlaskConical,
  '/pharmacy/profile': Settings2,
};

export function WorkspaceNavIcon({ path }: { path: string }) {
  const Icon = icons[path] ?? ClipboardList;
  return <Icon className="workspace-nav-icon" size={18} strokeWidth={1.9} aria-hidden="true" />;
}
