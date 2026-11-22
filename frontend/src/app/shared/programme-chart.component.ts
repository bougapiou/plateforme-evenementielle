import { Component, TemplateRef, computed, input, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ACTIVITY_FORMAT_LABELS,
  ACTIVITY_TYPE_LABELS,
  Activity,
  ActivityType,
} from '../features/events/event.models';
import { formatTime } from './format';

interface ProgrammeDay {
  key: string;
  label: string;
  /** Sessions held within this day. */
  items: Activity[];
  /** Sessions spanning several days that are running on this day ("En continu"). */
  continu: Activity[];
}

/** Accent colour of each activity type. */
const TYPE_COLOR: Record<ActivityType, string> = {
  CEREMONIE: '#b45309',
  KEYNOTE: '#a16207',
  CONFERENCE: '#1d4ed8',
  PANEL: '#7c3aed',
  TABLE_RONDE: '#9333ea',
  FORUM: '#6d28d9',
  ATELIER: '#0f766e',
  FORMATION: '#0369a1',
  DEMONSTRATION: '#0e7490',
  EXPOSITION: '#15803d',
  EMISSION_MEDIA: '#db2777',
  NETWORKING: '#c2410c',
  SPECTACLE: '#be123c',
  GALA: '#9f1239',
  PAUSE: '#64748b',
  AUTRE: '#475569',
};

const DAY_FMT = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
const SHORT_FMT = new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' });

const dayKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());

