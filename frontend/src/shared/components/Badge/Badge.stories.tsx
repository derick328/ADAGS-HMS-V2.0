import { AlertTriangle, Check, Clock } from 'lucide-react';
import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from './Badge';
import type { BadgeSize, BadgeVariant } from './Badge';
import styles from './Badge.stories.module.css';

/**
 * Plain-React showcase for the Badge primitive — not Storybook, just a
 * route that renders every variant/size/state so a change can be reviewed
 * visually. Mounted at /dev/components/badge.
 */

const VARIANTS: BadgeVariant[] = ['neutral', 'primary', 'success', 'warning', 'danger', 'info'];
const SIZES: BadgeSize[] = ['sm', 'md', 'lg'];

export function BadgeStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Badge</h1>
      <p className={styles.subtitle}>Non-interactive by default — a plain, compact status tag.</p>

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
                  <Badge key={`${variant}-${size}`} variant={variant} size={size}>
                    Label
                  </Badge>
                ))}
              </Fragment>
            ))}
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>With icons</h2>
        <div className={styles.panel}>
          <div className={styles.row}>
            <Badge variant="success" leadingIcon={Check}>
              Verified
            </Badge>
            <Badge variant="danger" leadingIcon={AlertTriangle}>
              Overdue
            </Badge>
            <Badge variant="warning" trailingIcon={Clock}>
              Expiring soon
            </Badge>
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Dot indicator</h2>
        <p className={styles.sectionHint}>The dot colour always matches the variant's text colour.</p>
        <div className={styles.panel}>
          <div className={styles.row}>
            {VARIANTS.map((variant) => (
              <Badge key={variant} variant={variant} dot>
                {variant}
              </Badge>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
