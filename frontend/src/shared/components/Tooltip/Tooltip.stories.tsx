import { Info, Printer } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../Button';
import { Tooltip } from './Tooltip';
import type { TooltipPlacement, TooltipSize } from './Tooltip';
import styles from './Tooltip.stories.module.css';

/**
 * Plain-React showcase for the Tooltip primitive — not Storybook, just a route
 * that renders every placement / size / state for visual and keyboard review.
 * Mounted at /dev/components/tooltip.
 */

const PLACEMENTS: TooltipPlacement[] = ['top', 'right', 'bottom', 'left'];
const SIZES: TooltipSize[] = ['sm', 'md'];

function PlacementSizeCell({ placement, size }: { placement: TooltipPlacement; size: TooltipSize }) {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>
        {placement} · {size}
      </p>
      <Tooltip placement={placement} size={size} content={`Tooltip on the ${placement}`}>
        <Button variant="secondary" size="sm">
          Hover or focus
        </Button>
      </Tooltip>
    </div>
  );
}

function DelayCell() {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>configurable delay</p>
      <p className={styles.cellHint}>Hover open delays differ; keyboard focus always opens immediately.</p>
      <div className={styles.row}>
        <Tooltip delay={0} content="No open delay">
          <Button variant="secondary" size="sm">
            0 ms
          </Button>
        </Tooltip>
        <Tooltip content="Default 300 ms open">
          <Button variant="secondary" size="sm">
            default
          </Button>
        </Tooltip>
        <Tooltip delay={{ open: 800, close: 400 }} content="Slow to open, slow to leave">
          <Button variant="secondary" size="sm">
            800 / 400 ms
          </Button>
        </Tooltip>
      </div>
    </div>
  );
}

function ControlledCell() {
  const [open, setOpen] = useState(false);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>controlled</p>
      <div className={styles.row}>
        <Button variant="secondary" size="sm" onClick={() => setOpen((v) => !v)}>
          {open ? 'Hide' : 'Show'} tooltip
        </Button>
        <Tooltip open={open} onOpenChange={setOpen} content="Visibility is owned by the parent">
          <Button variant="secondary" size="sm">
            Anchored here
          </Button>
        </Tooltip>
      </div>
      <p className={styles.state}>open: {String(open)}</p>
    </div>
  );
}

function DisabledCell() {
  const [count, setCount] = useState(0);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>disabled tooltip, live trigger</p>
      <p className={styles.cellHint}>No tooltip appears; the button still clicks.</p>
      <Tooltip disabled content="You should never see this">
        <Button variant="secondary" size="sm" onClick={() => setCount((c) => c + 1)}>
          Clicked {count}×
        </Button>
      </Tooltip>
    </div>
  );
}

function IconButtonCell() {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>icon-only trigger</p>
      <p className={styles.cellHint}>Tab to the button — the tooltip opens on focus, not hover alone.</p>
      <div className={styles.row}>
        <Tooltip content="Print label">
          <button type="button" className={styles.iconButton} aria-label="Print label">
            <Printer size={16} strokeWidth={2} aria-hidden="true" />
          </button>
        </Tooltip>
        <Tooltip placement="bottom" size="sm" content="Prices resolve from the payer's price list — nobody types one.">
          <button type="button" className={styles.iconButton} aria-label="About pricing">
            <Info size={16} strokeWidth={2} aria-hidden="true" />
          </button>
        </Tooltip>
      </div>
    </div>
  );
}

function CollisionCell() {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>viewport collision → flip</p>
      <p className={styles.cellHint}>
        Both ask for a side with no room. The tooltip flips to the opposite side rather than render
        off-screen.
      </p>
      <div className={styles.spread}>
        <Tooltip placement="left" content="Asked for left; flips to right at the edge">
          <Button variant="secondary" size="sm">
            wants left
          </Button>
        </Tooltip>
        <Tooltip placement="right" content="Asked for right; flips to left at the edge">
          <Button variant="secondary" size="sm">
            wants right
          </Button>
        </Tooltip>
      </div>
    </div>
  );
}

function LongContentCell() {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>long content wraps</p>
      <Tooltip
        placement="right"
        content="Critical lab results release regardless of payment status — there is no path where an unpaid balance withholds a life-threatening value."
      >
        <Button variant="secondary" size="sm">
          Policy note
        </Button>
      </Tooltip>
    </div>
  );
}

export function TooltipStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Tooltip</h1>
      <p className={styles.subtitle}>
        Portalled role=&quot;tooltip&quot;, wired to the trigger with aria-describedby. Opens on hover
        and on keyboard focus; Escape and activating the trigger dismiss it; it is pointer-events:none
        so it never blocks the trigger. Flips at the viewport edge. Motion removed under
        prefers-reduced-motion. Tokens only.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Placements &times; sizes</h2>
        <p className={styles.sectionHint}>top · right · bottom · left, each at sm and md (default).</p>
        <div className={styles.grid}>
          {PLACEMENTS.map((placement) =>
            SIZES.map((size) => (
              <PlacementSizeCell key={`${placement}-${size}`} placement={placement} size={size} />
            )),
          )}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Behaviour</h2>
        <p className={styles.sectionHint}>Delay, controlled state, disabled, icon triggers, collision, wrapping.</p>
        <div className={styles.grid}>
          <DelayCell />
          <ControlledCell />
          <DisabledCell />
          <IconButtonCell />
          <CollisionCell />
          <LongContentCell />
        </div>
      </section>
    </div>
  );
}
