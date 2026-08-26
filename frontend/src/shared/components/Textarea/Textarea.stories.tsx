import { FileText, StickyNote } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Textarea } from './Textarea';
import type { TextareaSize } from './Textarea';
import styles from './Textarea.stories.module.css';

/**
 * Plain-React showcase for the Textarea primitive — not Storybook, just a
 * route that renders every variant/size/state so a change can be reviewed
 * visually. Mounted at /dev/components/textarea.
 */

const SIZES: TextareaSize[] = ['sm', 'md', 'lg'];

export function TextareaStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Textarea</h1>
      <p className={styles.subtitle}>
        Hover, click into, and tab through the fields below to review the interactive states —
        they aren&apos;t reproducible as static swatches.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Sizes</h2>
        <div className={styles.panel}>
          <div className={styles.grid}>
            {SIZES.map((size) => (
              <Textarea key={size} label={`Size: ${size}`} size={size} rows={3} placeholder="Type here" />
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
            <Textarea label="Default" placeholder="Clinical notes" helperText="Visible to the care team only" />
            <Textarea
              label="Error"
              defaultValue="Pt c/o"
              errorText="Describe the presenting complaint in full"
            />
            <Textarea
              label="Success"
              variant="success"
              defaultValue="Reviewed and countersigned."
              helperText="Saved to the encounter record"
            />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>With icons</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            <div className={styles.cell}>
              <Textarea label="Notes" leadingIcon={StickyNote} placeholder="Add a note for the next shift" />
            </div>
            <div className={styles.cell}>
              <Textarea label="Referral letter" leadingIcon={FileText} rows={4} placeholder="Dear colleague," />
            </div>
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Character count</h2>
        <p className={styles.sectionHint}>
          maxLength still enforces the native limit — this is a read-out of it, not a separate check.
        </p>
        <div className={styles.panel}>
          <div className={styles.row}>
            <div className={styles.cell}>
              <Textarea
                label="Discharge summary"
                maxLength={120}
                showCharacterCount
                defaultValue="Afebrile, tolerating oral intake, ambulatory."
              />
            </div>
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Required, disabled, read-only, non-resizable</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            <div className={styles.cell}>
              <Textarea label="Reason for visit" required placeholder="Enter the reason for visit" />
            </div>
            <div className={styles.cell}>
              <Textarea label="Triage note" defaultValue="IMMEDIATE — chest pain, 45m." disabled />
            </div>
            <div className={styles.cell}>
              <Textarea label="Admitted by" defaultValue="Reception desk" readOnly />
            </div>
            <div className={styles.cell}>
              <Textarea label="Fixed-height note" resize="none" rows={2} placeholder="Can't be resized" />
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
