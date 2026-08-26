import { Link } from 'react-router-dom';
import { Skeleton } from './Skeleton';
import type { SkeletonSize, SkeletonWidth } from './Skeleton';
import styles from './Skeleton.stories.module.css';

/**
 * Plain-React showcase for the Skeleton primitive — not Storybook, just a
 * route that renders every variant / size / width so a change can be
 * reviewed visually. Mounted at /dev/components/skeleton.
 */

const SIZES: SkeletonSize[] = ['sm', 'md', 'lg'];
const WIDTHS: SkeletonWidth[] = ['full', '3/4', '1/2', '1/4'];

export function SkeletonStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Skeleton</h1>
      <p className={styles.subtitle}>
        Decorative loading placeholder. Gentle opacity pulse (no gradient), stilled under
        prefers-reduced-motion, aria-hidden unless given a label.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Text — sizes</h2>
        <p className={styles.sectionHint}>Line height comes from the type scale: sm 12px, md 14px, lg 16px.</p>
        <div className={styles.grid}>
          {SIZES.map((size) => (
            <div key={size} className={styles.cell}>
              <p className={styles.cellLabel}>size: {size}</p>
              <Skeleton variant="text" size={size} />
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Text — width fractions</h2>
        <div className={styles.grid}>
          {WIDTHS.map((width) => (
            <div key={width} className={styles.cell}>
              <p className={styles.cellLabel}>width: {width}</p>
              <Skeleton variant="text" width={width} />
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Text — multi-line</h2>
        <p className={styles.sectionHint}>lines &gt; 1 stacks bars with the last one shortened, like ragged copy.</p>
        <div className={styles.grid}>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>lines: 3</p>
            <Skeleton lines={3} />
          </div>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>lines: 5, size sm</p>
            <Skeleton lines={5} size="sm" />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Circular — sizes</h2>
        <p className={styles.sectionHint}>Diameter from the spacing scale: sm 24px, md 32px, lg 40px. width is ignored.</p>
        <div className={styles.grid}>
          {SIZES.map((size) => (
            <div key={size} className={styles.cell}>
              <p className={styles.cellLabel}>size: {size}</p>
              <Skeleton variant="circular" size={size} />
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Rectangular — sizes</h2>
        <p className={styles.sectionHint}>Block height from the spacing scale: sm 40px, md 80px, lg 160px.</p>
        <div className={styles.grid}>
          {SIZES.map((size) => (
            <div key={size} className={styles.cell}>
              <p className={styles.cellLabel}>size: {size}</p>
              <Skeleton variant="rectangular" size={size} />
            </div>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Composition — an avatar + name/meta row</h2>
        <div className={styles.grid}>
          <div className={`${styles.cell} ${styles.narrow}`}>
            <p className={styles.cellLabel}>list item skeleton</p>
            <div className={styles.row}>
              <Skeleton variant="circular" size="md" />
              <div className={styles.rowText}>
                <Skeleton variant="text" width="3/4" />
                <Skeleton variant="text" size="sm" width="1/2" />
              </div>
            </div>
          </div>
          <div className={`${styles.cell} ${styles.narrow}`}>
            <p className={styles.cellLabel}>card skeleton</p>
            <Skeleton variant="rectangular" size="md" />
            <Skeleton variant="text" width="3/4" />
            <Skeleton lines={2} size="sm" />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Accessibility</h2>
        <p className={styles.sectionHint}>
          Left: default — aria-hidden, no role (the parent owns the loading state). Right: with a label — becomes a
          polite role=&quot;status&quot; live region. Inspect the DOM to see the difference.
        </p>
        <div className={styles.grid}>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>&lt;Skeleton lines=&#123;2&#125; /&gt;</p>
            <Skeleton lines={2} />
          </div>
          <div className={styles.cell}>
            <p className={styles.cellLabel}>&lt;Skeleton ... label=&quot;Loading chart&quot; /&gt;</p>
            <Skeleton variant="rectangular" size="sm" label="Loading chart" />
          </div>
        </div>
      </section>
    </div>
  );
}
