import { AlertTriangle, Trash2, UserPlus } from 'lucide-react';
import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../Button';
import { Modal } from './Modal';
import type { ModalSize } from './Modal';
import styles from './Modal.stories.module.css';

/**
 * Plain-React showcase for the Modal primitive — not Storybook, just a route
 * that opens every configuration so a change can be reviewed visually.
 * Mounted at /dev/components/modal.
 *
 * Because a modal is an overlay, each "variant" is a trigger button that opens
 * that configuration. The long filler column below the grid exists so you can
 * confirm the page scroll lock (and its scrollbar-width compensation) while a
 * modal is open.
 */

const SIZES: ModalSize[] = ['sm', 'md', 'lg', 'xl'];

function SizeCell({ size }: { size: ModalSize }) {
  const [open, setOpen] = useState(false);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>size: {size}</p>
      <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
        Open {size}
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        size={size}
        title={`A ${size} modal`}
        description="The panel grows to its size cap, then the body scrolls inside it."
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button onClick={() => setOpen(false)}>Done</Button>
          </>
        }
      >
        <p className={styles.paragraph}>
          Sentence-case copy, neutral text tokens, hairline dividers above and below this body.
        </p>
        <p className={styles.paragraph}>
          Tab and Shift+Tab cycle between the two footer buttons and the close control without
          leaving the dialog. Escape closes it; so does a click on the dimmed backdrop.
        </p>
      </Modal>
    </div>
  );
}

function ConfirmationCell() {
  const [open, setOpen] = useState(false);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>confirmation + leadingIcon</p>
      <Button variant="danger" size="sm" onClick={() => setOpen(true)}>
        Void invoice
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        size="sm"
        leadingIcon={AlertTriangle}
        title="Void this invoice?"
        description="The invoice stays in the sequence with a voided status. This cannot be undone."
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button variant="danger" leadingIcon={Trash2} onClick={() => setOpen(false)}>
              Void invoice
            </Button>
          </>
        }
      />
    </div>
  );
}

function FormCell() {
  const [open, setOpen] = useState(false);
  const firstFieldRef = useRef<HTMLInputElement>(null);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>form body + initialFocusRef</p>
      <Button variant="secondary" size="sm" leadingIcon={UserPlus} onClick={() => setOpen(true)}>
        Add next of kin
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        size="lg"
        title="Add next of kin"
        description="Focus lands on the first field when the dialog opens."
        initialFocusRef={firstFieldRef}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="nok-form">
              Save
            </Button>
          </>
        }
      >
        <form
          id="nok-form"
          onSubmit={(event) => {
            event.preventDefault();
            setOpen(false);
          }}
        >
          <div className={styles.formRow}>
            <label className={styles.formLabel} htmlFor="nok-name">
              Full name
            </label>
            <input ref={firstFieldRef} id="nok-name" className={styles.formInput} type="text" />
          </div>
          <div className={styles.formRow}>
            <label className={styles.formLabel} htmlFor="nok-phone">
              Phone number
            </label>
            <input id="nok-phone" className={styles.formInput} type="tel" />
          </div>
        </form>
      </Modal>
    </div>
  );
}

function ScrollingBodyCell() {
  const [open, setOpen] = useState(false);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>long body scrolls inside the panel</p>
      <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
        Open long content
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Terms of the payment bypass"
        description="Header and footer stay pinned; only this middle section scrolls."
        footer={<Button onClick={() => setOpen(false)}>Acknowledge</Button>}
      >
        {Array.from({ length: 20 }, (_, i) => (
          <p key={i} className={styles.paragraph}>
            Paragraph {i + 1}. The billing gate is evaluated live at the point of consumption, per
            invoice line, and never blocks discharge. An active payment bypass satisfies the gate for
            the whole encounter.
          </p>
        ))}
      </Modal>
    </div>
  );
}

