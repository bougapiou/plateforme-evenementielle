import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EventsService } from '../events/events.service';
import { EventCategory, EventSummary } from '../events/event.models';
import { formatDateRange, formatTime } from '../../shared/format';
import { IconComponent } from '../../shared/icon.component';

const ZONE = 'Africa/Ouagadougou';

@Component({
  selector: 'app-events-public-list',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <section class="rounded-2xl bg-gradient-to-br from-brand-700 to-brand-900 px-5 py-8 text-white sm:px-8 sm:py-10">
      <p class="text-xs font-semibold uppercase tracking-widest text-brand-100">Découvrez et participez</p>
      <h1 class="mt-2 max-w-xl text-2xl font-extrabold sm:text-4xl">Aux événements qui font la différence</h1>
      <p class="mt-3 max-w-2xl text-sm text-brand-100 sm:text-base">
        Salons, foires, festivals, forums, conférences… Trouvez l'événement qui vous correspond,
        inscrivez-vous et recevez votre billet en quelques clics.
      </p>
    </section>

    <div class="mt-6 flex flex-col gap-3 sm:flex-row">
      <div class="relative flex-1">
        <app-icon name="search" class="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
        <input class="form-input pl-10" placeholder="Rechercher un événement…"
               [(ngModel)]="search" (ngModelChange)="reload()" />
      </div>
      <input class="form-input sm:max-w-[14rem]" placeholder="Ville" [(ngModel)]="ville"
             (ngModelChange)="reload()" />
    </div>

    <div class="-mx-4 mt-4 flex gap-2 overflow-x-auto px-4 pb-1">
      <button type="button" (click)="setCategory('')" class="shrink-0 rounded-full border px-4 py-1.5 text-sm font-medium"
              [class]="categorie === '' ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-200 bg-white text-slate-600 hover:border-brand-300'">
        Tous
      </button>
      @for (c of categories(); track c.id) {
        <button type="button" (click)="setCategory(c.slug)" class="shrink-0 whitespace-nowrap rounded-full border px-4 py-1.5 text-sm font-medium"
                [class]="categorie === c.slug ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-200 bg-white text-slate-600 hover:border-brand-300'">
          {{ c.nom }}
        </button>
      }
    </div>

    <div class="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      @for (e of events(); track e.id) {
        <article class="card flex flex-col overflow-hidden transition hover:shadow-md">
          <!-- Clic sur l'image = détail de l'événement -->
          <a [routerLink]="['/evenements', e.slug]" class="relative block h-44 bg-gradient-to-br from-brand-100 to-slate-100">
            @if (e.coverUrl) { <img [src]="e.coverUrl" alt="" class="h-full w-full object-cover" loading="lazy" /> }
            <span class="absolute left-3 top-3 rounded-xl bg-white px-2.5 py-1.5 text-center leading-tight shadow">
              <span class="block text-xl font-extrabold text-slate-900">{{ day(e) }}</span>
              <span class="block text-xs font-bold uppercase text-brand-700">{{ month(e) }}</span>
              <span class="block text-[10px] text-slate-500">{{ year(e) }}</span>
            </span>
            @if (e.categoryNom) {
              <span class="badge absolute bottom-3 left-3 bg-brand-600 text-white shadow">{{ e.categoryNom }}</span>
            }
          </a>

          <div class="flex flex-1 flex-col p-4">
            <a [routerLink]="['/evenements', e.slug]" class="font-bold text-slate-900 hover:text-brand-700">
              {{ e.nom }}
            </a>
            <p class="mt-2 flex items-center gap-1.5 text-xs text-slate-500">
              <app-icon name="pin" class="h-4 w-4 shrink-0 text-slate-400" />
              <span class="truncate">{{ place(e) }}</span>
            </p>
            <p class="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
              <app-icon name="clock" class="h-4 w-4 shrink-0 text-slate-400" />
              <span>{{ when(e) }}</span>
            </p>
            <!-- Clic sur la description = détail de l'événement -->
            @if (e.descriptionCourte) {
              <a [routerLink]="['/evenements', e.slug]" class="mt-3 line-clamp-2 text-sm text-slate-600 hover:text-slate-900">
                {{ e.descriptionCourte }}
              </a>
            }
            <div class="mt-auto pt-4">
              <a [routerLink]="['/evenements', e.slug]" [queryParams]="{ participer: 1 }"
                 class="btn-primary w-full justify-center gap-2 text-center leading-tight">
                <app-icon name="ticket" class="h-4 w-4 shrink-0" />
                S'inscrire et prendre un billet
              </a>
            </div>
          </div>
        </article>
      } @empty {
        <p class="card col-span-full p-6 text-sm text-slate-500">Aucun événement publié pour le moment.</p>
      }
    </div>
  `,
})
export class EventsPublicListComponent {
  private service = inject(EventsService);
  events = signal<EventSummary[]>([]);
  categories = signal<EventCategory[]>([]);
  search = '';
  categorie = '';
  ville = '';

  constructor() {
    this.service.categories().subscribe((c) => this.categories.set(c));
    this.reload();
  }

  setCategory(slug: string): void {
    this.categorie = slug;
    this.reload();
  }

  reload(): void {
    this.service
      .publicList({ search: this.search, categorie: this.categorie, ville: this.ville })
      .subscribe((p) => this.events.set(p.content));
  }

  private part = (iso: string, opts: Intl.DateTimeFormatOptions) =>
    new Intl.DateTimeFormat('fr-FR', { ...opts, timeZone: ZONE }).format(new Date(iso));

  day = (e: EventSummary) => this.part(e.dateDebut, { day: '2-digit' });
  month = (e: EventSummary) => this.part(e.dateDebut, { month: 'short' }).replace('.', '');
  year = (e: EventSummary) => this.part(e.dateDebut, { year: 'numeric' });

  place = (e: EventSummary) => [e.ville, e.lieu].filter(Boolean).join(' · ') || '—';

  /** Same day: opening hours; several days: the date range. */
  when = (e: EventSummary) => {
    const sameDay = new Date(e.dateDebut).toDateString() === new Date(e.dateFin).toDateString();
    return sameDay
      ? `${formatTime(e.dateDebut)} – ${formatTime(e.dateFin)}`
      : formatDateRange(e.dateDebut, e.dateFin);
  };
}
