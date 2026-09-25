import {
  Component, ElementRef, HostListener, OnDestroy, computed, effect, input, output, signal, viewChild,
} from '@angular/core';
import { ActivityFlow, AttendanceView } from '../features/checkin/checkin.service';
import { IconComponent, IconName } from './icon.component';
import { PresenceSourcePickerComponent } from './presence-source-picker.component';
import {
  PRESENCE_SOURCES, PresenceSource, contributions, flowOf, hasPhysicalCount, loadSource, saveSource,
} from './presence';
import { formatDateTime } from './format';

type CounterKey = 'entrees' | 'sorties' | 'presents' | 'reentrees';

interface Counter {
  key: CounterKey;
  label: string;
  icon: IconName;
  value: number;
  /** What each source adds to a combined counter. */
  detail: string | null;
}

const TONES: Record<CounterKey, {
  dark: string; darkIcon: string; darkValue: string; darkLabel: string;
  light: string; lightIcon: string; lightValue: string; lightLabel: string;
}> = {
  entrees: {
    dark: 'border-emerald-800/50 bg-emerald-950/40', darkIcon: 'text-emerald-400',
    darkValue: 'text-emerald-300', darkLabel: 'text-emerald-500',
    light: 'bg-green-50', lightIcon: 'text-green-600', lightValue: 'text-green-700', lightLabel: 'text-green-600',
  },
  sorties: {
    dark: 'border-slate-700 bg-slate-900/60', darkIcon: 'text-slate-300',
    darkValue: 'text-slate-100', darkLabel: 'text-slate-400',
    light: 'bg-slate-100', lightIcon: 'text-slate-600', lightValue: 'text-slate-700', lightLabel: 'text-slate-600',
  },
  presents: {
    dark: 'border-sky-800/50 bg-sky-950/40', darkIcon: 'text-sky-400',
    darkValue: 'text-sky-300', darkLabel: 'text-sky-500',
    light: 'bg-brand-50', lightIcon: 'text-brand-600', lightValue: 'text-brand-700', lightLabel: 'text-brand-600',
  },
  reentrees: {
    dark: 'border-amber-800/50 bg-amber-950/40', darkIcon: 'text-amber-400',
    darkValue: 'text-amber-300', darkLabel: 'text-amber-500',
    light: 'bg-amber-50', lightIcon: 'text-amber-600', lightValue: 'text-amber-700', lightLabel: 'text-amber-600',
  },
};

const NUMBER = new Intl.NumberFormat('fr-FR');

function enterFullscreen(el: HTMLElement): void {
  try {
    const result = el.requestFullscreen?.();
    if (result && typeof result.catch === 'function') result.catch(() => {});
  } catch {
    /* unsupported (e.g. iPhone): the on-page full-window mode still applies */
  }
}

function leaveFullscreen(): void {
  try {
    if (!document.fullscreenElement) return;
    const result = document.exitFullscreen?.();
    if (result && typeof result.catch === 'function') result.catch(() => {});
  } catch {
    /* nothing to leave */
  }
}

/**
 * The live presence numbers of an event — entries, exits, people inside and re-entries — with the choice
 * of where they come from: ticket scans (QR), laser sensors, or both added up. It can grow to the whole
 * screen (browser full screen, or a full-window mode where that API is missing), and in `kiosk` mode
 * it is that full-screen board from the start, for a monitor at the venue.
 */