/** Lower-cased, accent-free text for forgiving keyword search. */
const fold = (s: string | undefined | null) =>
  (s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/**
 * Event programme rendered as a tree (arborescence): the event at the root,
 * one collapsible branch per day, the day's activities as leaves in
 * chronological order. A session spanning several days (an exhibition, a
 * fair…) is shown on every day it covers, in an « En continu » block.
 * Optional filter bar (day, room, type, theme, keyword / speaker).
 * Per-activity actions (participation, ticket…) are projected through the
 * optional `actions` template (`$implicit` = activity).
 */
@Component({
  selector: 'app-programme-chart',
  standalone: true,
  imports: [NgTemplateOutlet, FormsModule],
  template: `
    @if (filters()) {
      <div class="filters">
        <input class="form-input" type="search" placeholder="Rechercher (titre, intervenant…)"
               [ngModel]="q()" (ngModelChange)="q.set($event)" aria-label="Rechercher dans le programme" />
        @if (allDays().length > 1) {
          <select class="form-input" [ngModel]="fDay()" (ngModelChange)="fDay.set($event)" aria-label="Jour">
            <option value="">Tous les jours</option>
            @for (d of allDays(); track d.key) { <option [value]="d.key">{{ d.label }}</option> }
          </select>
        }
        @if (rooms().length > 1) {
          <select class="form-input" [ngModel]="fRoom()" (ngModelChange)="fRoom.set($event)" aria-label="Salle">
            <option value="">Toutes les salles</option>
            @for (r of rooms(); track r) { <option [value]="r">{{ r }}</option> }
          </select>
        }
        @if (types().length > 1) {
          <select class="form-input" [ngModel]="fType()" (ngModelChange)="fType.set($event)" aria-label="Type">
            <option value="">Tous les types</option>
            @for (t of types(); track t) { <option [value]="t">{{ typeLabel(t) }}</option> }
          </select>
        }
        @if (themes().length) {
          <select class="form-input" [ngModel]="fTheme()" (ngModelChange)="fTheme.set($event)" aria-label="Thème">
            <option value="">Tous les thèmes</option>
            @for (t of themes(); track t) { <option [value]="t">{{ t }}</option> }
          </select>
        }
        @if (filtering()) {
          <button type="button" class="btn-ghost text-brand-700" (click)="reset()">Effacer</button>
        }
      </div>
    }

    <div class="root">
      <span class="root-icon" aria-hidden="true">◆</span>
      <div>
        <p class="root-title">{{ title() }}</p>
        @if (subtitle()) { <p class="root-sub">{{ subtitle() }}</p> }
      </div>
    </div>

    @if (!days().length) {
      <p class="mt-4 text-sm text-slate-400">Aucune activité ne correspond à ces critères.</p>
    }

    <ul class="tree">
      @for (d of days(); track d.key) {
        <li>
          <button type="button" class="day" (click)="toggle(d.key)"
                  [attr.aria-expanded]="!collapsed().has(d.key)">
            <span class="chev" [class.closed]="collapsed().has(d.key)" aria-hidden="true">▾</span>
            @if (allDays().length > 1) { <span class="day-num">Jour {{ dayNumber(d.key) }}</span> }
            <span class="day-label">{{ d.label }}</span>
            <span class="day-count">{{ d.items.length + d.continu.length }} activité{{ d.items.length + d.continu.length > 1 ? 's' : '' }}</span>
          </button>

          @if (!collapsed().has(d.key)) {
            <ul class="tree">
              @if (d.continu.length) {
                <li class="continu">
                  <p class="continu-title">En continu</p>
                  @for (a of d.continu; track a.id) {
                    <ng-container *ngTemplateOutlet="act; context: { $implicit: a, multi: true }" />
                  }
                </li>
              }
              @for (a of d.items; track a.id) {
                <li [style.--accent]="color(a)">
                  <ng-container *ngTemplateOutlet="act; context: { $implicit: a, multi: false }" />
                </li>
              }
            </ul>
          }
        </li>
      }
    </ul>

    <ng-template #act let-a let-multi="multi">
      <div class="act" [style.--accent]="color(a)">
        <div class="act-head">
          <span class="dot" aria-hidden="true"></span>
          <span class="act-time">{{ multi ? span(a) : slot(a) }}</span>
          <span class="act-title">{{ a.titre }}</span>
          <span class="act-type">{{ typeLabel(a.typeActivite) }}</span>
          @if (a.theme) { <span class="badge bg-sky-50 text-sky-800">{{ a.theme }}</span> }
          @if (a.format && a.format !== 'SUR_PLACE') {
            <span class="badge bg-fuchsia-100 text-fuchsia-800">{{ formatLabel(a.format) }}</span>
          }
          @if (a.acces === 'GRATUIT') {
            <span class="badge bg-green-100 text-green-800">Gratuit</span>
          } @else if (a.acces === 'PAYANT') {
            <span class="badge bg-amber-100 text-amber-800">Payant</span>
          }
        </div>
        <div class="act-body">
          @if (a.imageUrl) { <img [src]="a.imageUrl" [alt]="'Visuel : ' + a.titre" class="act-img" /> }
          <div class="min-w-0 flex-1">
            @if (a.salle || a.lieu || a.intervenant || a.moderateur) {
              <p class="act-meta">
                @if (a.salle || a.lieu) { <span><b>Lieu</b> {{ a.salle || a.lieu }}</span> }
                @if (a.intervenant) { <span><b>Intervenant</b> {{ a.intervenant }}</span> }
                @if (a.moderateur) { <span><b>Modérateur</b> {{ a.moderateur }}</span> }
              </p>
            }
            @if (a.description) { <p class="act-desc">{{ a.description }}</p> }
            @if (actions(); as tpl) {
              <ng-container *ngTemplateOutlet="tpl; context: { $implicit: a }" />
            }
          </div>
        </div>
      </div>
    </ng-template>
  `,
  styles: `
    :host { display: block; --line: #cbd5e1; font-size: 0.875rem; }

    .filters { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 1rem; }
    .filters .form-input { width: auto; min-width: 9rem; flex: 1 1 9rem; }
    .filters input[type='search'] { flex: 2 1 14rem; }

    .root {
      display: inline-flex; align-items: center; gap: 0.6rem;
      padding: 0.55rem 1rem; border-radius: 0.75rem;
      background: linear-gradient(135deg, #0f172a, #334155); color: #fff;
    }
    .root-icon { font-size: 0.8rem; opacity: 0.8; }
    .root-title { font-weight: 700; line-height: 1.25; }
    .root-sub { font-size: 0.75rem; opacity: 0.75; }

    /* Tree lines: an elbow into each node, a vertical rail past non-last ones. */
    .tree { list-style: none; margin: 0 0 0 1.1rem; padding: 0; }
    .tree > li { position: relative; padding: 0.6rem 0 0 1.6rem; }
    .tree > li::before {
      content: ''; position: absolute; left: 0; top: 0; width: 1.25rem; height: 1.45rem;
      border-left: 2px solid var(--line); border-bottom: 2px solid var(--line);
      border-bottom-left-radius: 0.5rem;
    }
    .tree > li:not(:last-child)::after {
      content: ''; position: absolute; left: 0; top: 0; bottom: 0;
      border-left: 2px solid var(--line);
    }
    .tree .tree { margin-left: 1.25rem; }

    .day {
      display: inline-flex; flex-wrap: wrap; align-items: baseline; gap: 0.5rem;
      padding: 0.35rem 0.85rem; border-radius: 9999px; text-align: left;
      background: #eef2ff; border: 1px solid #c7d2fe; cursor: pointer;
    }
    .day:hover { background: #e0e7ff; }
    .day:focus-visible { outline: 2px solid #4338ca; outline-offset: 2px; }
    .chev { color: #4338ca; transition: transform 0.15s; display: inline-block; }
    .chev.closed { transform: rotate(-90deg); }
    .day-num { font-size: 0.68rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #4338ca; }
    .day-label { font-weight: 600; color: #1e293b; }
    .day-label::first-letter { text-transform: uppercase; }
    .day-count { font-size: 0.75rem; color: #475569; }

    .continu { display: flex; flex-direction: column; gap: 0.4rem; }
    .continu-title {
      font-size: 0.68rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase;
      color: #15803d;
    }
    .continu .act { background: #f8fafc; border-style: dashed; border-left-style: solid; }

    .act {
      background: #fff; border: 1px solid #e2e8f0; border-left: 4px solid var(--accent);
      border-radius: 0.6rem; padding: 0.5rem 0.75rem;
      box-shadow: 0 1px 2px rgb(15 23 42 / 0.05);
    }
    .act-head { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; }
    .dot { width: 0.5rem; height: 0.5rem; border-radius: 9999px; background: var(--accent); }
    .act-time {
      font-weight: 700; color: var(--accent); font-size: 0.78rem; white-space: nowrap;
      background: color-mix(in srgb, var(--accent) 10%, transparent);
      padding: 0.05rem 0.5rem; border-radius: 9999px;
    }
    .act-title { font-weight: 600; color: #1e293b; }
    .act-type { font-size: 0.65rem; font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase; color: #64748b; }
    .act-body { display: flex; gap: 0.75rem; }
    .act-body:empty { display: none; }
    .act-img { margin-top: 0.4rem; width: 5rem; height: 3.5rem; object-fit: cover; border-radius: 0.4rem; flex-shrink: 0; }
    .act-meta { margin-top: 0.3rem; display: flex; flex-wrap: wrap; gap: 0.25rem 1rem; color: #475569; font-size: 0.78rem; }
    .act-meta b { font-weight: 500; color: #64748b; margin-right: 0.25rem; }
    .act-desc { margin-top: 0.3rem; color: #475569; font-size: 0.78rem; white-space: pre-line; }
  `,
})
export class ProgrammeChartComponent {
  activities = input.required<Activity[]>();
  title = input.required<string>();
  subtitle = input<string>();
  actions = input<TemplateRef<{ $implicit: Activity }>>();
  /** Show the filter bar (day, room, type, theme, keyword). */
  filters = input(false);

  /** Keys of the days the visitor folded. */
  collapsed = signal<Set<string>>(new Set());

  q = signal('');
  fDay = signal('');
  fRoom = signal('');
  fType = signal('');
  fTheme = signal('');
  filtering = computed(() => !!(this.q() || this.fDay() || this.fRoom() || this.fType() || this.fTheme()));

  private sorted = computed(() =>
    [...this.activities()].sort((a, b) => a.dateDebut.localeCompare(b.dateDebut) || a.ordre - b.ordre),
  );

  /** Every day covered by the programme (multi-day sessions included). */
  allDays = computed(() => this.group(this.sorted()).map(({ key, label }) => ({ key, label })));
  rooms = computed(() => uniq(this.activities().map((a) => a.salle || a.lieu)));
  themes = computed(() => uniq(this.activities().map((a) => a.theme)));
  types = computed(() => uniq(this.activities().map((a) => a.typeActivite)) as ActivityType[]);

  /** Days to display, after filtering. */
  days = computed<ProgrammeDay[]>(() => {
    const q = fold(this.q().trim());
    const list = this.sorted().filter((a) => {
      if (this.fRoom() && (a.salle || a.lieu) !== this.fRoom()) return false;
      if (this.fType() && a.typeActivite !== this.fType()) return false;
      if (this.fTheme() && a.theme !== this.fTheme()) return false;
      if (q) {
        const hay = fold([a.titre, a.intervenant, a.moderateur, a.description, a.salle, a.theme].join(' '));
        if (!hay.includes(q)) return false;
      }
      return true;
    });
    const days = this.group(list);
    return this.fDay() ? days.filter((d) => d.key === this.fDay()) : days;
  });

  /** Groups sessions by local calendar day; a multi-day session lands in « continu » of each day. */
  private group(list: Activity[]): ProgrammeDay[] {
    const byDay = new Map<string, ProgrammeDay>();
    const dayOf = (d: Date) => {
      const key = dayKey(d);
      let day = byDay.get(key);
      if (!day) {
        day = { key, label: DAY_FMT.format(d), items: [], continu: [] };
        byDay.set(key, day);
      }
      return day;
    };
    for (const a of list) {
      const start = new Date(a.dateDebut);
      const end = a.dateFin ? new Date(a.dateFin) : start;
      const first = startOfDay(start);
      // An end at exactly midnight does not cover the following day.
      const last = startOfDay(new Date(end.getTime() - (end > start ? 1 : 0)));
      if (last <= first) {
        dayOf(start).items.push(a);
        continue;
      }
      for (let d = first; d <= last; d = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1)) {
        dayOf(d).continu.push(a);
      }
    }
    return [...byDay.values()].sort((x, y) => x.key.localeCompare(y.key));
  }

  dayNumber(key: string): number {
    return this.allDays().findIndex((d) => d.key === key) + 1;
  }

  reset(): void {
    this.q.set('');
    this.fDay.set('');
    this.fRoom.set('');
    this.fType.set('');
    this.fTheme.set('');
  }

  toggle(key: string): void {
    const next = new Set(this.collapsed());
    if (next.has(key)) next.delete(key);
    else next.add(key);
    this.collapsed.set(next);
  }

  color(a: Activity): string {
    return TYPE_COLOR[a.typeActivite as ActivityType] ?? TYPE_COLOR.AUTRE;
  }

  typeLabel(t: ActivityType | undefined): string {
    return t && t !== 'AUTRE' ? ACTIVITY_TYPE_LABELS[t] : 'Activité';
  }

  formatLabel(f: keyof typeof ACTIVITY_FORMAT_LABELS): string {
    return ACTIVITY_FORMAT_LABELS[f];
  }

  slot(a: Activity): string {
    const start = formatTime(a.dateDebut);
    return a.dateFin ? `${start} – ${formatTime(a.dateFin)}` : start;
  }

  /** « Du mar. 24 nov. au ven. 27 nov. · 09:00 – 18:00 » for a multi-day session. */
  span(a: Activity): string {
    const s = new Date(a.dateDebut);
    const e = new Date(a.dateFin!);
    return `Du ${SHORT_FMT.format(s)} au ${SHORT_FMT.format(e)} · ${formatTime(a.dateDebut)} – ${formatTime(a.dateFin!)}`;
  }
}

function uniq(values: (string | undefined | null)[]): string[] {
  return [...new Set(values.filter((v): v is string => !!v))].sort((a, b) => a.localeCompare(b, 'fr'));
}
