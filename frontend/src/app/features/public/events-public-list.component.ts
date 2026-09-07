import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EventsService } from '../events/events.service';
import { EventCategory, EventSummary } from '../events/event.models';
import { formatDateRange } from '../../shared/format';

@Component({
  selector: 'app-events-public-list',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="rounded-xl bg-gradient-to-br from-brand-700 to-brand-900 px-6 py-10 text-white">
      <h1 class="text-2xl font-extrabold sm:text-3xl">Événements à venir</h1>
      <p class="mt-2 max-w-2xl text-brand-100">
        Salons, foires, festivals, forums, conférences… Inscrivez-vous, achetez vos billets
        et réservez vos stands en ligne.
      </p>
    </section>

    <div class="mt-6 flex flex-wrap gap-3">
      <input class="form-input max-w-xs" placeholder="Rechercher un événement…"
             [(ngModel)]="search" (ngModelChange)="reload()" />
      <select class="form-input max-w-xs" [(ngModel)]="categorie" (ngModelChange)="reload()">
        <option value="">Toutes les catégories</option>
        @for (c of categories(); track c.id) { <option [value]="c.slug">{{ c.nom }}</option> }
      </select>
      <input class="form-input max-w-xs" placeholder="Ville" [(ngModel)]="ville"
             (ngModelChange)="reload()" />
    </div>

    <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      @for (e of events(); track e.id) {
        <a [routerLink]="['/evenements', e.slug]" class="card flex flex-col overflow-hidden hover:border-brand-300">
          <div class="h-32 bg-slate-100">
            @if (e.coverUrl) { <img [src]="e.coverUrl" alt="" class="h-full w-full object-cover" /> }
          </div>
          <div class="flex flex-1 flex-col p-4">
            @if (e.categoryNom) {
              <span class="badge mb-2 self-start bg-brand-50 text-brand-700">{{ e.categoryNom }}</span>
            }
            <h3 class="font-semibold text-slate-800">{{ e.nom }}</h3>
            <p class="mt-1 line-clamp-2 text-sm text-slate-500">{{ e.descriptionCourte }}</p>
            <p class="mt-3 text-xs text-slate-400">
              {{ range(e) }} · {{ e.ville || e.lieu || '—' }}
            </p>
            <span class="mt-3 text-sm font-semibold text-brand-700">Voir l'événement →</span>
          </div>
        </a>
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

  range = (e: EventSummary) => formatDateRange(e.dateDebut, e.dateFin);

  reload(): void {
    this.service
      .publicList({ search: this.search, categorie: this.categorie, ville: this.ville })
      .subscribe((p) => this.events.set(p.content));
  }
}