@Component({
  selector: 'app-presence-panel',
  standalone: true,
  imports: [IconComponent, PresenceSourcePickerComponent],
  template: `
    <section #root [class]="rootClass()">
      @if (dark()) {
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="min-w-0">
            <p class="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
              <span class="relative flex h-2.5 w-2.5">
                <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-60"></span>
                <span class="relative inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500"></span>
              </span>
              Présence en direct
            </p>
            <h1 class="mt-1 break-words text-2xl font-bold sm:text-4xl">{{ title() }}</h1>
            <p class="mt-1 text-sm text-slate-400 sm:text-base">{{ subtitle() }}</p>
          </div>
          <div class="flex w-full flex-wrap items-center justify-between gap-2 sm:w-auto sm:justify-end">
            @if (updatedAt()) {
              <span class="hidden text-xs text-slate-500 sm:inline">Mis à jour {{ updatedAt() }}</span>
            }
            @if (sensorsApply() && data()) {
              <app-presence-source-picker [value]="source()" [dark]="true" (changed)="pick($event)" />
            }
            <button type="button" [title]="fullscreenLabel()" [attr.aria-label]="fullscreenLabel()"
                    class="rounded-lg border border-slate-700 p-2 text-slate-300 hover:text-white"
                    (click)="toggleFullscreen()">
              <app-icon [name]="expanded() ? 'x' : 'expand'" class="h-5 w-5" />
            </button>
            @if (closable()) {
              <button type="button" title="Fermer" aria-label="Fermer"
                      class="rounded-lg border border-slate-700 p-2 text-slate-300 hover:text-white"
                      (click)="closed.emit()">
                <app-icon name="x" class="h-5 w-5" />
              </button>
            }
          </div>
        </div>

        @if (message()) {
          <p class="mt-10 text-lg text-slate-300">{{ message() }}</p>
        } @else if (data()) {
          <div class="mt-6 grid flex-1 auto-rows-fr gap-3 sm:mt-8 sm:gap-6"
               [class]="counters().length === 4 ? 'grid-cols-2' : 'grid-cols-1 sm:grid-cols-3'">
            @for (c of counters(); track c.key) {
              <div class="flex min-h-0 flex-col items-center justify-center rounded-3xl border p-3 text-center sm:p-6"
                   [class]="tone(c.key).dark">
                <app-icon [name]="c.icon" class="h-7 w-7 sm:h-9 sm:w-9" [class]="tone(c.key).darkIcon" />
                <p class="mt-2 font-black leading-none tabular-nums sm:mt-3" [style.font-size]="valueSize()"
                   [class]="tone(c.key).darkValue">{{ fmt(c.value) }}</p>
                <p class="mt-2 text-xs font-semibold uppercase tracking-widest sm:text-base"
                   [class]="tone(c.key).darkLabel">{{ c.label }}</p>
                @if (c.detail) {
                  <p class="mt-1 text-xs text-slate-400 sm:text-sm">{{ c.detail }}</p>
                }
              </div>
            }
          </div>
          @if (noSensorData()) {
            <p class="mt-4 text-center text-xs text-slate-500 sm:text-sm">
              Aucun passage n'a encore été compté par un capteur pour cet événement.
            </p>
          }
        } @else {
          <p class="mt-10 text-slate-400">Chargement…</p>
        }
      } @else {
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div class="min-w-0">
            <h2 class="break-words text-lg font-semibold text-slate-800">{{ title() }}</h2>
            @if (data()) {
              <p class="text-sm text-slate-500">
                {{ subtitle() }}
                @if (refreshSeconds()) { · mise à jour automatique toutes les {{ refreshSeconds() }} s }
              </p>
            }
          </div>
          @if (data()) {
            <div class="flex flex-wrap items-center gap-2">
              @if (sensorsApply()) {
                <app-presence-source-picker [value]="source()" (changed)="pick($event)" />
              }
              <button type="button" class="btn-ghost text-brand-700" (click)="toggleFullscreen()">
                <app-icon name="expand" class="h-4 w-4" /> Plein écran
              </button>
            </div>
          }
        </div>

        @if (message()) {
          <p class="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ message() }}</p>
        } @else if (data()) {
          <div class="mt-4 grid gap-3" [class]="counters().length === 4 ? 'grid-cols-2 sm:grid-cols-4' : 'grid-cols-1 sm:grid-cols-3'">
            @for (c of counters(); track c.key) {
              <div class="rounded-lg p-3 text-center" [class]="tone(c.key).light">
                <app-icon [name]="c.icon" class="mx-auto h-5 w-5" [class]="tone(c.key).lightIcon" />
                <p class="text-2xl font-bold tabular-nums" [class]="tone(c.key).lightValue">{{ fmt(c.value) }}</p>
                <p class="text-xs" [class]="tone(c.key).lightLabel">{{ c.label.toLowerCase() }}</p>
                @if (c.detail) {
                  <p class="mt-1 text-[11px] text-slate-500">{{ c.detail }}</p>
                }
              </div>
            }
          </div>

          @if (source() === 'combine') {
            <p class="mt-3 text-xs text-slate-400">
              Combiné : chaque compteur additionne les scans de billets et les passages comptés par les capteurs.
              À utiliser quand les deux ne comptent pas les mêmes accès — sinon une personne serait comptée deux fois.
            </p>
          }
          @if (noSensorData()) {
            <p class="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
              Aucun passage n'a encore été compté par un capteur pour cet événement.
            </p>
          }

          @if (!activityId() && data()!.activites.length) {
            <h3 class="mt-6 font-semibold text-slate-800">Par activité</h3>
            @if (source() === 'physique') {
              <p class="mt-1 text-sm text-slate-500">
                Les capteurs comptent les passages à l'entrée de l'événement, pas activité par activité :
                choisissez « Billets QR » ou « Combiné » pour voir le détail des activités.
              </p>
            } @else {
              @if (source() === 'combine') {
                <p class="text-xs text-slate-400">Détail par activité : scans de billets uniquement.</p>
              }
              <div class="mt-2 overflow-x-auto">
                <table class="w-full min-w-[560px] text-sm">
                  <thead>
                    <tr class="border-b border-slate-200 text-left text-slate-500">
                      <th class="py-2 pr-3">Activité</th>
                      <th class="px-2 text-center">Entrées</th>
                      <th class="px-2 text-center">Sorties</th>
                      <th class="px-2 text-center">Présents</th>
                      <th class="px-2 text-center">Ré-entrées</th>
                      @if (activityLink()) { <th class="pl-2"></th> }
                    </tr>
                  </thead>
                  <tbody>
                    @for (a of data()!.activites; track a.id) {
                      <tr class="border-b border-slate-100">
                        <td class="py-2 pr-3">
                          <p class="font-medium text-slate-800">{{ a.titre }}</p>
                          <p class="text-xs text-slate-400">
                            {{ a.dateDebut ? dt(a.dateDebut) : '' }}
                            {{ a.acces === 'PAYANT' ? '· payant' : a.acces === 'GRATUIT' ? '· gratuit' : '' }}
                          </p>
                        </td>
                        <td class="px-2 text-center font-semibold text-green-700">{{ a.flux['entrees'] || 0 }}</td>
                        <td class="px-2 text-center font-semibold text-slate-700">{{ a.flux['sorties'] || 0 }}</td>
                        <td class="px-2 text-center font-semibold text-brand-700">{{ a.flux['presents'] || 0 }}</td>
                        <td class="px-2 text-center font-semibold text-amber-700">{{ a.flux['reentrees'] || 0 }}</td>
                        @if (activityLink(); as link) {
                          <td class="pl-2 text-right">
                            <a [href]="link(a)" target="_blank" rel="noopener" title="Plein écran"
                               class="text-slate-400 hover:text-brand-700">
                              <app-icon name="expand" class="h-4 w-4" />
                            </a>
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          }
        } @else {
          <p class="mt-4 text-sm text-slate-500">Chargement…</p>
        }
      }
    </section>
  `,
})
export class PresencePanelComponent implements OnDestroy {
  data = input<AttendanceView | null>(null);
  /** Show one activity of the event instead of the event (sensors count at the door, so they do not apply). */
  activityId = input<string | null>(null);
  /** Chrome-free, always dark and full page: the route meant for a monitor at the venue. */
  kiosk = input(false);
  /** Source forced by the link that opened the screen (`?source=`). */
  initialSource = input<PresenceSource | null>(null);
  refreshSeconds = input(0);
  titleFallback = input('');
  /** Shown instead of the numbers, e.g. "event not found". */
  message = input<string | null>(null);
  closable = input(false);
  /** One link per activity to open that activity's own full-screen board. */
  activityLink = input<((a: ActivityFlow) => string) | null>(null);
  closed = output<void>();

