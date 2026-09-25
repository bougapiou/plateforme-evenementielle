import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AttendanceView, CheckinService } from '../checkin/checkin.service';
import { PresencePanelComponent } from '../../shared/presence-panel.component';
import { PresenceSource, isPresenceSource } from '../../shared/presence';
import { pollAttendance } from '../../shared/presence-poll';

const REFRESH_MS = 5000;

/**
 * Présence en direct pour un événement, sans connexion. Accessible uniquement si l'événement est déjà
 * visible sur le site public (voir EventStatus.isPubliclyVisible côté backend) — un brouillon reste privé.
 * La source (billets QR, capteurs, combiné) se choisit sur la page ; le bouton « Plein écran » agrandit
 * les compteurs sur tout l'écran sans quitter la page.
 */
@Component({
  selector: 'app-public-attendance',
  standalone: true,
  imports: [RouterLink, PresencePanelComponent],
  template: `
    <a routerLink="/presence-en-direct" class="text-sm text-brand-700 hover:underline">
      ← Autres événements en direct
    </a>
    <h1 class="mt-1 text-xl font-bold text-slate-800">Présence en direct</h1>

    <div class="card mt-4 p-4 sm:p-5">
      <app-presence-panel [data]="data()" [initialSource]="initialSource" [refreshSeconds]="refreshSeconds"
                          [message]="notFound() ? notFoundMessage : null" />
    </div>

    @if (data()) {
      <p class="mt-3 text-sm">
        <a [routerLink]="['/evenements', slug()]" class="text-brand-700 hover:underline">
          Voir la page de l'événement →
        </a>
      </p>
    }
  `,
})
export class PublicAttendanceComponent implements OnDestroy {
  private checkin = inject(CheckinService);

  slug = input.required<string>();
  data = signal<AttendanceView | null>(null);
  notFound = signal(false);
  refreshSeconds = REFRESH_MS / 1000;
  notFoundMessage = "Cet événement n'a pas de présence à afficher (introuvable ou pas encore publié).";

  initialSource: PresenceSource | null = (() => {
    const v = inject(ActivatedRoute).snapshot.queryParamMap.get('source');
    return isPresenceSource(v) ? v : null;
  })();

  private poll?: Subscription;

  constructor() {
    effect(() => {
      const slug = this.slug();
      this.poll?.unsubscribe();
      this.data.set(null);
      this.notFound.set(false);
      if (!slug) return;
      this.poll = pollAttendance(
        () => this.checkin.publicAttendance(slug),
        REFRESH_MS,
        (v) => this.data.set(v),
        () => this.notFound.set(true),
      );
    });
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
  }
}
