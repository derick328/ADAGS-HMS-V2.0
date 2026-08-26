import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../Button';
import { Toast } from './Toast';
import type { ToastSize, ToastState, ToastVariant } from './Toast';
import styles from './Toast.stories.module.css';

/**
 * Plain-React showcase for the Toast primitive — not Storybook, just a route
 * that renders every variant / size / state so a change can be reviewed
 * visually. Mounted at /dev/components/toast.
 *
 * The static grids use the controlled `state` prop so nothing moves or
 * disappears while you review it. The "Live" section at the bottom mounts real
 * uncontrolled toasts to exercise enter, auto-dismiss, pause-on-hover and
 * manual close.
 */

const VARIANTS: ToastVariant[] = ['success', 'info', 'warning', 'danger'];
const SIZES: ToastSize[] = ['sm', 'md', 'lg'];
const STATES: ToastState[] = ['entering', 'visible', 'exiting'];

function LiveDemo() {
  const [items, setItems] = useState<{ id: number; variant: ToastVariant }[]>([]);
  const nextId = useRef(0);

  const push = (variant: ToastVariant) => {
    setItems((current) => [...current, { id: nextId.current++, variant }]);
  };
  const remove = (id: number) => setItems((current) => current.filter((item) => item.id !== id));

  return (
    <div>
      <div className={styles.triggers}>
        <Button variant="secondary" size="sm" onClick={() => push('success')}>
          Push success (4s)
        </Button>
        <Button variant="secondary" size="sm" onClick={() => push('info')}>
          Push info (4s)
        </Button>
        <Button variant="secondary" size="sm" onClick={() => push('danger')}>
          Push danger (sticky)
        </Button>
      </div>

      <div className={styles.stack} aria-label="Live toasts">
        {items.map(({ id, variant }) => (
          <Toast
            key={id}
            variant={variant}
            title={variant === 'danger' ? 'Couldn’t reach the ward' : 'Patient checked in'}
            description={
              variant === 'danger'
                ? 'The reading is queued and will sync automatically. Hover to keep this open.'
                : 'Amina Hassan added to the triage queue. Hover to pause the 4s timer.'
            }
            duration={variant === 'danger' ? null : 4000}
            action={variant === 'danger' ? { label: 'Retry now', onClick: () => remove(id) } : undefined}
            onClose={() => remove(id)}
          />
        ))}
      </div>
    </div>
  );
}

export function ToastStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Toast</h1>
      <p className={styles.subtitle}>
        Transient floating status message. Self-contained — no global manager. role=&quot;status&quot; for
        success/info, role=&quot;alert&quot; for warning/danger. Motion removed under prefers-reduced-motion.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Variants</h2>
        <p className={styles.sectionHint}>
          Colour + default icon + left accent bar together — never colour alone. Title and body use the neutral
          text tokens for contrast.
        </p>
        <div className={styles.grid}>
          {VARIANTS.map((variant) => (
            <div key={variant} className={styles.cell}>
              <p className={styles.cellLabel}>variant: {variant}</p>
              <Toast
                state="visible"
                variant={variant}
                title={`This is a ${variant} toast`}
                description="A short supporting sentence that explains what just happened."
                showClose
              />
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Sizes</h2>
        <div className={styles.grid}>
          {SIZES.map((size) => (
            <div key={size} className={styles.cell}>
              <p className={styles.cellLabel}>size: {size}</p>
              <Toast
                state="visible"
                variant="info"
                size={size}
                title="3 lab results ready"
                description="Chemistry panel for bed A1 has been verified."
              />
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>States</h2>
        <p className={styles.sectionHint}>
          Controlled via the `state` prop. `entering` and `exiting` resolve to opacity 0 + a small translate, so
          they read as nearly blank here by design — see them animate in the Live section below.
        </p>
        <div className={styles.grid}>
          {STATES.map((state) => (
            <div key={state} className={styles.cell}>
              <p className={styles.cellLabel}>state: {state}</p>
              <Toast state={state} variant="success" title="Saved" description="Vitals recorded for bed A1." />
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Slots</h2>
        <div className={styles.grid}>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>with an action</p>
            <Toast
              state="visible"
              variant="danger"
              title="Couldn’t save the vitals"
              description="The ward is offline. The reading is queued and will sync automatically."
              action={{ label: 'Retry now', onClick: () => {} }}
            />
          </div>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>no close button, no icon</p>
            <Toast
              state="visible"
              variant="info"
              leadingIcon={null}
              showClose={false}
              title="Autosaving…"
              description="Your note is being saved as you type."
            />
          </div>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>title only</p>
            <Toast state="visible" variant="success" size="sm" title="Copied to clipboard" />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Live — enter, auto-dismiss, pause-on-hover, manual close</h2>
        <p className={styles.sectionHint}>
          success / info auto-dismiss after 4s; hovering or focusing a toast pauses that timer and leaving
          resumes it. danger is sticky — close it with the button, the action, or Escape while it has focus.
        </p>
        <LiveDemo />
      </section>
    </div>
  );
}
