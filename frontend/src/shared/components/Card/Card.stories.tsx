import { ChevronRight, User } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../Badge';
import { Button } from '../Button';
import { Card } from './Card';
import type { CardPadding, CardVariant } from './Card';
import styles from './Card.stories.module.css';

/**
 * Plain-React showcase for the Card primitive — not Storybook, just a route
 * that renders every variant/size/state so a change can be reviewed
 * visually. Mounted at /dev/components/card.
 */

const VARIANTS: CardVariant[] = ['default', 'elevated', 'outlined'];
const PADDINGS: CardPadding[] = ['sm', 'md', 'lg'];

function InteractiveDemo() {
  const [clicks, setClicks] = useState(0);
  return (
    <div className={styles.grid}>
      <Card
        variant="interactive"
        onClick={() => setClicks((c) => c + 1)}
        leadingIcon={User}
        header="Amina Hassan"
        trailingAction={<ChevronRight size={16} aria-hidden="true" />}
      >
        <p className={styles.body}>Ward A1 · Admitted 2 days ago</p>
      </Card>
      <p className={styles.meta}>
        Clicked {clicks} time{clicks === 1 ? '' : 's'} — try both a mouse click and Tab + Enter/Space.
      </p>
    </div>
  );
}

export function CardStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Card</h1>
      <p className={styles.subtitle}>
        A plain container by default — only variant=&quot;interactive&quot; below is keyboard-operable.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Variants</h2>
        <div className={styles.grid}>
          {VARIANTS.map((variant) => (
            <Card key={variant} variant={variant} header={variant}>
              <p className={styles.body}>
                {variant === 'default' && 'Hairline border, no shadow — the default flush surface.'}
                {variant === 'elevated' && 'The raised shadow level, for content that sits slightly above the page.'}
                {variant === 'outlined' && 'Same surface as default, with a more emphasized border.'}
              </p>
            </Card>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Padding sizes</h2>
        <div className={styles.grid}>
          {PADDINGS.map((padding) => (
            <Card key={padding} padding={padding} header={`Padding: ${padding}`}>
              <p className={styles.body}>Body content.</p>
            </Card>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Composition — header, icon, trailing action, footer</h2>
        <div className={styles.grid}>
          <Card
            leadingIcon={User}
            header="Amina Hassan"
            trailingAction={<Badge variant="success">Active</Badge>}
            footer={
              <>
                <Button variant="secondary" size="sm">
                  Dismiss
                </Button>
                <Button size="sm">View chart</Button>
              </>
            }
          >
            <p className={styles.body}>Ward A1 · Admitted 2 days ago · Payer: NHIF</p>
          </Card>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Interactive — keyboard-operable</h2>
        <p className={styles.sectionHint}>
          Only this variant gets role=&quot;button&quot;, focus styling, and Enter/Space support.
        </p>
        <InteractiveDemo />
      </section>
    </div>
  );
}
