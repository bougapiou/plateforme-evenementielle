import { Activity } from '../features/events/event.models';

/**
 * Personal agenda (« Mon programme », PART-02): the sessions a visitor marked,
 * kept in this browser, exportable as an iCalendar (.ics) file or added one by
 * one to Google Agenda.
 */
const KEY = (eventId: string) => `pne.agenda.${eventId}`;

export function loadAgenda(eventId: string): string[] {
  try {
    return JSON.parse(localStorage.getItem(KEY(eventId)) ?? '[]') as string[];
  } catch {
    return [];
  }
}

export function saveAgenda(eventId: string, ids: string[]): void {
  localStorage.setItem(KEY(eventId), JSON.stringify(ids));
}

const icsDate = (iso: string) => new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');

/** RFC 5545 text escaping. */
const esc = (s: string) => s.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');

/** Folds lines longer than 75 octets (RFC 5545 §3.1). */
function fold(line: string): string {
  const out: string[] = [];
  let rest = line;
  while (rest.length > 74) {
    out.push(rest.slice(0, 74));
    rest = ' ' + rest.slice(74);
  }
  out.push(rest);
  return out.join('\r\n');
}

/** Builds an .ics calendar with the given sessions (time and room included). */
export function buildIcs(eventNom: string, eventLieu: string | undefined, sessions: Activity[]): string {
  const stamp = icsDate(new Date().toISOString());
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//PNE Burkina Faso//Mon programme//FR',
    'CALSCALE:GREGORIAN', 'METHOD:PUBLISH', `X-WR-CALNAME:${esc(eventNom)}`];
  for (const a of sessions) {
    const end = a.dateFin ?? new Date(new Date(a.dateDebut).getTime() + 3600_000).toISOString();
    const where = [a.salle || a.lieu, eventLieu].filter(Boolean).join(', ');
    const desc = [a.intervenant && `Intervenant : ${a.intervenant}`, a.moderateur && `Modérateur : ${a.moderateur}`,
      a.description].filter(Boolean).join('\n');
    lines.push('BEGIN:VEVENT', `UID:${a.id}@pne.gov.bf`, `DTSTAMP:${stamp}`, `DTSTART:${icsDate(a.dateDebut)}`,
      `DTEND:${icsDate(end)}`, `SUMMARY:${esc(a.titre)}`);
    if (where) lines.push(`LOCATION:${esc(where)}`);
    if (desc) lines.push(`DESCRIPTION:${esc(desc)}`);
    lines.push('BEGIN:VALARM', 'TRIGGER:-PT30M', 'ACTION:DISPLAY', `DESCRIPTION:${esc(a.titre)}`, 'END:VALARM',
      'END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  return lines.map(fold).join('\r\n') + '\r\n';
}

export function downloadIcs(filename: string, ics: string): void {
  const url = URL.createObjectURL(new Blob([ics], { type: 'text/calendar;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

/** « Ajouter à Google Agenda » link for one session. */
export function googleCalendarUrl(a: Activity, eventLieu?: string): string {
  const end = a.dateFin ?? new Date(new Date(a.dateDebut).getTime() + 3600_000).toISOString();
  const p = new URLSearchParams({
    action: 'TEMPLATE',
    text: a.titre,
    dates: `${icsDate(a.dateDebut)}/${icsDate(end)}`,
    location: [a.salle || a.lieu, eventLieu].filter(Boolean).join(', '),
    details: a.description ?? '',
  });
  return `https://calendar.google.com/calendar/render?${p.toString()}`;
}
