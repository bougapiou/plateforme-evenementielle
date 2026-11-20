import { Component, TemplateRef, computed, input, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Activity, ActivityType } from '../features/events/event.models';
import { formatTime } from './format';

interface ProgrammeDay {
  key: string;
  label: string;
  items: Activity[];
}

/** Display label and accent colour of each activity type. */
const TYPE_STYLE: Record<ActivityType, { label: string; color: string }> = {
  CEREMONIE: { label: 'Cérémonie', color: '#b45309' },
  CONFERENCE: { label: 'Conférence', color: '#1d4ed8' },
  PANEL: { label: 'Panel', color: '#7c3aed' },
  ATELIER: { label: 'Atelier', color: '#0f766e' },
  FORMATION: { label: 'Formation', color: '#0369a1' },
  TABLE_RONDE: { label: 'Table ronde', color: '#9333ea' },
  NETWORKING: { label: 'Networking', color: '#c2410c' },
  PAUSE: { label: 'Pause', color: '#64748b' },
  SPECTACLE: { label: 'Spectacle', color: '#be123c' },
  AUTRE: { label: 'Activité', color: '#475569' },
};

const DAY_FMT = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

/**
 * Event programme rendered as a tree (arborescence): the event at the root,
 * one collapsible branch per day, the day's activities as leaves in
 * chronological order. Per-activity actions (participation, ticket…) are
 * projected through the optional `actions` template (`$implicit` = activity).
 */
@Component({
  selector: 'app-programme-chart',
  standalone: true,
  imports: [NgTemplateOutlet],
  template: `
    <div class="root">
      <span class="root-icon">◆</span>
      <div>
        <p class="root-title">{{ title() }}</p>
        @if (subtitle()) { <p class="root-sub">{{ subtitle() }}</p> }
      </div>
    </div>

    <ul class="tree">
      @for (d of days(); track d.key; let i = $index) {
        <li>
          <button type="button" class="day" (click)="toggle(d.key)"
                  [attr.aria-expanded]="!collapsed().has(d.key)">
            <span class="chev" [class.closed]="collapsed().has(d.key)">▾</span>
            @if (days().length > 1) { <span class="day-num">Jour {{ i + 1 }}</span> }
            <span class="day-label">{{ d.label }}</span>
            <span class="day-count">{{ d.items.length }} activité{{ d.items.length > 1 ? 's' : '' }}</span>
          </button>

          @if (!collapsed().has(d.key)) {
            <ul class="tree">
              @for (a of d.items; track a.id) {
                <li [style.--accent]="style(a).color">
                  <div class="act">
                    <div class="act-head">
                      <span class="dot"></span>
                      <span class="act-time">{{ slot(a) }}</span>
                      <span class="act-title">{{ a.titre }}</span>
                      <span class="act-type">{{ style(a).label }}</span>
                      @if (a.acces === 'GRATUIT') {
                        <span class="badge bg-green-100 text-green-800">Gratuit</span>
                      } @else if (a.acces === 'PAYANT') {
                        <span class="badge bg-amber-100 text-amber-800">Payant</span>
                      }
                    </div>
                    <div class="act-body">
                      @if (a.imageUrl) { <img [src]="a.imageUrl" alt="" class="act-img" /> }
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
                </li>
              }
            </ul>
          }
        </li>
      }
    </ul>
  `,
  styles: `
    :host { display: block; --line: #cbd5e1; font-size: 0.875rem; }

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
    .chev { color: #4338ca; transition: transform 0.15s; display: inline-block; }
    .chev.closed { transform: rotate(-90deg); }
    .day-num { font-size: 0.68rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #4338ca; }
    .day-label { font-weight: 600; color: #1e293b; }
    .day-label::first-letter { text-transform: uppercase; }
    .day-count { font-size: 0.75rem; color: #64748b; }

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
    .act-type { font-size: 0.65rem; font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase; color: #94a3b8; }
    .act-body { display: flex; gap: 0.75rem; }
    .act-body:empty { display: none; }
    .act-img { margin-top: 0.4rem; width: 5rem; height: 3.5rem; object-fit: cover; border-radius: 0.4rem; flex-shrink: 0; }
    .act-meta { margin-top: 0.3rem; display: flex; flex-wrap: wrap; gap: 0.25rem 1rem; color: #64748b; font-size: 0.78rem; }
    .act-meta b { font-weight: 500; color: #94a3b8; margin-right: 0.25rem; }
    .act-desc { margin-top: 0.3rem; color: #64748b; font-size: 0.78rem; }
  `,
})
export class ProgrammeChartComponent {
  activities = input.required<Activity[]>();
  title = input.required<string>();
  subtitle = input<string>();
  actions = input<TemplateRef<{ $implicit: Activity }>>();

  /** Keys of the days the visitor folded. */
  collapsed = signal<Set<string>>(new Set());

  /** Activities grouped by local calendar day, each day sorted by start time. */
  days = computed<ProgrammeDay[]>(() => {
    const sorted = [...this.activities()].sort(
      (a, b) => a.dateDebut.localeCompare(b.dateDebut) || a.ordre - b.ordre,
    );
    const byDay = new Map<string, ProgrammeDay>();
    for (const a of sorted) {
      const d = new Date(a.dateDebut);
      const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
      let day = byDay.get(key);
      if (!day) {
        day = { key, label: DAY_FMT.format(d), items: [] };
        byDay.set(key, day);
      }
      day.items.push(a);
    }
    return [...byDay.values()];
  });

  toggle(key: string): void {
    const next = new Set(this.collapsed());
    if (next.has(key)) next.delete(key);
    else next.add(key);
    this.collapsed.set(next);
  }

  style(a: Activity) {
    return TYPE_STYLE[a.typeActivite ?? 'AUTRE'] ?? TYPE_STYLE.AUTRE;
  }

  slot(a: Activity): string {
    const start = formatTime(a.dateDebut);
    return a.dateFin ? `${start} – ${formatTime(a.dateFin)}` : start;
  }
}
