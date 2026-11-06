import { Search, X } from 'lucide-react';
import type { InputHTMLAttributes } from 'react';
import './SearchField.css';

type SearchFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  onClear?: () => void;
};

export function SearchField({ value, onClear, className, ...props }: SearchFieldProps) {
  return <span className={`search-field ${className ?? ''}`}>
    <Search size={18} aria-hidden="true" className="search-field-icon" />
    <input {...props} type="search" value={value} className="clinical-control search-field-input" />
    {value && onClear && <button type="button" className="search-field-clear" onClick={onClear} aria-label="Clear search"><X size={16} aria-hidden="true" /></button>}
  </span>;
}
