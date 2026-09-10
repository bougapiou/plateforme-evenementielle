const DATE_FMT = new Intl.DateTimeFormat('fr-FR', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});
const DATETIME_FMT = new Intl.DateTimeFormat('fr-FR', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});
const TIME_FMT = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' });

export function formatDate(iso?: string | null): string {
  return iso ? DATE_FMT.format(new Date(iso)) : '—';
}
export function formatDateTime(iso?: string | null): string {
  return iso ? DATETIME_FMT.format(new Date(iso)) : '—';
}
export function formatTime(iso?: string | null): string {
  return iso ? TIME_FMT.format(new Date(iso)) : '';
}
export function formatDateRange(start?: string | null, end?: string | null): string {
  if (!start) return '—';
  const s = new Date(start);
  const e = end ? new Date(end) : null;
  if (e && s.toDateString() === e.toDateString()) return formatDate(start);
  return `${formatDate(start)} → ${formatDate(end)}`;
}

const FCFA = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
export function formatFcfa(amount?: number | null): string {
  return amount == null ? '—' : `${FCFA.format(amount)} FCFA`;
}

/** Price for public display: hidden as "Gratuit" when the amount is zero or unset. */
export function priceLabel(amount?: number | null): string {
  return amount && amount > 0 ? formatFcfa(amount) : 'Gratuit';
}
