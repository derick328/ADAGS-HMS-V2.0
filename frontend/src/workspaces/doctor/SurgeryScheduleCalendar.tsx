import { useMemo, useState, type CSSProperties } from 'react';

// A month calendar for the Schedule surgery flow. Days on which this doctor
// already has an (active) surgery are painted green with white text; every
// other day is left blank. Selecting a day drives the form's scheduled date
// and reveals that day's surgeries (time + procedure) below — so the doctor
// sees what they're booking alongside before committing a theatre slot.
//
// Green = "active"/"booked" here, consistent with the brand ramp: --green-600
// carries white text at 5.6:1 (tokens.css), so "green and white" is legible;
// --green-signal is used only as the selected-day ring, never behind text.

export interface CalendarSurgery {
  id?: string | null;
  scheduledDate?: string | null; // YYYY-MM-DD (facility-local calendar day)
  timeSlot?: string | null;
  procedureName?: string | null;
  procedureServiceItemCode?: string | null;
  surgeryType?: string | null;
  urgency?: string | null;
  status?: string | null;
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/** Local YYYY-MM-DD for a Date's calendar day, without UTC drift. */
function isoDay(year: number, month: number, day: number): string {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

/** Monday-based weekday index (0 = Mon … 6 = Sun) for a given day. */
function mondayIndex(year: number, month: number, day: number): number {
  return (new Date(year, month, day).getDay() + 6) % 7;
}

export function SurgeryScheduleCalendar({
  surgeries,
  selectedDate,
  minDate,
  onSelectDate,
}: {
  surgeries: CalendarSurgery[];
  selectedDate: string;
  minDate: string;
  onSelectDate: (date: string) => void;
}) {
  // Surgeries grouped by their scheduled day, sorted by time slot within a day.
  const byDay = useMemo(() => {
    const map = new Map<string, CalendarSurgery[]>();
    for (const s of surgeries) {
      const day = s.scheduledDate;
      if (!day) continue;
      const list = map.get(day) ?? [];
      list.push(s);
      map.set(day, list);
    }
    for (const list of map.values()) {
      list.sort((a, b) => (a.timeSlot ?? '').localeCompare(b.timeSlot ?? ''));
    }
    return map;
  }, [surgeries]);

  const anchor = selectedDate || minDate || isoDay(new Date().getFullYear(), new Date().getMonth(), new Date().getDate());
  const [view, setView] = useState(() => {
    const [y, m] = anchor.split('-').map(Number);
    return { year: y, month: m - 1 };
  });

  const firstWeekday = mondayIndex(view.year, view.month, 1);
  const daysInMonth = new Date(view.year, view.month + 1, 0).getDate();
  const cells: (number | null)[] = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];
  while (cells.length % 7 !== 0) cells.push(null);

  // Don't page earlier than the month that contains the earliest selectable
  // day — nothing before today can be scheduled.
  const minMonthKey = minDate ? minDate.slice(0, 7) : '0000-00';
  const viewMonthKey = `${view.year}-${String(view.month + 1).padStart(2, '0')}`;
  const canGoPrev = viewMonthKey > minMonthKey;

  const step = (delta: number) => {
    setView((v) => {
      const d = new Date(v.year, v.month + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });
  };

  const selectedList = selectedDate ? byDay.get(selectedDate) ?? [] : [];

  return (
    <div>
      <div style={calendarShell}>
        <div style={monthBar}>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => step(-1)}
            disabled={!canGoPrev}
            aria-label="Previous month"
            style={navBtn}
          >
            ‹
          </button>
          <strong style={{ fontSize: 'var(--text-sm)' }}>
            {MONTH_NAMES[view.month]} {view.year}
          </strong>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => step(1)}
            aria-label="Next month"
            style={navBtn}
          >
            ›
          </button>
        </div>

        <div style={grid} role="grid" aria-label="Surgery calendar">
          {WEEKDAYS.map((w) => (
            <div key={w} style={weekdayCell} role="columnheader">
              {w}
            </div>
          ))}
          {cells.map((day, i) => {
            if (day == null) return <div key={`pad-${i}`} />;
            const iso = isoDay(view.year, view.month, day);
            const hasSurgery = byDay.has(iso);
            const count = byDay.get(iso)?.length ?? 0;
            const isSelected = iso === selectedDate;
            const isPast = minDate ? iso < minDate : false;
            const isToday = iso === minDate;

            return (
              <button
                key={iso}
                type="button"
                role="gridcell"
                aria-pressed={isSelected}
                aria-label={`${day} ${MONTH_NAMES[view.month]}${hasSurgery ? `, ${count} surgery${count > 1 ? ' items' : ''}` : ', no surgery'}`}
                disabled={isPast}
                onClick={() => onSelectDate(iso)}
                style={dayCellStyle(hasSurgery, isSelected, isPast, isToday)}
              >
                <span>{day}</span>
                {hasSurgery && (
                  <span style={dayCountStyle(isSelected)}>{count}</span>
                )}
              </button>
            );
          })}
        </div>

        <div style={legendRow}>
          <span style={legendItem}>
            <span style={{ ...legendSwatch, background: 'var(--green-600)' }} />
            Has surgery
          </span>
          <span style={legendItem}>
            <span style={{ ...legendSwatch, background: 'var(--bg-surface)', border: '1px solid var(--border-strong)' }} />
            Free
          </span>
        </div>
      </div>

      {selectedDate && (
        <div style={dayDetail}>
          <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)', marginBottom: 'var(--space-2)' }}>
            {selectedList.length > 0
              ? `Your surgeries on ${selectedDate}`
              : `No surgeries scheduled on ${selectedDate}`}
          </div>
          {selectedList.length > 0 && (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
              {selectedList.map((s, i) => (
                <li key={s.id ?? i} style={detailRow}>
                  <span style={timePill}>{s.timeSlot?.trim() || 'Time not set'}</span>
                  <span>
                    <strong>{s.procedureName ?? s.procedureServiceItemCode ?? 'Procedure'}</strong>
                    {s.surgeryType && (
                      <span style={{ color: 'var(--text-muted)' }}> · {s.surgeryType}</span>
                    )}
                    {s.status && s.status !== 'SCHEDULED' && (
                      <span style={{ color: 'var(--text-muted)' }}> · {s.status.replaceAll('_', ' ').toLowerCase()}</span>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

const calendarShell: CSSProperties = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-3)',
};

const monthBar: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  marginBottom: 'var(--space-2)',
};

const navBtn: CSSProperties = {
  padding: '2px 10px',
  fontSize: 'var(--text-lg)',
  lineHeight: 1,
};

const grid: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(7, 1fr)',
  gap: 4,
};

const weekdayCell: CSSProperties = {
  textAlign: 'center',
  fontSize: 'var(--text-xs)',
  color: 'var(--text-muted)',
  padding: '2px 0',
};

function dayCellStyle(hasSurgery: boolean, isSelected: boolean, isPast: boolean, isToday: boolean): CSSProperties {
  const base: CSSProperties = {
    position: 'relative',
    aspectRatio: '1 / 1',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius)',
    font: 'inherit',
    fontSize: 'var(--text-sm)',
    cursor: isPast ? 'default' : 'pointer',
    padding: 0,
  };
  if (isPast) {
    return { ...base, background: 'var(--bg-raised)', color: 'var(--text-muted)', opacity: 0.55 };
  }
  if (hasSurgery) {
    // green + white — the "has surgery" marker
    base.background = 'var(--green-600)';
    base.color = 'var(--white)';
    base.borderColor = 'var(--green-600)';
  } else {
    // blank / none
    base.background = 'var(--bg-surface)';
    base.color = 'var(--text-primary)';
  }
  if (isSelected) {
    base.outline = '2px solid var(--green-signal)';
    base.outlineOffset = '1px';
    base.borderColor = 'var(--green-signal)';
    if (!hasSurgery) base.background = 'var(--green-100)';
  } else if (isToday) {
    base.borderColor = 'var(--green-600)';
  }
  return base;
}

function dayCountStyle(isSelected: boolean): CSSProperties {
  return {
    fontSize: 'var(--text-xs)',
    lineHeight: 1,
    marginTop: 2,
    color: isSelected ? 'var(--text-secondary)' : 'var(--green-200)',
  };
}

const legendRow: CSSProperties = {
  display: 'flex',
  gap: 'var(--space-3)',
  marginTop: 'var(--space-2)',
};

const legendItem: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  fontSize: 'var(--text-xs)',
  color: 'var(--text-muted)',
};

const legendSwatch: CSSProperties = {
  width: 12,
  height: 12,
  borderRadius: 3,
  display: 'inline-block',
};

const dayDetail: CSSProperties = {
  marginTop: 'var(--space-3)',
  background: 'var(--bg-raised)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius)',
  padding: 'var(--space-3)',
};

const detailRow: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 'var(--space-2)',
  fontSize: 'var(--text-sm)',
};

const timePill: CSSProperties = {
  flexShrink: 0,
  fontVariantNumeric: 'tabular-nums',
  fontWeight: 'var(--weight-medium)',
  color: 'var(--green-900)',
  background: 'var(--green-100)',
  borderRadius: 'var(--radius-sm)',
  padding: '1px 6px',
  fontSize: 'var(--text-xs)',
};