  private root = viewChild.required<ElementRef<HTMLElement>>('root');
  private clicked = signal<PresenceSource | null>(null);
  private stored = loadSource();
  expanded = signal(false);
  private nativeFullscreen = signal(false);

  dark = computed(() => this.kiosk() || this.expanded());
  rootClass = computed(() => {
    if (this.kiosk()) return 'flex min-h-screen flex-col bg-slate-950 p-4 text-white sm:p-8';
    if (this.expanded()) return 'fixed inset-0 z-50 flex flex-col overflow-y-auto bg-slate-950 p-4 text-white sm:p-8';
    return '';
  });

  activity = computed(() => {
    const id = this.activityId();
    return id ? (this.data()?.activites.find((a) => a.id === id) ?? null) : null;
  });
  sensorsApply = computed(() => !this.activityId());

  source = computed<PresenceSource>(() => {
    if (!this.sensorsApply()) return 'qr';
    const auto: PresenceSource = hasPhysicalCount(this.data()?.comptagePhysique) ? 'combine' : 'qr';
    return this.clicked() ?? this.initialSource() ?? this.stored ?? auto;
  });

  private qr = computed(() => this.activity()?.flux ?? this.data()?.event);
  private physique = computed(() => (this.sensorsApply() ? this.data()?.comptagePhysique : undefined));