function NoHeaderCell() {
  const [open, setOpen] = useState(false);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>no title / no header</p>
      <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
        Open bare modal
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        size="sm"
        closeLabel="Close message"
        footer={<Button onClick={() => setOpen(false)}>Got it</Button>}
      >
        <p className={styles.paragraph}>
          With no title the dialog falls back to an <code>aria-label</code> from{' '}
          <code>closeLabel</code>, and the body carries the top padding itself.
        </p>
      </Modal>
    </div>
  );
}

function NonDismissibleCell() {
  const [open, setOpen] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>close routes disabled → forced close button</p>
      <p className={styles.cellHint}>
        {acknowledged ? 'Acknowledged.' : 'Backdrop + Escape do nothing; a dev warning fires.'}
      </p>
      <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
        Open must-act modal
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        size="sm"
        leadingIcon={AlertTriangle}
        title="Allergy on file"
        description="This patient has a recorded allergy. Confirm you have read it before prescribing."
        showClose={false}
        closeOnBackdropClick={false}
        closeOnEscape={false}
        footer={
          <Button
            onClick={() => {
              setAcknowledged(true);
              setOpen(false);
            }}
          >
            I have read the allergy record
          </Button>
        }
      />
    </div>
  );
}

function NestedCell() {
  const [outer, setOuter] = useState(false);
  const [inner, setInner] = useState(false);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>nested — one shared scroll lock</p>
      <Button variant="secondary" size="sm" onClick={() => setOuter(true)}>
        Open outer
      </Button>
      <Modal
        open={outer}
        onClose={() => setOuter(false)}
        title="Outer dialog"
        description="Opening the inner dialog stacks a second modal on top."
        footer={
          <>
            <Button variant="secondary" onClick={() => setOuter(false)}>
              Close
            </Button>
            <Button onClick={() => setInner(true)}>Open inner</Button>
          </>
        }
      >
        <p className={styles.paragraph}>
          The page scroll lock is reference-counted, so it stays locked until both dialogs close and
          then restores exactly once.
        </p>
        <Modal
          open={inner}
          onClose={() => setInner(false)}
          size="sm"
          title="Inner dialog"
          footer={<Button onClick={() => setInner(false)}>Back</Button>}
        >
          <p className={styles.paragraph}>Escape closes this one first, not the outer.</p>
        </Modal>
      </Modal>
    </div>
  );
}

export function ModalStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Modal</h1>
      <p className={styles.subtitle}>
        Overlay dialog rendered through a portal. Focus trap, focus restore, scroll lock, accessible
        backdrop, Escape to close, background made inert. role=&quot;dialog&quot; with
        aria-modal=&quot;true&quot;; aria-labelledby / aria-describedby wired when the content exists.
        Motion removed under prefers-reduced-motion.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Sizes</h2>
        <p className={styles.sectionHint}>sm / md (default) / lg / xl — max-width plus panel padding.</p>
        <div className={styles.grid}>
          {SIZES.map((size) => (
            <SizeCell key={size} size={size} />
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Slots &amp; behaviour</h2>
        <p className={styles.sectionHint}>
          title · description · children · footer · leadingIcon · close action · configurable close.
        </p>
        <div className={styles.grid}>
          <ConfirmationCell />
          <FormCell />
          <ScrollingBodyCell />
          <NoHeaderCell />
          <NonDismissibleCell />
          <NestedCell />
        </div>
      </section>

      <div className={styles.filler}>
        <p className={styles.paragraph}>
          Filler content. Scroll the page, then open any modal above: the page must not shift
          sideways (scrollbar-width compensation) and must not scroll behind the backdrop. When every
          modal closes, the scroll position and the scrollbar return unchanged.
        </p>
        {Array.from({ length: 40 }, (_, i) => (
          <p key={i} className={styles.paragraph}>
            Row {i + 1} — background content that should be inert and unscrollable while a modal is
            open.
          </p>
        ))}
      </div>
    </div>
  );
}
