import { Check, ChevronDown } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import './DropdownSelect.css';

export type DropdownOption = { value: string; label: string; disabled?: boolean };

export function DropdownSelect({ label, value, options, onChange, placeholder, disabled, name, id }: {
  label: string;
  value: string;
  options: DropdownOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  name?: string;
  id?: string;
}) {
  const generatedId = useId();
  const controlId = id ?? generatedId;
  const listId = `${controlId}-options`;
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [above, setAbove] = useState(false);
  const selected = options.find((option) => option.value === value);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  function show() {
    if (disabled) return;
    const bounds = triggerRef.current?.getBoundingClientRect();
    setAbove(Boolean(bounds && window.innerHeight - bounds.bottom < 270 && bounds.top > 270));
    setActive(Math.max(0, options.findIndex((option) => option.value === value)));
    setOpen(true);
  }

  function choose(index: number) {
    const option = options[index];
    if (!option || option.disabled) return;
    onChange(option.value);
    setOpen(false);
    triggerRef.current?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'Escape' && open) { event.preventDefault(); setOpen(false); return; }
    if (event.key === 'Tab') { setOpen(false); return; }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) { show(); return; }
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      let next = active;
      for (let count = 0; count < options.length; count++) {
        next = (next + direction + options.length) % options.length;
        if (!options[next].disabled) break;
      }
      setActive(next);
    } else if (event.key === 'Home' && open) { event.preventDefault(); setActive(0); }
    else if (event.key === 'End' && open) { event.preventDefault(); setActive(options.length - 1); }
    else if ((event.key === 'Enter' || event.key === ' ') && open) { event.preventDefault(); choose(active); }
    else if ((event.key === 'Enter' || event.key === ' ') && !open) { event.preventDefault(); show(); }
  }

  return <div className="dropdown-select" ref={rootRef}>
    <label id={`${controlId}-label`} className="dropdown-select-label">{label}</label>
    {name && <input type="hidden" name={name} value={value} />}
    <button ref={triggerRef} id={controlId} type="button" role="combobox" aria-haspopup="listbox" aria-expanded={open} aria-controls={listId} aria-labelledby={`${controlId}-label ${controlId}`} aria-activedescendant={open && options.length ? `${listId}-${active}` : undefined} disabled={disabled} className={`dropdown-select-trigger ${!selected ? 'dropdown-select-placeholder' : ''}`} onClick={() => open ? setOpen(false) : show()} onKeyDown={handleKeyDown}>
      <span>{selected?.label ?? placeholder ?? 'Select an option'}</span><ChevronDown size={18} aria-hidden="true" />
    </button>
    {open && <div id={listId} role="listbox" aria-labelledby={`${controlId}-label`} className={`dropdown-select-list ${above ? 'dropdown-select-above' : ''}`}>
      {options.map((option, index) => <div key={`${option.value}-${index}`} id={`${listId}-${index}`} role="option" aria-selected={option.value === value} aria-disabled={option.disabled || undefined} data-active={index === active || undefined} className="dropdown-select-option" onMouseEnter={() => setActive(index)} onMouseDown={(event) => event.preventDefault()} onClick={() => choose(index)}><span>{option.label}</span>{option.value === value && <Check size={16} aria-hidden="true" />}</div>)}
    </div>}
  </div>;
}
