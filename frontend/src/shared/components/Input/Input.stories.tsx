import { Lock, Mail, User } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Input } from './Input';
import type { InputSize } from './Input';
import styles from './Input.stories.module.css';

/**
 * Plain-React showcase for the Input primitive — not Storybook, just a
 * route that renders every variant/size/state so a change can be reviewed
 * visually. Mounted at /dev/components/input.
 */

const SIZES: InputSize[] = ['sm', 'md', 'lg'];

export function InputStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Input</h1>
      <p className={styles.subtitle}>
        Hover, click into, and tab through the fields below to review the interactive states —
        they aren&apos;t reproducible as static swatches.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Sizes</h2>
        <div className={styles.panel}>
          <div className={styles.grid}>
            {SIZES.map((size) => (
              <Input key={size} label={`Size: ${size}`} size={size} placeholder="Type here" />
            ))}
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Variants</h2>
        <p className={styles.sectionHint}>
          Error and success auto-render a status icon when no trailingIcon is given, so the state
          is never colour alone.
        </p>
        <div className={styles.panel}>
          <div className={styles.grid}>
            <Input label="Default" placeholder="Full name" helperText="As it appears on the patient's ID" />
            <Input label="Error" placeholder="Full name" defaultValue="J" errorText="Enter the full name" />
            <Input label="Success" placeholder="Full name" defaultValue="Amina Hassan" variant="success" helperText="Matches the ID on file" />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>With icons</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            <div className={styles.cell}>
              <Input label="Username" leadingIcon={User} placeholder="jdoe" />
            </div>
            <div className={styles.cell}>
              <Input label="Email" leadingIcon={Mail} type="email" placeholder="name@facility.org" />
            </div>
            <div className={styles.cell}>
              <Input label="Password" leadingIcon={Lock} type="password" placeholder="••••••••" />
            </div>
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Required, disabled, read-only</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            <div className={styles.cell}>
              <Input label="Facility" required placeholder="Enter a facility" />
            </div>
            <div className={styles.cell}>
              <Input label="Patient ID" defaultValue="P-2026-0417" disabled />
            </div>
            <div className={styles.cell}>
              <Input label="Registered by" defaultValue="Reception desk" readOnly />
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
