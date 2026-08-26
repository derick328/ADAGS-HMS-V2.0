import type { LucideIcon } from 'lucide-react';
import { useCallback, useId, useRef, useState } from 'react';
import type { KeyboardEvent, ReactNode } from 'react';
import styles from './Tabs.module.css';

/**
 * ADAGS tabs primitive. Tokens only — see STYLE_GUIDE.md §2, §3, §6, §8.
 *
 * Data-driven so the WAI-ARIA wiring (tablist / tab / tabpanel ids,
 * `aria-controls` / `aria-labelledby`, roving `tabIndex`, arrow-key
 * navigation) lives in one place and can't be assembled wrong by a caller.
 * Panels stay mounted and `hidden` by default so form state and scroll
 * position survive a tab switch; pass `unmountInactivePanels` to opt out.
 *
 * Keyboard: ArrowLeft / ArrowRight wrap and skip disabled tabs, Home / End
 * jump to the first / last enabled tab. With `activation="automatic"`
 * (default) selection follows arrow focus; with `"manual"` the arrows only
 * move focus and Enter / Space / click selects. Each panel is `tabIndex=0`
 * so Tab reaches its content after the active tab.
 *
 * One of `aria-label` / `aria-labelledby` is required by the type.
 *
 * @example Uncontrolled, three text tabs
 * ```tsx
 * <Tabs
 *   aria-label="Encounter sections"
 *   items={[
 *     { id: 'summary', label: 'Summary', content: <EncounterSummary /> },
 *     { id: 'orders', label: 'Orders', content: <OrderList /> },
 *     { id: 'notes', label: 'Notes', content: <ClinicalNotes /> },
 *   ]}
 * />
 * ```
 *
 * @example Controlled, with icons and a disabled tab
 * ```tsx
 * const [tab, setTab] = useState('vitals');
 * <Tabs
 *   aria-label="Patient record"
 *   variant="underline"
 *   value={tab}
 *   onChange={setTab}
 *   items={[
 *     { id: 'vitals', label: 'Vitals', icon: Activity, content: <Vitals /> },
 *     { id: 'labs', label: 'Labs', icon: FlaskConical, content: <Labs /> },
 *     { id: 'billing', label: 'Billing', icon: CreditCard, disabled: true, content: <Billing /> },
 *   ]}
 * />
 * ```
 *
 * @example Enclosed, large, manual activation
 * ```tsx
 * <Tabs
 *   aria-labelledby="report-heading"
 *   variant="enclosed"
 *   size="lg"
 *   activation="manual"
 *   defaultValue="revenue"
 *   items={reportTabs}
 * />
 * ```
 */

export type TabsVariant = 'default' | 'enclosed' | 'underline';
export type TabsSize = 'sm' | 'md' | 'lg';
export type TabsActivation = 'automatic' | 'manual';

export interface TabItem {
  /** Unique within this Tabs instance. Used as the value and to build the ARIA ids. */
  id: string;
  label: ReactNode;
  /** Leading icon, rendered at the size-appropriate px and `aria-hidden`. */
  icon?: LucideIcon;
  disabled?: boolean;
  /** The tabpanel body for this tab. */
  content: ReactNode;
}

interface TabsBaseProps {
  items: TabItem[];
  /** Controlled active tab id. Provide together with `onChange`. */
  value?: string;
  /** Uncontrolled initial active tab id. @default the first non-disabled item */
  defaultValue?: string;
  onChange?: (id: string) => void;
  /** @default 'default' */
  variant?: TabsVariant;
  /** @default 'md' */
  size?: TabsSize;
  /** How arrow keys behave. @default 'automatic' (selection follows focus) */
  activation?: TabsActivation;
  /** Remove inactive panels from the DOM instead of hiding them with `hidden`. @default false */
  unmountInactivePanels?: boolean;
  /** Prefix for the generated tab / panel ids. @default useId() */
  idPrefix?: string;
  className?: string;
}

