import { CalendarX2, Inbox, Plus, RefreshCw, SearchX, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import { EmptyState } from './EmptyState';
import type { EmptyStateVariant } from './EmptyState';
import styles from './EmptyState.stories.module.css';

/**
 * Plain-React showcase for the EmptyState primitive — not Storybook, just a
 * route that renders every variant / slot combination so a change can be
 * reviewed visually. Mounted at /dev/components/emptystate.
 *
 * Every cell frames the EmptyState in a bordered panel, because that's how it
 * ships: the primitive itself is transparent and inherits its host's edge.
 */

const VARIANTS: EmptyStateVariant[] = ['default', 'compact', 'full'];

/** A tiny inline illustration, to exercise the `illustration` slot. */
function WardIllustration() {
  return (
    <svg width="72" height="72" viewBox="0 0 72 72" fill="none" aria-hidden="true">
      <rect x="6" y="20" width="60" height="40" rx="4" stroke="var(--border-strong)" strokeWidth="2" />
      <path d="M6 34h60" stroke="var(--border-strong)" strokeWidth="2" />
      <circle cx="20" cy="27" r="3" fill="var(--text-muted)" />
      <path d="M18 52h16M18 46h28" stroke="var(--text-muted)" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

export function EmptyStateStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>EmptyState</h1>
      <p className={styles.subtitle}>
        Shown in place of absent content. Transparent — no border or fill of its own — so it inherits its host
        container&apos;s edge. Title is a real heading; actions are real Buttons.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Variants</h2>
        <p className={styles.sectionHint}>
          default → a queue/list inside a panel · compact → a small results area · full → a first-run splash that
          fills the region.
        </p>
        <div className={styles.grid}>
          {VARIANTS.map((variant) => (
            <div key={variant} className={styles.cell}>
              <p className={styles.cellLabel}>variant: {variant}</p>
              <EmptyState
                variant={variant}
                icon={Inbox}
                title="No patients waiting for triage"
                description="New arrivals appear here as reception checks them in."
              />
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>With actions</h2>
        <p className={styles.sectionHint}>
          primaryAction / secondaryAction take a {'{ label, ...ButtonProps }'} object and render as Button —
          secondary sits left, primary right, matching the Card footer pattern.
        </p>
        <div className={styles.grid}>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>primary only</p>
            <EmptyState
              icon={Inbox}
              title="No patients waiting for triage"
              description="New arrivals appear here as reception checks them in."
              primaryAction={{ label: 'Register a walk-in', leadingIcon: Plus, onClick: () => {} }}
            />
          </div>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>primary + secondary</p>
            <EmptyState
              icon={Users}
              title="This ward has no admitted patients"
              description="Once a doctor admits a patient here, their bed and vitals show up on this screen."
              primaryAction={{ label: 'View admission requests', onClick: () => {} }}
              secondaryAction={{ label: 'Read the ward guide', variant: 'ghost', onClick: () => {} }}
            />
          </div>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>compact + one action</p>
            <EmptyState
              variant="compact"
              role="status"
              icon={SearchX}
              title="No patients match “amina h.”"
              description="Check the spelling, or search by phone number instead."
              secondaryAction={{ label: 'Clear search', leadingIcon: RefreshCw, onClick: () => {} }}
            />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Illustration slot</h2>
        <p className={styles.sectionHint}>
          Pass `illustration` for a custom mark; it takes the icon slot and wins over `icon`. You own its
          accessible semantics (this one is aria-hidden).
        </p>
        <div className={styles.grid}>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>variant: full, illustration</p>
            <EmptyState
              variant="full"
              illustration={<WardIllustration />}
              title="Your shift has no scheduled rounds"
              description="Rounds added by the charge nurse will be listed here in start-time order."
              primaryAction={{ label: 'Go to today’s schedule', onClick: () => {} }}
            />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Narrow container</h2>
        <p className={styles.sectionHint}>
          A 280px host. The measure stays readable and the two actions wrap to stacked rows — no breakpoint
          needed.
        </p>
        <div className={styles.grid}>
          <div className={`${styles.cell} ${styles.narrow}`}>
            <p className={styles.cellLabel}>280px wide</p>
            <EmptyState
              icon={CalendarX2}
              title="No appointments booked for today"
              description="Bookings made at reception show up here as the day fills in."
              primaryAction={{ label: 'Book appointment', onClick: () => {} }}
              secondaryAction={{ label: 'View next week', onClick: () => {} }}
            />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Heading structure</h2>
        <p className={styles.sectionHint}>
          `title` renders as a real heading — `headingLevel` (default 2) sets the tag so the primitive fits the
          page outline. Inspect the DOM: the left is an h2, the right an h3.
        </p>
        <div className={styles.grid}>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>headingLevel: 2 (default)</p>
            <EmptyState variant="compact" title="No results" description="Rendered as <h2>." />
          </div>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>headingLevel: 3</p>
            <EmptyState variant="compact" headingLevel={3} title="No results" description="Rendered as <h3>." />
          </div>
        </div>
      </section>
    </div>
  );
}
