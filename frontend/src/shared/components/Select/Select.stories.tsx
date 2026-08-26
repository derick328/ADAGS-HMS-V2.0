import { Building2, CreditCard } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Select } from './Select';
import type { SelectSize } from './Select';
import styles from './Select.stories.module.css';

/**
 * Plain-React showcase for the Select primitive — not Storybook, just a
 * route that renders every variant/size/state so a change can be reviewed
 * visually. Mounted at /dev/components/select.
 */

const SIZES: SelectSize[] = ['sm', 'md', 'lg'];

const WARD_OPTIONS = [
  { value: 'a1', label: 'Ward A1' },
  { value: 'b2', label: 'Ward B2' },
  { value: 'icu', label: 'ICU' },
];

const PAYER_OPTIONS = [
  { value: 'cash', label: 'Cash' },
  { value: 'nhif', label: 'NHIF' },
  { value: 'insurance', label: 'Private insurance' },
];

export function SelectStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Select</h1>
      <p className={styles.subtitle}>
        Tab to and open the dropdowns below to review the interactive states — they aren&apos;t
        reproducible as static swatches. This is a native &lt;select&gt; throughout.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Sizes</h2>
        <div className={styles.panel}>
          <div className={styles.grid}>
            {SIZES.map((size) => (
              <Select key={size} label={`Size: ${size}`} size={size} placeholder="Select a ward" options={WARD_OPTIONS} />
            ))}
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Variants</h2>
        <div className={styles.panel}>
          <div className={styles.grid}>
            <Select label="Default" placeholder="Select a ward" options={WARD_OPTIONS} helperText="Where the patient is currently admitted" />
            <Select label="Error" placeholder="Select a payer type" options={PAYER_OPTIONS} errorText="Select a payer type" />
            <Select label="Success" defaultValue="nhif" options={PAYER_OPTIONS} variant="success" helperText="Verified against the payer registry" />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>With a leading icon</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            <div className={styles.cell}>
              <Select label="Ward" leadingIcon={Building2} placeholder="Select a ward" options={WARD_OPTIONS} />
            </div>
            <div className={styles.cell}>
              <Select label="Payer type" leadingIcon={CreditCard} placeholder="Select a payer type" options={PAYER_OPTIONS} />
            </div>
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Required and disabled</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            <div className={styles.cell}>
              <Select label="Facility" required placeholder="Select a facility" options={WARD_OPTIONS} />
            </div>
            <div className={styles.cell}>
              <Select label="Ward" defaultValue="icu" options={WARD_OPTIONS} disabled />
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
