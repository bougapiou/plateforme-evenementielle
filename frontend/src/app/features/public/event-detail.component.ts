import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventsService } from '../events/events.service';
import { EventPublic } from '../events/event.models';
import { formatDateRange, formatDateTime, formatTime } from '../../shared/format';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (event(); as e) {
      <div class="rounded-xl bg-slate-100">
        @if (e.coverUrl) { <img [src]="e.coverUrl" alt="" class="h-56 w-full rounded-xl object-cover" /> }
      </div>

      <div class="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div>
          @if (e.categoryNom) {
            <span class="badge bg-brand-50 text-brand-700">{{ e.categoryNom }}</span>
          }
          <h1 class="mt-2 text-2xl font-extrabold text-slate-900">{{ e.nom }}</h1>
          <p class="mt-1 text-slate-500">
            {{ range(e) }} · {{ e.lieu || '' }}{{ e.ville ? ', ' + e.ville : '' }}
          </p>
          <p class="text-sm text-slate-400">Organisé par {{ e.organizerNom }}</p>
        </div>
        <div class="flex gap-2">
          <button class="btn-primary" disabled title="Disponible avec la billetterie">
            Acheter un ticket
          </button>
          @if (e.standsActifs) {
            <button class="btn-ghost border border-slate-300" disabled>Réserver un stand</button>
          }
        </div>
      </div>

      @if (e.descriptionDetaillee || e.descriptionCourte) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Présentation</h2>
          <p class="mt-2 whitespace-pre-line text-sm text-slate-600">
            {{ e.descriptionDetaillee || e.descriptionCourte }}
          </p>
        </section>
      }

      @if (e.hasActivities && e.programme.length) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Programme</h2>
          <ol class="mt-3 space-y-3">
            @for (a of e.programme; track a.id) {
              <li class="flex gap-3 text-sm">
                <span class="w-14 shrink-0 font-semibold text-brand-700">{{ time(a.dateDebut) }}</span>
                <div>
                  <p class="font-medium text-slate-800">{{ a.titre }}</p>
                  <p class="text-slate-400">
                    {{ a.salle }}{{ a.intervenant ? ' · ' + a.intervenant : '' }}{{ a.moderateur ? ' · modération : ' + a.moderateur : '' }}
                  </p>
                </div>
              </li>
            }
          </ol>
        </section>
      }

      @if (e.intervenants.length) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Intervenants</h2>
          <div class="mt-3 grid gap-3 sm:grid-cols-2">
            @for (s of e.intervenants; track s.id) {
              <div class="rounded-lg border border-slate-100 p-3 text-sm">
                <p class="font-medium text-slate-800">{{ s.nom }}</p>
                <p class="text-slate-400">{{ s.titre }}{{ s.organisation ? ' · ' + s.organisation : '' }}</p>
                @if (s.bio) { <p class="mt-1 text-slate-500">{{ s.bio }}</p> }
              </div>
            }
          </div>
        </section>
      }

      @if (e.partenaires.length) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Partenaires</h2>
          <ul class="mt-3 flex flex-wrap gap-2 text-sm">
            @for (p of e.partenaires; track p.id) {
              <li class="badge bg-slate-100 text-slate-600">{{ p.nom }}</li>
            }
          </ul>
        </section>
      }

      <section class="card mt-6 p-5">
        <h2 class="font-semibold text-slate-800">Informations pratiques</h2>
        <dl class="mt-3 grid gap-2 text-sm sm:grid-cols-2">
          <div><dt class="text-slate-400">Dates</dt><dd>{{ range(e) }}</dd></div>
          <div><dt class="text-slate-400">Lieu</dt><dd>{{ e.lieu || '—' }}{{ e.adresse ? ', ' + e.adresse : '' }}</dd></div>
          <div><dt class="text-slate-400">Ville</dt><dd>{{ e.ville || '—' }}, {{ e.pays }}</dd></div>
          <div><dt class="text-slate-400">Contact</dt><dd>{{ e.contactEmail || e.contactTelephone || '—' }}</dd></div>
          @if (e.inscriptionFin) {
            <div><dt class="text-slate-400">Clôture des inscriptions</dt><dd>{{ dt(e.inscriptionFin) }}</dd></div>
          }
        </dl>
        @if (e.conditionsParticipation) {
          <p class="mt-3 text-sm text-slate-500"><b>Conditions :</b> {{ e.conditionsParticipation }}</p>
        }
      </section>

      <p class="mt-6"><a routerLink="/" class="text-sm text-brand-700">← Tous les événements</a></p>
    } @else if (error()) {
      <p class="py-16 text-center text-slate-500">{{ error() }}</p>
    } @else {
      <p class="py-16 text-center text-slate-400">Chargement…</p>
    }
  `,
})
export class EventDetailComponent {
  private service = inject(EventsService);
  slug = input.required<string>();
  event = signal<EventPublic | null>(null);
  error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const slug = this.slug();
      if (slug) {
        this.service.publicBySlug(slug).subscribe({
          next: (e) => this.event.set(e),
          error: () => this.error.set("Cet événement n'est pas disponible."),
        });
      }
    });
  }

  range = (e: EventPublic) => formatDateRange(e.dateDebut, e.dateFin);
  dt = (iso?: string) => formatDateTime(iso);
  time = (iso?: string) => formatTime(iso);
}