  counters = computed<Counter[]>(() => {
    const f = flowOf(this.source(), this.qr(), this.physique());
    const combined = this.source() === 'combine';
    const detail = (key: 'entrees' | 'sorties' | 'presents'): string | null => {
      if (!combined) return null;
      const c = contributions(key, this.qr(), this.physique());
      return `Billets ${NUMBER.format(c.qr)} · Capteurs ${NUMBER.format(c.physique)}`;
    };
    const list: Counter[] = [
      { key: 'entrees', label: 'Entrées', icon: 'login', value: f.entrees, detail: detail('entrees') },
      { key: 'sorties', label: 'Sorties', icon: 'logout', value: f.sorties, detail: detail('sorties') },
      { key: 'presents', label: 'Présents', icon: 'present', value: f.presents, detail: detail('presents') },
    ];
    if (f.reentrees !== null) {
      list.push({
        key: 'reentrees', label: 'Ré-entrées', icon: 'repeat', value: f.reentrees,
        detail: combined ? 'Billets uniquement' : null,
      });
    }
    return list;
  });

  /** Big numbers scale with the screen height and width, so the board fits a 768px monitor as well as a TV. */
  valueSize = computed(() =>
    this.counters().length === 4 ? 'clamp(2.5rem, min(11vh, 10vw), 9rem)' : 'clamp(2.75rem, min(17vh, 11vw), 11rem)',
  );

  noSensorData = computed(
    () => this.sensorsApply() && this.source() !== 'qr' && !hasPhysicalCount(this.data()?.comptagePhysique),
  );

  title = computed(() => this.data()?.eventNom ?? this.titleFallback());
  subtitle = computed(() => {
    const activity = this.activity();
    if (activity) return activity.titre;
    return PRESENCE_SOURCES.find((s) => s.value === this.source())!.description;
  });

  /** Time of the last refresh: recomputed each time new numbers arrive. */
  updatedAt = computed(() => (this.data() ? new Date().toLocaleTimeString('fr-FR') : ''));

  fullscreenLabel = computed(() => (this.nativeFullscreen() || this.expanded() ? 'Quitter le plein écran' : 'Plein écran'));

  constructor() {
    // while the board covers the page, the page behind must not scroll
    effect(() => {
      document.body.style.overflow = this.expanded() ? 'hidden' : '';
    });
  }

  ngOnDestroy(): void {
    document.body.style.overflow = '';
    if (this.expanded() || this.kiosk()) leaveFullscreen();
  }

  tone = (key: CounterKey) => TONES[key];
  fmt = (n: number) => NUMBER.format(n);
  dt = (iso?: string) => formatDateTime(iso);

  pick(source: PresenceSource): void {
    this.clicked.set(source);
    saveSource(source);
  }

  toggleFullscreen(): void {
    const el = this.root().nativeElement;
    if (this.kiosk()) {
      if (document.fullscreenElement) leaveFullscreen();
      else enterFullscreen(el);
      return;
    }
    if (this.expanded()) {
      this.expanded.set(false);
      leaveFullscreen();
    } else {
      this.expanded.set(true);
      enterFullscreen(el);
    }
  }

  @HostListener('document:fullscreenchange')
  onFullscreenChange(): void {
    const active = !!document.fullscreenElement;
    this.nativeFullscreen.set(active);
    // Esc leaves the browser's full screen: leave the full-window mode with it
    if (!active && this.expanded()) this.expanded.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.expanded() && !document.fullscreenElement) this.expanded.set(false);
  }
}
