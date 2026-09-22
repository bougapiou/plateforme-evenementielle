import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { AttendanceView, CheckinService } from '../checkin/checkin.service';
import { IconComponent } from '../../shared/icon.component';
import { formatDateTime } from '../../shared/format';

const REFRESH_MS = 8000;

/**
 * Présence en direct pour un événement, sans connexion — un lien dédié par
 * événement (pas de sélecteur d'événements ici : ce serait une liste
 * publique de tout ce qui est "contrôlable", qu'on ne veut pas exposer).
 * Accessible uniquement si l'événement est déjà visible sur le site public
 * (voir EventStatus.isPubliclyVisible côté backend) — un brouillon reste privé.
 */
@Component({
  selector: 'app-public-attendance',
  standalone: true,
  imports: [IconComponent],
  template: `
    @if (data(); as d) {
      <div class="flex items-center justify-between">
        <h1 class="text-xl font-bold text-slate-800">{{ d.eventNom }}</h1>
        <span class="text-xs text-slate-400">Mise à jour automatique toutes les {{ refreshSeconds }} s</span>
      </div>
      <p class="mt-1 text-sm text-slate-500">Présence en direct</p>

      <div class="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div class="rounded-lg bg-green-50 p-3 text-center">
          <app-icon name="login" class="mx-auto h-5 w-5 text-green-600" />
          <p class="text-2xl font-bold text-green-700">{{ d.event['entrees'] || 0 }}</p>
          <p class="text-xs text-green-600">entrées</p>
        </div>
        <div class="rounded-lg bg-slate-100 p-3 text-center">
          <app-icon name="logout" class="mx-auto h-5 w-5 text-slate-600" />
          <p class="text-2xl font-bold text-slate-700">{{ d.event['sorties'] || 0 }}</p>
          <p class="text-xs text-slate-600">sorties</p>
        </div>
        <div class="rounded-lg bg-brand-50 p-3 text-center">
          <app-icon name="present" class="mx-auto h-5 w-5 text-brand-600" />
          <p class="text-2xl font-bold text-brand-700">{{ d.event['presents'] || 0 }}</p>
          <p class="text-xs text-brand-600">présents</p>
        </div>
        <div class="rounded-lg bg-amber-50 p-3 text-center">
          <app-icon name="repeat" class="mx-auto h-5 w-5 text-amber-600" />
          <p class="text-2xl font-bold text-amber-700">{{ d.event['reentrees'] || 0 }}</p>
          <p class="text-xs text-amber-600">ré-entrées</p>
        </div>
      </div>

      @if (d.activites.length) {
        <h3 class="mt-6 font-semibold text-slate-800">Par activité</h3>
        <div class="mt-2 overflow-x-auto">
          <table class="w-full min-w-[560px] text-sm">
            <thead>
              <tr class="border-b border-slate-200 text-left text-slate-500">
                <th class="py-2 pr-3">Activité</th>
                <th class="px-2 text-center">Entrées</th>
                <th class="px-2 text-center">Sorties</th>
                <th class="px-2 text-center">Présents</th>
                <th class="px-2 text-center">Ré-entrées</th>
              </tr>
            </thead>
            <tbody>
              @for (a of d.activites; track a.id) {
                <tr class="border-b border-slate-100">
                  <td class="py-2 pr-3">
                    <p class="font-medium text-slate-800">{{ a.titre }}</p>
                    <p class="text-xs text-slate-400">{{ a.dateDebut ? dt(a.dateDebut) : '' }}</p>
                  </td>
                  <td class="px-2 text-center font-semibold text-green-700">{{ a.flux['entrees'] || 0 }}</td>
                  <td class="px-2 text-center font-semibold text-slate-700">{{ a.flux['sorties'] || 0 }}</td>
                  <td class="px-2 text-center font-semibold text-brand-700">{{ a.flux['presents'] || 0 }}</td>
                  <td class="px-2 text-center font-semibold text-amber-700">{{ a.flux['reentrees'] || 0 }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    } @else if (notFound()) {
      <p class="mt-6 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
        Cet événement n'a pas de présence à afficher (introuvable ou pas encore publié).
      </p>
    } @else {
      <p class="mt-6 text-sm text-slate-500">Chargement…</p>
    }
  `,
})
export class PublicAttendanceComponent implements OnDestroy {
  private checkin = inject(CheckinService);

  slug = input.required<string>();
  data = signal<AttendanceView | null>(null);
  notFound = signal(false);
  refreshSeconds = REFRESH_MS / 1000;

  private poll?: Subscription;

  constructor() {
    effect(() => {
      const slug = this.slug();
      this.poll?.unsubscribe();
      this.data.set(null);
      this.notFound.set(false);
      if (!slug) return;
      this.poll = interval(REFRESH_MS)
        .pipe(
          startWith(0),
          switchMap(() => this.checkin.publicAttendance(slug)),
        )
        .subscribe({
          next: (v) => this.data.set(v),
          error: () => this.notFound.set(true),
        });
    });
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
  }

  dt = (iso?: string) => formatDateTime(iso);
}
