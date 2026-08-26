import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Radio } from './Radio';
import type { RadioSize } from './Radio';
import styles from './Radio.stories.module.css';

/**
 * Plain-React showcase for the Radio primitive — not Storybook, just a
 * route that renders every variant/size/state so a change can be reviewed
 * visually. Mounted at /dev/components/radio.
 */

const SIZES: RadioSize[] = ['sm', 'md', 'lg'];

function ControlledGroupDemo() {
  const [priority, setPriority] = useState('routine');
  return (
    <div className={styles.stack}>
      {['routine', 'urgent', 'immediate'].map((value) => (
        <Radio
          key={value}
          name="triage-priority-demo"
          value={value}
          label={value === 'immediate' ? 'IMMEDIATE' : value.charAt(0).toUpperCase() + value.slice(1)}
          checked={priority === value}
          onChange={() => setPriority(value)}
        />
      ))}
    </div>
  );
}

export function RadioStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Radio</h1>
      <p className={styles.subtitle}>
        Click and tab (then use arrow keys within a group) through the radios below to review the
        interactive states — they aren&apos;t reproducible as static swatches.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Sizes</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            {SIZES.map((size) => (
              <Radio key={size} name={`size-demo-${size}`} label={`Size: ${size}`} size={size} defaultChecked />
            ))}
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>States</h2>
        <div className={styles.panel}>
          <div className={styles.stack}>
            <Radio name="states-demo-a" value="unchecked" label="Unchecked" />
            <Radio name="states-demo-b" value="checked" label="Checked" defaultChecked />
            <Radio name="states-demo-c" value="helper" label="With helper text" helperText="Applies to this encounter only" />
            <Radio name="states-demo-d" value="error" label="Error" errorText="Select an option to continue" />
            <Radio name="states-demo-e" value="disabled" label="Disabled, unchecked" disabled />
            <Radio name="states-demo-f" value="disabled-checked" label="Disabled, checked" disabled defaultChecked />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Grouped, controlled</h2>
        <p className={styles.sectionHint}>
          A shared `name` gives this native radio-group behaviour — single selection, arrow-key
          navigation — for free.
        </p>
        <div className={styles.panel}>
          <ControlledGroupDemo />
        </div>
      </section>
    </div>
  );
}
