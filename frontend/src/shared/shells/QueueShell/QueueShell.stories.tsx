import { AlertTriangle, Clock, Inbox, Siren, Stethoscope, UserPlus } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/Badge';
import type { BadgeProps } from '../../components/Badge';
import { Button } from '../../components/Button';
import { QueueShell } from './QueueShell';
import type { QueueFilter, QueueStatus } from './QueueShell';
import styles from './QueueShell.stories.module.css';

/**
 * Plain-React demo for the QueueShell layout — mounted at /dev/shells/queue.
 * Fake triage data, plus a dev bar to flip the loading / error / empty states.
 */

type Priority = 'immediate' | 'urgent' | 'routine';

interface Patient {
  id: string;
  name: string;
  mrn: string;
  priority: Priority;
  waitingMins: number;
  complaint: string;
  vitals: string;
}

const PRIORITY: Record<Priority, { label: string; variant: BadgeProps['variant']; icon: typeof Siren }> = {
  immediate: { label: 'Immediate', variant: 'danger', icon: Siren },
  urgent: { label: 'Urgent', variant: 'warning', icon: AlertTriangle },
  routine: { label: 'Routine', variant: 'neutral', icon: Clock },
};

const PATIENTS: Patient[] = [
  { id: 'e1', name: 'Amina Hassan', mrn: 'MRN-40021', priority: 'immediate', waitingMins: 3, complaint: 'Chest pain, radiating to left arm', vitals: 'BP 90/60 · HR 122 · SpO₂ 92%' },
  { id: 'e2', name: 'John Mushi', mrn: 'MRN-40088', priority: 'urgent', waitingMins: 18, complaint: 'Deep laceration to right forearm', vitals: 'BP 128/82 · HR 96 · Temp 37.1°C' },
  { id: 'e3', name: 'Grace Kimaro', mrn: 'MRN-39975', priority: 'routine', waitingMins: 42, complaint: 'Persistent dry cough, 2 weeks', vitals: 'BP 118/74 · HR 78 · Temp 37.4°C' },
  { id: 'e4', name: 'Samuel Ally', mrn: 'MRN-40109', priority: 'urgent', waitingMins: 25, complaint: 'Severe abdominal pain, lower right', vitals: 'BP 134/88 · HR 104 · Temp 38.2°C' },
  { id: 'e5', name: 'Fatima Juma', mrn: 'MRN-40044', priority: 'routine', waitingMins: 55, complaint: 'Follow-up, blood pressure review', vitals: 'BP 146/92 · HR 80' },
  { id: 'e6', name: 'Peter Nyerere', mrn: 'MRN-40120', priority: 'immediate', waitingMins: 1, complaint: 'Difficulty breathing, wheeze', vitals: 'BP 138/86 · HR 118 · SpO₂ 89%' },
  { id: 'e7', name: 'Rehema Said', mrn: 'MRN-39990', priority: 'routine', waitingMins: 63, complaint: 'Ear pain, left side', vitals: 'BP 120/78 · HR 72 · Temp 37.6°C' },
  { id: 'e8', name: 'Daniel Mrema', mrn: 'MRN-40133', priority: 'urgent', waitingMins: 12, complaint: 'Head injury after fall, no LOC', vitals: 'BP 130/84 · HR 88 · GCS 15' },
];

const FILTERS: QueueFilter[] = [
  { id: 'q', type: 'search', label: 'Search patients', placeholder: 'Name, MRN or complaint' },
  {
    id: 'priority',
    type: 'select',
    label: 'Priority',
    placeholder: 'All priorities',
    options: [
      { value: 'immediate', label: 'Immediate' },
      { value: 'urgent', label: 'Urgent' },
      { value: 'routine', label: 'Routine' },
    ],
  },
];

const STATUSES: QueueStatus[] = ['idle', 'loading', 'error'];

export function QueueShellStories() {
  const [status, setStatus] = useState<QueueStatus>('idle');
  const [emptied, setEmptied] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [actionNote, setActionNote] = useState<string | null>(null);

  const filtered = useMemo(() => {
    if (emptied) return [];
    const q = (filters.q ?? '').trim().toLowerCase();
    const priority = filters.priority ?? '';
    return PATIENTS.filter((p) => {
      if (priority && p.priority !== priority) return false;
      if (q && !`${p.name} ${p.mrn} ${p.complaint}`.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [emptied, filters]);

  const items = status === 'loading' ? [] : filtered;
  const immediateCount = filtered.filter((p) => p.priority === 'immediate').length;

  const simulateRefresh = () => {
    setStatus('loading');
    window.setTimeout(() => setStatus('idle'), 900);
  };

  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>

      <div className={styles.devbar}>
        <span className={styles.devbarLabel}>demo state</span>
        {STATUSES.map((s) => (
          <Button
            key={s}
            size="sm"
            variant={status === s ? 'primary' : 'secondary'}
            onClick={() => setStatus(s)}
          >
            {s}
          </Button>
        ))}
        <Button size="sm" variant="secondary" onClick={() => setEmptied((v) => !v)}>
          {emptied ? 'restore data' : 'empty the list'}
        </Button>
        {actionNote && <span className={styles.actionNote}>{actionNote}</span>}
      </div>

      <div className={styles.shellWrap}>
        <QueueShell<Patient>
          title="Triage queue"
          counts={
            <>
              <Badge size="sm">{filtered.length} waiting</Badge>
              {immediateCount > 0 && (
                <Badge size="sm" variant="danger" leadingIcon={Siren}>
                  {immediateCount} immediate
                </Badge>
              )}
            </>
          }
          headerActions={
            <Button size="sm" variant="secondary" leadingIcon={UserPlus} onClick={() => setActionNote('New walk-in (demo)')}>
              New walk-in
            </Button>
          }
          onRefresh={simulateRefresh}
          items={items}
          getRowId={(p) => p.id}
          renderRow={(p) => {
            const meta = PRIORITY[p.priority];
            return {
              primary: p.name,
              secondary: `${p.mrn} · ${p.complaint}`,
              trailing: (
                <>
                  <Badge size="sm" variant={meta.variant} leadingIcon={meta.icon}>
                    {meta.label}
                  </Badge>
                  {p.waitingMins}m
                </>
              ),
            };
          }}
          detailTitle={(p) => p.name}
          renderDetail={(p) => {
            const meta = PRIORITY[p.priority];
            return (
              <div className={styles.detail}>
                <div className={styles.detailHead}>
                  <h2 className={styles.detailName}>{p.name}</h2>
                  <Badge size="sm" variant={meta.variant} leadingIcon={meta.icon}>
                    {meta.label}
                  </Badge>
                </div>
                <dl className={styles.kv}>
                  <dt>MRN</dt>
                  <dd>{p.mrn}</dd>
                  <dt>Waiting</dt>
                  <dd>{p.waitingMins} minutes</dd>
                  <dt>Complaint</dt>
                  <dd>{p.complaint}</dd>
                  <dt>Vitals</dt>
                  <dd>{p.vitals}</dd>
                </dl>
                <Button leadingIcon={Stethoscope} onClick={() => setActionNote(`Started assessment for ${p.name} (demo)`)}>
                  Start assessment
                </Button>
              </div>
            );
          }}
          selectedId={selectedId}
          onSelectChange={setSelectedId}
          filters={FILTERS}
          filterValues={filters}
          onFilterChange={setFilters}
          status={status}
          onRetry={() => setStatus('idle')}
          emptyIcon={Inbox}
          emptyTitle="No patients waiting for triage"
          emptyDescription="New arrivals appear here as reception checks them in."
        />
      </div>
    </div>
  );
}
