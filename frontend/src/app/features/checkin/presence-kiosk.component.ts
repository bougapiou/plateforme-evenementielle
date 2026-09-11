import { Component, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { CheckinService } from './checkin.service';
import { IconComponent } from '../../shared/icon.component';
import { formatTime } from '../../shared/format';

const REFRESH_MS = 6000;

/**
 * Full-screen, chrome-free display of the four live flow counters — meant for
 * a monitor at the venue entrance. No navbar, no sidebar: this route sits
 * outside the dashboard layout on purpose.
 */
@Component({
  selector: 'app-presence-kiosk',
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
          @if (activiteNom()) {
            <p class="mt-1 text-slate-400">{{ activiteNom() }}</p>
          }
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
    </div>
  `,
})
export class PresenceKioskComponent implements OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private checkin = inject(CheckinService);

  private eventId = this.route.snapshot.paramMap.get('eventId') ?? '';
  private activityId = this.route.snapshot.queryParamMap.get('activityId') ?? undefined;

  eventNom = signal(this.route.snapshot.queryParamMap.get('nom') ?? 'Événement');
  activiteNom = signal(this.route.snapshot.queryParamMap.get('activiteNom'));
  stats = signal<Record<string, number>>({});
  updatedAt = signal('');

  private poll: Subscription;

  constructor() {
    this.poll = interval(REFRESH_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.checkin.stats(this.eventId, this.activityId)),
      )
      .subscribe({
        next: (s) => {
          this.stats.set(s);
          this.updatedAt.set(formatTime(new Date().toISOString()));
        },
        error: () => {},
      });
  }

  ngOnDestroy(): void {
    this.poll.unsubscribe();
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
    this.router.navigateByUrl('/tableau-de-bord/presence');
  }
}
