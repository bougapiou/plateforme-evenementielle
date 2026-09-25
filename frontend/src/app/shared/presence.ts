import { FlowCounters } from '../features/checkin/checkin.service';

/** Where the numbers of a presence screen come from. */
export type PresenceSource = 'qr' | 'physique' | 'combine';

export const PRESENCE_SOURCES: { value: PresenceSource; label: string; description: string }[] = [
  { value: 'qr', label: 'Billets QR', description: 'Personnes contrôlées par scan de billet' },
  { value: 'physique', label: 'Capteurs', description: 'Passages comptés par les capteurs laser' },
  { value: 'combine', label: 'Combiné', description: 'Scans de billets + capteurs, additionnés' },
];

export interface Flow {
  entrees: number;
  sorties: number;
  presents: number;
  /** Only ticket scans can tell who came back: null when the source cannot know. */
  reentrees: number | null;
}

const num = (c: FlowCounters | undefined, key: string): number => c?.[key] ?? 0;

export function hasPhysicalCount(physique: FlowCounters | undefined): boolean {
  return num(physique, 'entrees') > 0 || num(physique, 'sorties') > 0;
}

/** The counters to show for a source; in "combine" mode both sources are added up. */
export function flowOf(source: PresenceSource, qr: FlowCounters | undefined, physique: FlowCounters | undefined): Flow {
  const q: Flow = {
    entrees: num(qr, 'entrees'), sorties: num(qr, 'sorties'),
    presents: num(qr, 'presents'), reentrees: num(qr, 'reentrees'),
  };
  const p: Flow = {
    entrees: num(physique, 'entrees'), sorties: num(physique, 'sorties'),
    presents: num(physique, 'presents'), reentrees: null,
  };
  if (source === 'qr') return q;
  if (source === 'physique') return p;
  return {
    entrees: q.entrees + p.entrees,
    sorties: q.sorties + p.sorties,
    presents: q.presents + p.presents,
    reentrees: q.reentrees,
  };
}

/** What each source contributes to a combined counter, e.g. "Billets 120 · Capteurs 80". */
export function contributions(
  key: 'entrees' | 'sorties' | 'presents',
  qr: FlowCounters | undefined,
  physique: FlowCounters | undefined,
): { qr: number; physique: number } {
  return { qr: num(qr, key), physique: num(physique, key) };
}

const STORAGE_KEY = 'pne.presence.source';

export function isPresenceSource(v: unknown): v is PresenceSource {
  return v === 'qr' || v === 'physique' || v === 'combine';
}

/** The source the person chose last time on this browser (a display left open keeps its setting). */
export function loadSource(): PresenceSource | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return isPresenceSource(v) ? v : null;
  } catch {
    return null;
  }
}

export function saveSource(source: PresenceSource): void {
  try {
    localStorage.setItem(STORAGE_KEY, source);
  } catch {
    /* private mode: the choice just isn't remembered */
  }
}
