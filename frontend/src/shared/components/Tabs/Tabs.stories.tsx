import { Activity, CreditCard, FileText, FlaskConical, Pill } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../Button';
import { Tabs } from './Tabs';
import type { TabItem, TabsSize, TabsVariant } from './Tabs';
import styles from './Tabs.stories.module.css';

/**
 * Plain-React showcase for the Tabs primitive — not Storybook, just a route
 * that renders every variant / size / state so a change can be reviewed
 * visually and driven from the keyboard. Mounted at /dev/components/tabs.
 */

const VARIANTS: TabsVariant[] = ['default', 'enclosed', 'underline'];
const SIZES: TabsSize[] = ['sm', 'md', 'lg'];

function panel(text: string) {
  return <p className={styles.paragraph}>{text}</p>;
}

const TEXT_ITEMS: TabItem[] = [
  { id: 'summary', label: 'Summary', content: panel('Encounter summary — arrival, triage priority, assigned clinician.') },
  { id: 'orders', label: 'Orders', content: panel('Lab and pharmacy orders placed during this encounter.') },
  { id: 'notes', label: 'Notes', content: panel('Clinical notes, amended never overwritten.') },
];

const ICON_ITEMS: TabItem[] = [
  { id: 'vitals', label: 'Vitals', icon: Activity, content: panel('Blood pressure, pulse, temperature, respiratory rate.') },
  { id: 'labs', label: 'Labs', icon: FlaskConical, content: panel('Results with their verification level snapshotted.') },
  { id: 'meds', label: 'Medications', icon: Pill, content: panel('Active prescriptions and administration record.') },
  { id: 'billing', label: 'Billing', icon: CreditCard, disabled: true, content: panel('Not available on this workspace.') },
];

function VariantSizeCell({ variant, size }: { variant: TabsVariant; size: TabsSize }) {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>
        {variant} · {size}
      </p>
      <Tabs aria-label={`${variant} ${size} example`} variant={variant} size={size} items={TEXT_ITEMS} />
    </div>
  );
}

function IconsAndDisabledCell({ variant }: { variant: TabsVariant }) {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>{variant} · icons + disabled tab</p>
      <p className={styles.cellHint}>Arrow keys skip the disabled &ldquo;Billing&rdquo; tab; Home / End jump to the ends.</p>
      <Tabs aria-label={`${variant} icons example`} variant={variant} items={ICON_ITEMS} />
    </div>
  );
}

function ControlledCell() {
  const [tab, setTab] = useState('orders');
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>controlled</p>
      <div className={styles.controls}>
        {TEXT_ITEMS.map((item) => (
          <Button key={item.id} variant="secondary" size="sm" onClick={() => setTab(item.id)}>
            Go to {item.id}
          </Button>
        ))}
      </div>
      <Tabs aria-label="Controlled example" value={tab} onChange={setTab} items={TEXT_ITEMS} />
      <p className={styles.state}>value: {tab}</p>
    </div>
  );
}

function UncontrolledCell() {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>uncontrolled · defaultValue=&quot;notes&quot;</p>
      <Tabs aria-label="Uncontrolled example" defaultValue="notes" items={TEXT_ITEMS} />
    </div>
  );
}

function ManualActivationCell() {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>activation=&quot;manual&quot;</p>
      <p className={styles.cellHint}>Arrows move focus only; Enter / Space or click selects.</p>
      <Tabs aria-label="Manual activation example" variant="underline" activation="manual" items={TEXT_ITEMS} />
    </div>
  );
}

function LabelledByCell() {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel} id="tabs-story-report-heading">
        aria-labelledby + unmountInactivePanels
      </p>
      <Tabs
        aria-labelledby="tabs-story-report-heading"
        variant="enclosed"
        size="lg"
        unmountInactivePanels
        items={[
          { id: 'revenue', label: 'Revenue', icon: CreditCard, content: panel('Inactive panels are removed from the DOM here.') },
          { id: 'expenses', label: 'Expenses', icon: FileText, content: panel('Switching remounts this panel fresh.') },
        ]}
      />
    </div>
  );
}

export function TabsStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Tabs</h1>
      <p className={styles.subtitle}>
        WAI-ARIA tabs pattern — tablist / tab / tabpanel with roving tabIndex, ArrowLeft / ArrowRight
        (wrapping, skipping disabled), Home / End, automatic or manual activation, and each panel
        reachable by Tab. Focus-visible via the global ring. Motion removed under
        prefers-reduced-motion. Tokens only.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Variants &times; sizes</h2>
        <p className={styles.sectionHint}>default (tinted) · enclosed (folder) · underline — each at sm / md / lg.</p>
        <div className={styles.grid}>
          {VARIANTS.map((variant) =>
            SIZES.map((size) => <VariantSizeCell key={`${variant}-${size}`} variant={variant} size={size} />),
          )}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Icons &amp; disabled tabs</h2>
        <p className={styles.sectionHint}>Leading Lucide icon per tab; disabled tabs are out of the tab sequence and arrow nav.</p>
        <div className={styles.grid}>
          {VARIANTS.map((variant) => (
            <IconsAndDisabledCell key={variant} variant={variant} />
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>State &amp; activation</h2>
        <p className={styles.sectionHint}>Controlled / uncontrolled, manual activation, aria-labelledby, panel unmounting.</p>
        <div className={styles.grid}>
          <ControlledCell />
          <UncontrolledCell />
          <ManualActivationCell />
          <LabelledByCell />
        </div>
      </section>
    </div>
  );
}
