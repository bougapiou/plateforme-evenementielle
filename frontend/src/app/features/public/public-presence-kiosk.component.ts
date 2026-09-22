import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { CheckinService } from '../checkin/checkin.service';
import { IconComponent } from '../../shared/icon.component';
import { formatTime } from '../../shared/format';

const REFRESH_MS = 8000;

/**
 * Version publique (sans connexion) de l'écran plein format du personnel de
 * contrôle (`checkin/presence-kiosk.component.ts`) — même présentation, mais
 * par slug et via l'API publique. Hors des deux layouts (public/tableau de
 * bord) exprès : aucune navbar, aucun pied de page.
 */
@Component({
  selector: 'app-public-presence-kiosk',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="flex min-h-screen flex-col bg-slate-950 p-6 text-white sm:p-12">
      <div class="flex items-start justify-between gap-4">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
            Présence en direct
          </p>
          <h1 class="mt-1 text-2xl font-bold sm:text-4xl">{{ eventNom() }}</h1>
        </div>
        <div class="flex items-center gap-2">
          @if (updatedAt()) {
            <span class="hidden text-xs text-slate-500 sm:inline">Mis à jour {{ updatedAt() }}</span>
          }
          <button type="button" title="Plein écran"
                  class="rounded-lg border border-slate-700 p-2 text-slate-300 hover:text-white"
                  (click)="toggleFullscreen()">
            <app-icon name="expand" class="h-5 w-5" />
          </button>
          <button type="button" title="Fermer"
                  class="rounded-lg border border-slate-700 p-2 text-slate-300 hover:text-white"
                  (click)="close()">
            <app-icon name="x" class="h-5 w-5" />
          </button>
        </div>
      </div>

      @if (notFound()) {
        <p class="mt-8 text-slate-400">
          Cet événement n'a pas de présence à afficher (introuvable ou pas encore publié).
        </p>
      } @else {
        <div class="mt-8 grid flex-1 grid-cols-2 gap-4 sm:mt-14 sm:gap-6">
          <div class="flex flex-col items-center justify-center rounded-3xl border border-emerald-800/50 bg-emerald-950/40 p-6">
            <app-icon name="login" class="h-8 w-8 text-emerald-400 sm:h-10 sm:w-10" />
            <p class="mt-3 text-5xl font-black tabular-nums text-emerald-300 sm:mt-4 sm:text-8xl">
              {{ stats()['entrees'] || 0 }}
            </p>
            <p class="mt-2 text-xs font-semibold uppercase tracking-widest text-emerald-500 sm:mt-3 sm:text-base">
              Entrées
            </p>
          </div>
          <div class="flex flex-col items-center justify-center rounded-3xl border border-slate-700 bg-slate-900/60 p-6">
            <app-icon name="logout" class="h-8 w-8 text-slate-300 sm:h-10 sm:w-10" />
            <p class="mt-3 text-5xl font-black tabular-nums text-slate-100 sm:mt-4 sm:text-8xl">
              {{ stats()['sorties'] || 0 }}
            </p>
            <p class="mt-2 text-xs font-semibold uppercase tracking-widest text-slate-400 sm:mt-3 sm:text-base">
              Sorties
            </p>
          </div>
          <div class="flex flex-col items-center justify-center rounded-3xl border border-sky-800/50 bg-sky-950/40 p-6">
            <app-icon name="present" class="h-8 w-8 text-sky-400 sm:h-10 sm:w-10" />
            <p class="mt-3 text-5xl font-black tabular-nums text-sky-300 sm:mt-4 sm:text-8xl">
              {{ stats()['presents'] || 0 }}
            </p>
            <p class="mt-2 text-xs font-semibold uppercase tracking-widest text-sky-500 sm:mt-3 sm:text-base">
              Présents
            </p>
          </div>
          <div class="flex flex-col items-center justify-center rounded-3xl border border-amber-800/50 bg-amber-950/40 p-6">
            <app-icon name="repeat" class="h-8 w-8 text-amber-400 sm:h-10 sm:w-10" />
            <p class="mt-3 text-5xl font-black tabular-nums text-amber-300 sm:mt-4 sm:text-8xl">
              {{ stats()['reentrees'] || 0 }}
            </p>
            <p class="mt-2 text-xs font-semibold uppercase tracking-widest text-amber-500 sm:mt-3 sm:text-base">
              Ré-entrées
            </p>
          </div>
        </div>
      }
    </div>
  `,
})
export class PublicPresenceKioskComponent implements OnDestroy {
  private router = inject(Router);
  private checkin = inject(CheckinService);

  slug = input.required<string>();

  eventNom = signal('Événement');
  stats = signal<Record<string, number>>({});
  updatedAt = signal('');
  notFound = signal(false);

  private poll?: Subscription;

  constructor() {
    effect(() => {
      const slug = this.slug();
      this.poll?.unsubscribe();
      this.notFound.set(false);
      if (!slug) return;
      this.poll = interval(REFRESH_MS)
        .pipe(
          startWith(0),
          switchMap(() => this.checkin.publicAttendance(slug)),
        )
        .subscribe({
          next: (v) => {
            this.eventNom.set(v.eventNom);
            this.stats.set(v.event);
            this.updatedAt.set(formatTime(new Date().toISOString()));
          },
          error: () => this.notFound.set(true),
        });
    });
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
  }

  toggleFullscreen(): void {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen?.().catch(() => {});
    } else {
      document.exitFullscreen?.().catch(() => {});
    }
  }

  close(): void {
    if (document.fullscreenElement) document.exitFullscreen?.().catch(() => {});
    this.router.navigateByUrl(`/evenements/${this.slug()}/flux`);
  }
}
