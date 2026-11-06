import { Search, X } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import './Combobox.css';

/**
 * 07-doctor.md §3: "One component, four configurations. Build it once."
 * Free-text-*feeling* input, catalogue-backed storage — the user types, the
 * system stores an identifier. No free-text-only path.
 *
 * Mechanics per spec: minimum 2 characters, 200ms debounce, keyboard
 * navigable. Used for diagnosis (ICD-10), lab test, drug, and procedure
 * pickers — each caller supplies its own `search` function and rendering,
 * this component owns only the debounce/keyboard/selection mechanics.
 */
export interface ComboboxProps<T> {
  label: string;
  placeholder?: string;
  minChars?: number;
  debounceMs?: number;
  search: (query: string) => Promise<T[]>;
  getKey: (option: T) => string;
  renderOption: (option: T) => React.ReactNode;
  onSelect: (option: T) => void;
  disabled?: boolean;
  /** Cleared query text shown after a selection, e.g. the picked drug's name — the input resets to empty by default. */
  clearOnSelect?: boolean;
  /**
   * Marks a row as visible-but-unselectable — a controlled-drug row, an
   * un-classified importer placeholder, etc. Clicking or Enter'ing a
   * disabled row is a full no-op (no `onSelect`, dropdown stays open,
   * query text is kept) so the user sees why they can't pick it instead
   * of getting a silent close.
   */
  isOptionDisabled?: (option: T) => boolean;
}

export function Combobox<T>({
  label,
  placeholder,
  minChars = 2,
  debounceMs = 200,
  search,
  getKey,
  renderOption,
  onSelect,
  disabled,
  clearOnSelect = true,
  isOptionDisabled,
}: ComboboxProps<T>) {
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState<T[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const [error, setError] = useState(false);
  const [openAbove, setOpenAbove] = useState(false);
  const listboxId = useId();
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const requestIdRef = useRef(0);

  useEffect(() => {
    if (query.trim().length < minChars) {
      requestIdRef.current += 1;
      setOptions([]);
      setIsOpen(false);
      setIsLoading(false);
      return;
    }
    const requestId = ++requestIdRef.current;
    setError(false);
    setIsLoading(true);
    const timer = setTimeout(() => {
      search(query.trim())
        .then((results) => {
          if (requestIdRef.current !== requestId) return; // a newer keystroke superseded this request
          setOptions(results);
          setIsOpen(true);
          setHighlightedIndex(0);
        })
        .catch(() => {
          if (requestIdRef.current !== requestId) return;
          setOptions([]);
          setError(true);
          setIsOpen(true);
        })
        .finally(() => {
          if (requestIdRef.current === requestId) setIsLoading(false);
        });
    }, debounceMs);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, minChars, debounceMs]);

  useEffect(() => {
    if (!isOpen || !inputRef.current) return;
    const bounds = inputRef.current.getBoundingClientRect();
    setOpenAbove(window.innerHeight - bounds.bottom < 300 && bounds.top > 300);
  }, [isOpen]);

  function select(option: T) {
    // Disabled rows (controlled-drug, un-classified importer placeholders)
    // must not collapse the dropdown or clear the query — otherwise the
    // click looks like a silent failure and the user has no idea why the
    // item they picked never became a pending line.
    if (isOptionDisabled?.(option)) return;
    onSelect(option);
    setQuery(clearOnSelect ? '' : String(getKey(option)));
    setOptions([]);
    setIsOpen(false);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape' && isOpen) { setIsOpen(false); return; }
    if (!isOpen || options.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setHighlightedIndex((index) => Math.min(index + 1, options.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlightedIndex((index) => Math.max(index - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      select(options[highlightedIndex]);
    }
  }

  return (
    <div className="clinical-combobox">
      <label htmlFor={inputId} className="clinical-combobox-label">
        {label}
      </label>
      <div className="clinical-combobox-control">
      <Search size={17} aria-hidden="true" className="clinical-combobox-icon" />
      <input
        ref={inputRef}
        id={inputId}
        className="clinical-control clinical-combobox-input"
        type="text"
        role="combobox"
        aria-expanded={isOpen}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-activedescendant={isOpen && options.length ? `${listboxId}-option-${highlightedIndex}` : undefined}
        value={query}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(event) => setQuery(event.target.value)}
        onKeyDown={onKeyDown}
        onFocus={() => options.length > 0 && setIsOpen(true)}
        onBlur={() => setTimeout(() => setIsOpen(false), 150)}
      />
      {query && <button type="button" className="clinical-combobox-clear" aria-label={`Clear ${label}`} onMouseDown={(event) => event.preventDefault()} onClick={() => { setQuery(''); setOptions([]); setIsOpen(false); inputRef.current?.focus(); }}><X size={16} aria-hidden="true" /></button>}
      </div>
      {isLoading && query.trim().length >= minChars && (
        <div className="clinical-combobox-message" role="status">Searching…</div>
      )}
      {isOpen && options.length > 0 && (
        <ul
          id={listboxId}
          role="listbox"
          className={`clinical-combobox-list ${openAbove ? 'clinical-combobox-list-above' : ''}`}
        >
          {options.map((option, index) => {
            const optDisabled = isOptionDisabled?.(option) ?? false;
            return (
              <li
                id={`${listboxId}-option-${index}`}
                key={getKey(option)}
                role="option"
                aria-selected={index === highlightedIndex}
                aria-disabled={optDisabled || undefined}
                onMouseDown={(event) => {
                  event.preventDefault(); // keep focus so onBlur doesn't beat the click
                  select(option);
                }}
                onMouseEnter={() => setHighlightedIndex(index)}
                title={optDisabled ? 'Not selectable — see reason' : undefined}
                className="clinical-combobox-option"
              >
                {renderOption(option)}
              </li>
            );
          })}
        </ul>
      )}
      {isOpen && !isLoading && options.length === 0 && query.trim().length >= minChars && (
        <div className={`clinical-combobox-empty ${openAbove ? 'clinical-combobox-list-above' : ''}`} role="status">
          {error ? 'Search failed. Try again.' : 'No matches.'}
        </div>
      )}
    </div>
  );
}
