import { Component, OnDestroy, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { IconComponent } from '../../shared/icon.component';
import { PointageService } from './pointage.service';
import { PointageStats } from './pointage.models';

const REFRESH_MS = 8000;

/** Page publique, pleine page : statistiques globales du module Pointage des visiteurs. */
@Component({
  selector: 'app-pointage-stats',
  standalone: true,
  imports: [IconComponent, RouterLink],
  template: `
    <div class="mx-auto max-w-5xl">
      @if (notFound()) {
        <p class="card p-6 text-center text-sm text-slate-500">
          Impossible de charger les statistiques. Réessayez dans un instant.
        </p>
      } @else if (data() === null) {
        <p class="mt-6 text-center text-sm text-slate-500">Chargement…</p>
      } @else {
        @if (data(); as d) {
          <a routerLink="/pointage-visiteurs"
             class="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
            <app-icon name="arrow-right" class="h-3.5 w-3.5 rotate-180" /> Retour au pointage
          </a>
          <div class="mt-2 flex items-center justify-between">
            <h1 class="text-xl font-bold text-slate-800">Pointage des visiteurs</h1>
            <div class="text-right">
              <p class="text-3xl font-black tabular-nums text-brand-700">{{ d.totalVisites }}</p>
              <p class="text-xs text-slate-500">passages signalés</p>
            </div>
          </div>
          <p class="mt-1 text-sm text-slate-500">
            Dont {{ d.totalIdentifiees }} avec nom renseigné et {{ d.totalAnonymes }} anonyme{{ d.totalAnonymes > 1 ? 's' : '' }}.
            Mise à jour automatique toutes les {{ REFRESH_MS / 1000 }} s.
          </p>
          <div class="mt-3 flex flex-wrap items-center gap-4">
            <a routerLink="/pointage-visiteurs"
               class="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
              <app-icon name="check" class="h-4 w-4" /> Signaler les stands que j'ai visités
            </a>
            <a routerLink="/pointage-visiteurs/rapport"
               class="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
              <app-icon name="chart" class="h-4 w-4" /> Rapport global
            </a>
          </div>

          <div class="mt-6 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
            @for (s of d.stands; track s.standId) {
              <a [routerLink]="['/pointage-visiteurs', 'stands', s.standId]"
                 class="card grid h-full grid-cols-2 items-stretch gap-2 p-4 hover:border-brand-300">
                <p class="min-w-0 self-center font-semibold text-slate-800">{{ s.nom }}</p>
                <div class="flex flex-col items-center justify-center text-center">
                  <p class="w-full text-5xl font-black leading-none tabular-nums text-brand-700">{{ s.visites }}</p>
                  <p class="mt-1.5 text-xs text-slate-400">passage{{ s.visites > 1 ? 's' : '' }}</p>
                </div>
              </a>
            } @empty {
              <div class="card p-6 text-center">
                <app-icon name="users" class="mx-auto h-8 w-8 text-slate-300" />
                <p class="mt-2 text-sm text-slate-500">Aucun stand pour le moment.</p>
              </div>
            }
          </div>
        }
      }
    </div>
  `,
})
export class PointageStatsComponent implements OnDestroy {
  private service = inject(PointageService);

  data = signal<PointageStats | null>(null);
  notFound = signal(false);
  REFRESH_MS = REFRESH_MS;

  private poll: Subscription = interval(REFRESH_MS)
    .pipe(startWith(0), switchMap(() => this.service.stats()))
    .subscribe({
      next: (v) => this.data.set(v),
      error: () => this.notFound.set(true),
    });

  ngOnDestroy(): void {
    this.poll.unsubscribe();
  }
}
