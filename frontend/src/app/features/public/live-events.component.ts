import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventsService } from '../events/events.service';
import { EventSummary } from '../events/event.models';
import { EventCoverComponent } from '../../shared/event-cover.component';
import { IconComponent } from '../../shared/icon.component';
import { formatDateRange, formatTime } from '../../shared/format';

/**
 * Entrée « Présence en direct » du menu public : les événements en cours, chacun avec un accès à ses
 * compteurs (entrées, sorties, présents — billets scannés et capteurs) et à l'affichage plein écran.
 */
@Component({
  selector: 'app-live-events',
  standalone: true,
  imports: [RouterLink, IconComponent, EventCoverComponent],
  template: `
    <div class="mx-auto max-w-3xl">
      <h1 class="text-xl font-bold text-slate-800">Présence en direct</h1>
      <p class="mt-1 text-sm text-slate-500">
        Combien de personnes sont entrées, sorties ou présentes en ce moment : billets scannés à l'entrée
        et passages comptés par les capteurs, séparément ou additionnés — avec un affichage plein écran.
      </p>

      @if (loading()) {
        <p class="mt-6 text-sm text-slate-500">Chargement…</p>
      } @else if (error()) {
        <p class="mt-6 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          Impossible de charger les événements en cours. Réessayez dans un instant.
        </p>
      } @else {
        <div class="mt-6 space-y-4">
          @for (e of events(); track e.id) {
            <article class="card flex flex-col overflow-hidden sm:flex-row">
              <app-event-cover class="h-32 w-full shrink-0 sm:h-auto sm:w-44"
                               [nom]="e.nom" [coverUrl]="e.coverUrl" />
              <div class="flex min-w-0 flex-1 flex-col p-4">
                <span class="mb-1 inline-flex w-fit items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">
                  <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-500"></span> En cours
                </span>
                <h2 class="break-words font-bold text-slate-900">{{ e.nom }}</h2>
                <p class="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                  <app-icon name="pin" class="h-4 w-4 shrink-0 text-slate-400" />
                  <span class="truncate">{{ place(e) }}</span>
                </p>
                <p class="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                  <app-icon name="clock" class="h-4 w-4 shrink-0 text-slate-400" />
                  <span>{{ when(e) }}</span>
                </p>
                <div class="mt-4 flex flex-wrap gap-2">
                  <a [routerLink]="['/evenements', e.slug, 'flux']" class="btn-primary">
                    <app-icon name="present" class="h-4 w-4" /> Voir la présence
                  </a>
                  <a [routerLink]="['/presence-publique', e.slug]" target="_blank" rel="noopener"
                     class="btn-ghost border border-slate-200">
                    <app-icon name="expand" class="h-4 w-4" /> Plein écran
                  </a>
                </div>
              </div>
            </article>
          } @empty {
            <div class="card p-6 text-center">
              <app-icon name="clock" class="mx-auto h-8 w-8 text-slate-300" />
              <p class="mt-2 font-medium text-slate-700">Aucun événement en cours pour le moment.</p>
              <p class="mt-1 text-sm text-slate-500">
                La présence en direct apparaît ici dès qu'un événement commence.
              </p>
              <a routerLink="/" class="btn-primary mt-4">Voir les événements à venir</a>
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class LiveEventsComponent {
  private service = inject(EventsService);

  events = signal<EventSummary[]>([]);
  loading = signal(true);
  error = signal(false);

  constructor() {
    this.service.liveEvents().subscribe({
      next: (list) => {
        this.events.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  place = (e: EventSummary) => [e.ville, e.lieu].filter(Boolean).join(' · ') || '—';

  when = (e: EventSummary) => {
    const sameDay = new Date(e.dateDebut).toDateString() === new Date(e.dateFin).toDateString();
    return sameDay
      ? `${formatTime(e.dateDebut)} – ${formatTime(e.dateFin)}`
      : formatDateRange(e.dateDebut, e.dateFin);
  };
}
