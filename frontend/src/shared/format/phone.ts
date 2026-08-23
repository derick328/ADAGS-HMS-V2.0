// 05-receptionist.md §4.2: stored E.164, default country +255. Displayed
// local — "+255700000000" -> "0700000000".
export function formatPhone(value: string | undefined | null): string {
  if (!value) return '—';
  return value.startsWith('+255') ? '0' + value.slice(4) : value;
}
