import { Link } from 'react-router-dom';
import styles from './ComponentsIndexPage.module.css';

/**
 * Index of primitive component showcases. Add an entry here whenever a new
 * primitive gets a `*.stories.tsx` route under /dev/components.
 */
const PRIMITIVES = [
  { name: 'Button', path: '/dev/components/button', hint: 'Variants, sizes, icons, loading and disabled states' },
  { name: 'Input', path: '/dev/components/input', hint: 'Variants, sizes, icons, required, disabled and read-only states' },
  {
    name: 'Textarea',
    path: '/dev/components/textarea',
    hint: 'Variants, sizes, icons, character count, resize, disabled and read-only states',
  },
  { name: 'Select', path: '/dev/components/select', hint: 'Native select — variants, sizes, icon, required and disabled states' },
  {
    name: 'Checkbox',
    path: '/dev/components/checkbox',
    hint: 'Sizes, checked/indeterminate/error/disabled states, a live select-all demo',
  },
  { name: 'Radio', path: '/dev/components/radio', hint: 'Sizes, states, and a live controlled radio group' },
  { name: 'Badge', path: '/dev/components/badge', hint: 'Variants, sizes, icons, and the dot indicator' },
  {
    name: 'Card',
    path: '/dev/components/card',
    hint: 'Variants, padding sizes, composition slots, and a keyboard-operable interactive card',
  },
  {
    name: 'Skeleton',
    path: '/dev/components/skeleton',
    hint: 'Text, circular and rectangular variants, sizes, width fractions, multi-line text, and the aria-hidden / role=status behaviour',
  },
  {
    name: 'EmptyState',
    path: '/dev/components/emptystate',
    hint: 'default / compact / full variants, icon or illustration, primary and secondary actions, heading structure, and narrow-screen wrapping',
  },
  {
    name: 'Toast',
    path: '/dev/components/toast',
    hint: 'success / info / warning / danger variants, sizes, entering / visible / exiting states, auto-dismiss with pause-on-hover, and a live demo',
  },
  {
    name: 'Modal',
    path: '/dev/components/modal',
    hint: 'sm / md / lg / xl sizes, title / description / children / footer / leadingIcon slots, portal render, focus trap and restore, scroll lock, accessible backdrop, Escape and configurable close, nested dialogs',
  },
  {
    name: 'Tabs',
    path: '/dev/components/tabs',
    hint: 'default / enclosed / underline variants, sm / md / lg sizes, icons, disabled tabs, controlled and uncontrolled state, automatic / manual activation, full WAI-ARIA tablist / tab / tabpanel with arrow, Home and End keys',
  },
  {
    name: 'Table',
    path: '/dev/components/table',
    hint: 'default / bordered / striped variants, sm / md / lg sizes, native table semantics, sortable headers with aria-sort (no comparator inside), row hover / selected / interactive states, caption, tfoot, empty state, labelled focusable overflow scroll region',
  },
  {
    name: 'Tooltip',
    path: '/dev/components/tooltip',
    hint: 'top / right / bottom / left placements with viewport-collision flip, sm / md sizes, opens on hover and focus, configurable delay, controlled and uncontrolled, disabled, Escape to dismiss, pointer-events:none, portalled role=tooltip wired via aria-describedby',
  },
];

export function ComponentsIndexPage() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Component gallery</h1>
      <p className={styles.subtitle}>Primitives from src/shared/components, for visual review during development.</p>
      <nav className={styles.list} aria-label="Primitives">
        {PRIMITIVES.map((primitive) => (
          <Link key={primitive.path} to={primitive.path} className={styles.item}>
            {primitive.name}
            <span className={styles.itemHint}>{primitive.hint}</span>
          </Link>
        ))}
      </nav>
    </div>
  );
}
