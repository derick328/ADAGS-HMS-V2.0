import { useEffect, useId, useRef, useState } from 'react';

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
}: ComboboxProps<T>) {
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState<T[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const listboxId = useId();
  const requestIdRef = useRef(0);

  useEffect(() => {
    if (query.trim().length < minChars) {
      setOptions([]);
      setIsOpen(false);
      return;
    }
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    const timer = setTimeout(() => {
      search(query.trim())
        .then((results) => {
          if (requestIdRef.current !== requestId) return; // a newer keystroke superseded this request
          setOptions(results);
          setIsOpen(true);
          setHighlightedIndex(0);
        })
        .finally(() => {
          if (requestIdRef.current === requestId) setIsLoading(false);
        });
    }, debounceMs);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, minChars, debounceMs]);

  function select(option: T) {
    onSelect(option);
    setQuery(clearOnSelect ? '' : String(getKey(option)));
    setOptions([]);
    setIsOpen(false);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
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
    } else if (event.key === 'Escape') {
      setIsOpen(false);
    }
  }

  return (
    <div style={{ position: 'relative' }}>
      <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginBottom: 4 }}>
        {label}
      </label>
      <input
        type="text"
        role="combobox"
        aria-expanded={isOpen}
        aria-controls={listboxId}
        aria-autocomplete="list"
        value={query}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(event) => setQuery(event.target.value)}
        onKeyDown={onKeyDown}
        onFocus={() => options.length > 0 && setIsOpen(true)}
        onBlur={() => setTimeout(() => setIsOpen(false), 150)}
        style={{
          width: '100%',
          padding: 'var(--space-2)',
          border: '1px solid var(--border-strong)',
          borderRadius: 'var(--radius)',
          font: 'inherit',
        }}
      />
      {isLoading && query.trim().length >= minChars && (
        <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginTop: 2 }}>Searching…</div>
      )}
      {isOpen && options.length > 0 && (
        <ul
          id={listboxId}
          role="listbox"
          style={{
            position: 'absolute',
            zIndex: 20,
            top: '100%',
            left: 0,
            right: 0,
            marginTop: 4,
            background: 'var(--bg-surface)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius)',
            maxHeight: 280,
            overflowY: 'auto',
            listStyle: 'none',
            padding: 4,
            boxShadow: '0 4px 12px rgba(0,0,0,0.12)',
          }}
        >
          {options.map((option, index) => (
            <li
              key={getKey(option)}
              role="option"
              aria-selected={index === highlightedIndex}
              onMouseDown={(event) => {
                event.preventDefault(); // keep focus so onBlur doesn't beat the click
                select(option);
              }}
              onMouseEnter={() => setHighlightedIndex(index)}
              style={{
                padding: '6px 8px',
                borderRadius: 'var(--radius-sm)',
                cursor: 'pointer',
                background: index === highlightedIndex ? 'var(--green-100)' : 'transparent',
              }}
            >
              {renderOption(option)}
            </li>
          ))}
        </ul>
      )}
      {isOpen && !isLoading && options.length === 0 && query.trim().length >= minChars && (
        <div
          style={{
            position: 'absolute',
            zIndex: 20,
            top: '100%',
            left: 0,
            right: 0,
            marginTop: 4,
            padding: '8px',
            background: 'var(--bg-surface)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius)',
            color: 'var(--text-muted)',
            fontSize: 'var(--text-sm)',
          }}
        >
          No matches.
        </div>
      )}
    </div>
  );
}
