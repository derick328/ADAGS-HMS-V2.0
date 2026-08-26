import { LogIn, Plus, Trash2 } from 'lucide-react';
import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import { Button } from './Button';
import type { ButtonSize, ButtonVariant } from './Button';
import styles from './Button.stories.module.css';

/**
 * Plain-React showcase for the Button primitive — not Storybook, just a
 * route that renders every variant/size/state so a change can be reviewed
 * visually. Mounted at /dev/components/button.
 */

const VARIANTS: ButtonVariant[] = ['primary', 'secondary', 'ghost', 'danger'];
const SIZES: ButtonSize[] = ['sm', 'md', 'lg'];

export function ButtonStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Button</h1>
      <p className={styles.subtitle}>
        Hover and tab through the buttons below to review the interactive states —
        they aren&apos;t reproducible as static swatches.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Variant × size</h2>
        <div className={styles.panel}>
          <div className={styles.grid}>
            <span />
            {SIZES.map((size) => (
              <span key={size} className={styles.rowLabel}>
                {size}
              </span>
            ))}

            {VARIANTS.map((variant) => (
              <Fragment key={variant}>
                <span className={styles.rowLabel}>{variant}</span>
                {SIZES.map((size) => (
                  <Button key={`${variant}-${size}`} variant={variant} size={size}>
                    Button
                  </Button>
                ))}
              </Fragment>
            ))}
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>With icons</h2>
        <p className={styles.sectionHint}>Leading, trailing, and both — icons render at 16px, stroke width 2.</p>
        <div className={styles.panel}>
          <div className={styles.row}>
            <Button leadingIcon={Plus}>Add patient</Button>
            <Button variant="secondary" trailingIcon={LogIn}>
              Continue
            </Button>
            <Button variant="danger" leadingIcon={Trash2}>
              Delete
            </Button>
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Disabled</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            {VARIANTS.map((variant) => (
              <Button key={variant} variant={variant} disabled>
                Button
              </Button>
            ))}
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Loading</h2>
        <p className={styles.sectionHint}>Width is preserved — compare against the equivalent button above.</p>
        <div className={styles.panel}>
          <div className={styles.row}>
            {VARIANTS.map((variant) => (
              <Button key={variant} variant={variant} loading>
                Sign in
              </Button>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