// A tablist must have an accessible name — require exactly one source of it.
type TabsAriaProps =
  | { 'aria-label': string; 'aria-labelledby'?: never }
  | { 'aria-labelledby': string; 'aria-label'?: never };

export type TabsProps = TabsBaseProps & TabsAriaProps;

const ICON_SIZE: Record<TabsSize, number> = { sm: 16, md: 16, lg: 20 };
const NAV_KEYS = ['ArrowLeft', 'ArrowRight', 'Home', 'End'];

export function Tabs({
  items,
  value,
  defaultValue,
  onChange,
  variant = 'default',
  size = 'md',
  activation = 'automatic',
  unmountInactivePanels = false,
  idPrefix,
  className,
  ...aria
}: TabsProps) {
  const generatedPrefix = useId();
  const prefix = idPrefix ?? generatedPrefix;

  const enabledIds = items.filter((item) => !item.disabled).map((item) => item.id);
  const firstEnabledId = enabledIds[0];

  const isControlled = value !== undefined;
  const [internalId, setInternalId] = useState<string | undefined>(() =>
    defaultValue !== undefined && enabledIds.includes(defaultValue) ? defaultValue : firstEnabledId,
  );
  const activeId = isControlled ? value : internalId;

  const tabRefs = useRef(new Map<string, HTMLButtonElement>());

  const select = useCallback(
    (id: string) => {
      if (!isControlled) setInternalId(id);
      if (id !== activeId) onChange?.(id);
    },
    [isControlled, activeId, onChange],
  );

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (!NAV_KEYS.includes(event.key) || enabledIds.length === 0) return;
    event.preventDefault();

    const focusedId = event.currentTarget.dataset.tabId ?? '';
    const pos = enabledIds.indexOf(focusedId);
    let nextPos: number;
    switch (event.key) {
      case 'Home':
        nextPos = 0;
        break;
      case 'End':
        nextPos = enabledIds.length - 1;
        break;
      case 'ArrowLeft':
        nextPos = (pos <= 0 ? enabledIds.length : pos) - 1;
        break;
      default: // ArrowRight
        nextPos = (pos + 1) % enabledIds.length;
    }

    const nextId = enabledIds[nextPos];
    tabRefs.current.get(nextId)?.focus();
    if (activation === 'automatic') select(nextId);
  };

  const rootClasses = [styles.root, styles[variant], styles[size], className].filter(Boolean).join(' ');

  return (
    <div className={rootClasses}>
      <div
        role="tablist"
        aria-orientation="horizontal"
        aria-label={aria['aria-label']}
        aria-labelledby={aria['aria-labelledby']}
        className={styles.tablist}
      >
        {items.map((item) => {
          const selected = item.id === activeId;
          const tabbable = selected || (activeId === undefined && item.id === firstEnabledId);
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              type="button"
              role="tab"
              id={`${prefix}-tab-${item.id}`}
              data-tab-id={item.id}
              aria-selected={selected}
              aria-controls={`${prefix}-panel-${item.id}`}
              tabIndex={tabbable ? 0 : -1}
              disabled={item.disabled}
              ref={(el) => {
                if (el) tabRefs.current.set(item.id, el);
                else tabRefs.current.delete(item.id);
              }}
              className={styles.tab}
              onClick={() => select(item.id)}
              onKeyDown={handleKeyDown}
            >
              {Icon && (
                <Icon size={ICON_SIZE[size]} strokeWidth={2} aria-hidden="true" className={styles.icon} />
              )}
              <span className={styles.label}>{item.label}</span>
            </button>
          );
        })}
      </div>

      {items.map((item) => {
        const selected = item.id === activeId;
        if (unmountInactivePanels && !selected) return null;
        return (
          <div
            key={item.id}
            role="tabpanel"
            id={`${prefix}-panel-${item.id}`}
            aria-labelledby={`${prefix}-tab-${item.id}`}
            tabIndex={0}
            hidden={!selected}
            className={styles.panel}
          >
            {item.content}
          </div>
        );
      })}
    </div>
  );
}
