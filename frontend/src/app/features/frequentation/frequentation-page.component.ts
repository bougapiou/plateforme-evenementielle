import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { IconComponent } from '../../shared/icon.component';
import { FrequentationService } from './frequentation.service';
import { Frequentation } from './frequentation.models';

const REFRESH_MS = 8000;

/**
 * Page publique, pleine page : statistiques globales de fréquentation des stands d'un événement
 * (total, dont identifiés / anonymes), classement cliquable vers le détail par stand. Alimentée
 * par les passages que les visiteurs signalent — aucun compte, aucune billetterie impliquée.
 */
@Component({
  selector: 'app-frequentation-page',
  standalone: true,
  imports: [IconComponent, RouterLink],
  template: `
    <div class="mx-auto max-w-2xl">
      @if (notFound()) {
        <p class="card p-6 text-center text-sm text-slate-500">
          Cet événement n'a pas de fréquentation de stands à afficher.
        </p>
      } @else if (data() === null) {
        <p class="mt-6 text-center text-sm text-slate-500">Chargement…</p>
      } @else {
        @if (data(); as d) {
          <div class="flex items-center justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-widest text-slate-400">
                {{ d.eventNom }}
              </p>
              <h1 class="mt-1 text-xl font-bold text-slate-800">Pointage des visiteurs</h1>
            </div>
            <div class="text-right">
              <p class="text-3xl font-black tabular-nums text-brand-700">{{ d.totalVisites }}</p>
              <p class="text-xs text-slate-500">passages signalés</p>
            </div>
          </div>
          <p class="mt-1 text-sm text-slate-500">
            Dont {{ d.totalIdentifiees }} avec nom renseigné et {{ d.totalAnonymes }} anonyme{{ d.totalAnonymes > 1 ? 's' : '' }}.
            Mise à jour automatique toutes les {{ REFRESH_MS / 1000 }} s.
          </p>
          <a [routerLink]="['/evenements', slug(), 'pointage', 'signaler']"
             class="mt-3 inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
            <app-icon name="check" class="h-4 w-4" /> Signaler les stands que j'ai visités
          </a>

          <div class="mt-6 space-y-2">
            @for (s of d.stands; track s.standId; let i = $index) {
              <a [routerLink]="['/evenements', slug(), 'pointage', 'stands', s.standId]"
                 class="card flex items-center gap-4 p-4 hover:border-brand-300">
                <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-bold"
                      [class]="i === 0 ? 'bg-amber-100 text-amber-700'
                        : i === 1 ? 'bg-slate-200 text-slate-600'
                        : i === 2 ? 'bg-orange-100 text-orange-700'
                        : 'bg-slate-100 text-slate-400'">
                  {{ i + 1 }}
                </span>
                <div class="min-w-0 flex-1">
                  <p class="truncate font-semibold text-slate-800">
                    Stand {{ s.numero }}
                    <span class="font-normal text-slate-500">· {{ s.standTypeNom }}</span>
                  </p>
                  @if (s.exposantNom) {
                    <p class="truncate text-sm text-slate-500">{{ s.exposantNom }}</p>
                  }
                </div>
                <div class="shrink-0 text-right">
                  <p class="text-xl font-bold tabular-nums text-brand-700">{{ s.visites }}</p>
                  <p class="text-xs text-slate-400">passage{{ s.visites > 1 ? 's' : '' }}</p>
                </div>
              </a>
            } @empty {
              <div class="card p-6 text-center">
                <app-icon name="users" class="mx-auto h-8 w-8 text-slate-300" />
                <p class="mt-2 text-sm text-slate-500">Aucun stand pour cet événement.</p>
              </div>
            }
          </div>
        }
      }
    </div>
  `,
})
export class FrequentationPageComponent implements OnDestroy {
  private service = inject(FrequentationService);

  slug = input.required<string>();
  data = signal<Frequentation | null>(null);
  notFound = signal(false);
  REFRESH_MS = REFRESH_MS;

  private poll?: Subscription;

  constructor() {
    effect(() => {
      const slug = this.slug();
      this.poll?.unsubscribe();
      this.data.set(null);
      this.notFound.set(false);
      if (!slug) return;
      this.poll = interval(REFRESH_MS)
        .pipe(startWith(0), switchMap(() => this.service.stats(slug)))
        .subscribe({
          next: (v) => this.data.set(v),
          error: () => this.notFound.set(true),
        });
    });
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
  }
}
