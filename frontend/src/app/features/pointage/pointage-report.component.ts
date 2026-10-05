import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { IconComponent } from '../../shared/icon.component';
import { PointageService } from './pointage.service';
import { PointageStats } from './pointage.models';

const REFRESH_MS = 8000;
const RADIUS = 26;
const CIRC = 2 * Math.PI * RADIUS;

interface StandSlice {
  standId: string;
  nom: string;
  visites: number;
  pourcentage: number;
  couleur: string;
  dasharray: string;
  dashoffset: number;
}

/** Rapport global du module Pointage des visiteurs : répartition en pourcentage, avec graphiques. */
@Component({
  selector: 'app-pointage-report',
  standalone: true,
  imports: [IconComponent, RouterLink],
  template: `
    <div class="mx-auto max-w-5xl">
      <div class="flex flex-wrap items-center gap-4">
        <a routerLink="/pointage-visiteurs/stats"
           class="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
          <app-icon name="arrow-right" class="h-3.5 w-3.5 rotate-180" /> Statistiques
        </a>
        <a routerLink="/pointage-visiteurs"
           class="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
          <app-icon name="check" class="h-4 w-4" /> Pointage des visiteurs
        </a>
      </div>

      @if (notFound()) {
        <p class="mt-3 card p-6 text-center text-sm text-slate-500">
          Impossible de charger le rapport. Réessayez dans un instant.
        </p>
      } @else if (data() === null) {
        <p class="mt-6 text-center text-sm text-slate-500">Chargement…</p>
      } @else {
        @if (data(); as d) {
          <h1 class="mt-2 text-xl font-bold text-slate-800">Rapport global</h1>
          <p class="mt-1 text-sm text-slate-500">
            {{ d.totalVisites }} passage{{ d.totalVisites > 1 ? 's' : '' }} signalé{{ d.totalVisites > 1 ? 's' : '' }} au total.
            Mise à jour automatique toutes les {{ REFRESH_MS / 1000 }} s.
          </p>

          <div class="card mt-4 p-5">
            <h2 class="font-semibold text-slate-800">Identité des visiteurs</h2>
            <div class="mt-3 flex h-4 w-full overflow-hidden rounded-full bg-slate-100">
              <div class="h-full bg-brand-600" [style.width.%]="identifieesPct()"></div>
              <div class="h-full bg-slate-300" [style.width.%]="anonymesPct()"></div>
            </div>
            <div class="mt-3 flex flex-wrap gap-6 text-sm">
              <span class="flex items-center gap-2">
                <span class="h-2.5 w-2.5 rounded-full bg-brand-600"></span>
                Avec nom renseigné : <strong class="tabular-nums">{{ identifieesPct() }}%</strong>
                <span class="text-slate-400">({{ d.totalIdentifiees }})</span>
              </span>
              <span class="flex items-center gap-2">
                <span class="h-2.5 w-2.5 rounded-full bg-slate-300"></span>
                Anonymes : <strong class="tabular-nums">{{ anonymesPct() }}%</strong>
                <span class="text-slate-400">({{ d.totalAnonymes }})</span>
              </span>
            </div>
          </div>

          <div class="card mt-4 p-5">
            <h2 class="font-semibold text-slate-800">Répartition des passages par stand</h2>
            <p class="mt-1 text-xs text-slate-400">
              Touchez une portion ou un stand ci-dessous pour voir ses statistiques détaillées.
            </p>

            <div class="mt-4 flex flex-col items-center gap-6 sm:flex-row sm:items-center sm:justify-center">
              <div class="relative h-56 w-56 shrink-0">
                <svg width="224" height="224" viewBox="0 0 64 64" class="-rotate-90">
                  <circle cx="32" cy="32" r="26" fill="none" stroke-width="10" class="stroke-slate-100" />
                  @for (s of slices(); track s.standId) {
                    <circle cx="32" cy="32" r="26" fill="none" stroke-width="10"
                            [attr.stroke]="s.couleur"
                            [attr.stroke-dasharray]="s.dasharray"
                            [attr.stroke-dashoffset]="s.dashoffset"
                            class="cursor-pointer transition-opacity hover:opacity-80"
                            (click)="voirStand(s.standId)">
                      <title>{{ s.nom }} — {{ s.pourcentage }}%</title>
                    </circle>
                  }
                </svg>
                <div class="absolute inset-0 flex flex-col items-center justify-center">
                  <p class="text-2xl font-black tabular-nums text-slate-800">{{ d.totalVisites }}</p>
                  <p class="text-xs text-slate-400">passages</p>
                </div>
              </div>

              <div class="grid w-full grid-cols-1 gap-1.5 sm:grid-cols-1">
                @for (s of slices(); track s.standId) {
                  <button type="button"
                          class="flex items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors hover:bg-slate-50"
                          (click)="voirStand(s.standId)">
                    <span class="h-3 w-3 shrink-0 rounded-full" [style.background]="s.couleur"></span>
                    <span class="min-w-0 flex-1 truncate text-slate-700">{{ s.nom }}</span>
                    <span class="shrink-0 tabular-nums text-slate-500">{{ s.pourcentage }}%</span>
                  </button>
                } @empty {
                  <p class="text-sm text-slate-400">Aucun stand pour le moment.</p>
                }
              </div>
            </div>
          </div>
        }
      }
    </div>
  `,
})
export class PointageReportComponent implements OnDestroy {
  private service = inject(PointageService);
  private router = inject(Router);

  data = signal<PointageStats | null>(null);
  notFound = signal(false);
  REFRESH_MS = REFRESH_MS;

  identifieesPct = computed(() => this.pct(this.data()?.totalIdentifiees));
  anonymesPct = computed(() => this.pct(this.data()?.totalAnonymes));

  slices = computed<StandSlice[]>(() => {
    const d = this.data();
    if (!d || d.stands.length === 0) return [];
    let cumulative = 0;
    return d.stands.map((s, i) => {
      const pourcentage = this.pct(s.visites);
      const len = (s.visites / Math.max(1, d.totalVisites)) * CIRC;
      const dasharray = `${len} ${CIRC - len}`;
      const dashoffset = -cumulative;
      cumulative += len;
      return {
        standId: s.standId,
        nom: s.nom,
        visites: s.visites,
        pourcentage,
        couleur: `hsl(${Math.round((i * 360) / d.stands.length)}, 65%, 52%)`,
        dasharray,
        dashoffset,
      };
    });
  });

  private poll: Subscription = interval(REFRESH_MS)
    .pipe(startWith(0), switchMap(() => this.service.stats()))
    .subscribe({
      next: (v) => this.data.set(v),
      error: () => this.notFound.set(true),
    });

  ngOnDestroy(): void {
    this.poll.unsubscribe();
  }

  voirStand(standId: string): void {
    this.router.navigate(['/pointage-visiteurs', 'stands', standId]);
  }

  private pct(n: number | undefined): number {
    const total = this.data()?.totalVisites ?? 0;
    if (!n || total === 0) return 0;
    return Math.round((n / total) * 100);
  }
}
