import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Checkbox } from './Checkbox';
import type { CheckboxSize } from './Checkbox';
import styles from './Checkbox.stories.module.css';

/**
 * Plain-React showcase for the Checkbox primitive — not Storybook, just a
 * route that renders every variant/size/state so a change can be reviewed
 * visually. Mounted at /dev/components/checkbox.
 */

const SIZES: CheckboxSize[] = ['sm', 'md', 'lg'];

function SelectAllDemo() {
  const [checkedIds, setCheckedIds] = useState<string[]>(['a1']);
  const items = [
    { id: 'a1', label: 'Ward A1' },
    { id: 'b2', label: 'Ward B2' },
    { id: 'icu', label: 'ICU' },
  ];
  const allChecked = checkedIds.length === items.length;
  const someChecked = checkedIds.length > 0 && !allChecked;

  const toggleAll = () => setCheckedIds(allChecked ? [] : items.map((item) => item.id));
  const toggleOne = (id: string) =>
    setCheckedIds((current) => (current.includes(id) ? current.filter((c) => c !== id) : [...current, id]));

  return (
    <div className={styles.stack}>
      <Checkbox label="Select all" checked={allChecked} indeterminate={someChecked} onChange={toggleAll} />
      <div className={`${styles.stack} ${styles.indent}`}>
        {items.map((item) => (
          <Checkbox
            key={item.id}
            label={item.label}
            checked={checkedIds.includes(item.id)}
            onChange={() => toggleOne(item.id)}
          />
        ))}
      </div>
    </div>
  );
}

export function CheckboxStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Checkbox</h1>
      <p className={styles.subtitle}>
        Click and tab through the checkboxes below to review the interactive states — they
        aren&apos;t reproducible as static swatches.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Sizes</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            {SIZES.map((size) => (
              <Checkbox key={size} label={`Size: ${size}`} size={size} defaultChecked />
            ))}
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>States</h2>
        <div className={styles.panel}>
          <div className={styles.stack}>
            <Checkbox label="Unchecked" />
            <Checkbox label="Checked" defaultChecked />
            <Checkbox label="With helper text" helperText="Applies to all encounters in this ward" />
            <Checkbox label="Required, unchecked" required errorText="You must confirm this before continuing" />
            <Checkbox label="Disabled, unchecked" disabled />
            <Checkbox label="Disabled, checked" disabled defaultChecked />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Indeterminate — select all</h2>
        <p className={styles.sectionHint}>
          A live example: the parent checkbox is set indeterminate via the DOM property, not a
          static attribute — it only makes sense wired to real state.
        </p>
        <div className={styles.panel}>
          <SelectAllDemo />
        </div>
      </section>
    </div>
  );
}
