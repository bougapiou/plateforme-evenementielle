import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { IconComponent } from '../../shared/icon.component';
import { FrequentationService } from './frequentation.service';
import { StandFrequentation } from './frequentation.models';

const REFRESH_MS = 8000;

/** Page publique : statistiques de fréquentation d'un seul stand (total, dont identifiés / anonymes). */
@Component({
  selector: 'app-stand-frequentation',
  standalone: true,
  imports: [IconComponent, RouterLink],
  template: `
    <div class="mx-auto max-w-md">
      @if (notFound()) {
        <div class="card p-6 text-center">
          <app-icon name="x" class="mx-auto h-8 w-8 text-slate-300" />
          <p class="mt-2 font-medium text-slate-700">Stand introuvable</p>
        </div>
      } @else if (data() === null) {
        <p class="mt-6 text-center text-sm text-slate-500">Chargement…</p>
      } @else {
        @if (data(); as s) {
          <a [routerLink]="['/evenements', slug(), 'pointage']"
             class="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
            <app-icon name="arrow-right" class="h-3.5 w-3.5 rotate-180" /> Classement des stands
          </a>

          <div class="card mt-3 p-6 text-center">
            <app-icon name="building" class="mx-auto h-8 w-8 text-brand-600" />
            <h1 class="mt-2 text-xl font-bold text-slate-800">Stand {{ s.numero }}</h1>
            <p class="mt-1 text-sm text-slate-500">
              {{ s.standTypeNom }}{{ s.exposantNom ? ' · ' + s.exposantNom : '' }}
            </p>
            <p class="mt-4 text-4xl font-black tabular-nums text-brand-700">{{ s.visites }}</p>
            <p class="text-xs text-slate-500">passage{{ s.visites > 1 ? 's' : '' }} signalé{{ s.visites > 1 ? 's' : '' }}</p>
          </div>

          <div class="mt-3 grid grid-cols-2 gap-3">
            <div class="card p-4 text-center">
              <p class="text-2xl font-bold tabular-nums text-slate-800">{{ s.visitesIdentifiees }}</p>
              <p class="text-xs text-slate-500">avec nom renseigné</p>
            </div>
            <div class="card p-4 text-center">
              <p class="text-2xl font-bold tabular-nums text-slate-800">{{ s.visitesAnonymes }}</p>
              <p class="text-xs text-slate-500">anonyme{{ s.visitesAnonymes > 1 ? 's' : '' }}</p>
            </div>
          </div>
          <p class="mt-3 text-center text-xs text-slate-400">
            Mise à jour automatique toutes les {{ REFRESH_MS / 1000 }} s.
          </p>
        }
      }
    </div>
  `,
})
export class StandFrequentationComponent implements OnDestroy {
  private service = inject(FrequentationService);

  slug = input.required<string>();
  standId = input.required<string>();

  data = signal<StandFrequentation | null>(null);
  notFound = signal(false);
  REFRESH_MS = REFRESH_MS;

  private poll?: Subscription;

  constructor() {
    effect(() => {
      const slug = this.slug();
      const standId = this.standId();
      this.poll?.unsubscribe();
      this.data.set(null);
      this.notFound.set(false);
      if (!slug || !standId) return;
      this.poll = interval(REFRESH_MS)
        .pipe(startWith(0), switchMap(() => this.service.standStats(slug, standId)))
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
