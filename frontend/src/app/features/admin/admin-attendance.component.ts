import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ActivityFlow, AttendanceView, CheckinService } from '../checkin/checkin.service';
import { EventSummary } from '../events/event.models';
import { PresencePanelComponent } from '../../shared/presence-panel.component';
import { pollAttendance } from '../../shared/presence-poll';

const REFRESH_MS = 2000;

@Component({
  selector: 'app-admin-attendance',
  standalone: true,
  imports: [FormsModule, RouterLink, PresencePanelComponent],
  template: `
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h1 class="text-xl font-bold text-slate-800">Présence / Flux — temps réel</h1>
      <a routerLink="/tableau-de-bord/presence/billets" class="btn-ghost text-brand-700">
        Détail par billet →
      </a>
    </div>

    <div class="mt-4 card p-4">
      <label class="form-label">Événement</label>
      <select class="form-input max-w-md" [(ngModel)]="eventId" (ngModelChange)="onEventChange()">
        <option value="">— Choisir —</option>
        @for (e of events(); track e.id) { <option [value]="e.id">{{ e.nom }}</option> }
      </select>
      @if (loaded() && events().length === 0) {
        <p class="mt-2 text-xs text-slate-400">Aucun événement à superviser.</p>
      }
      <p class="mt-2 text-xs text-slate-400">
        Cette page suit les mêmes personnes que le contrôle à l'entrée : vous, et
        le « Personnel de contrôle » ajouté sur l'onglet Contrôle de l'événement.
      </p>
    </div>

    @if (eventId) {
      <div class="card mt-4 p-4 sm:p-5">
        <app-presence-panel [data]="data()" [refreshSeconds]="refreshSeconds" [activityLink]="kioskHref" />
      </div>
    }
  `,
})
export class AdminAttendanceComponent implements OnDestroy {
  private checkin = inject(CheckinService);

  events = signal<EventSummary[]>([]);
  loaded = signal(false);
  data = signal<AttendanceView | null>(null);
  eventId = '';
  refreshSeconds = REFRESH_MS / 1000;

  private poll?: Subscription;

  constructor() {
    this.checkin.controllableEvents().subscribe({
      next: (list) => {
        this.events.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
  }

  onEventChange(): void {
    this.poll?.unsubscribe();
    this.data.set(null);
    if (!this.eventId) return;
    const id = this.eventId;
    this.poll = pollAttendance(
      () => this.checkin.attendance(id),
      REFRESH_MS,
      (v) => this.data.set(v),
      () => this.data.set(null),
    );
  }

  /** Link to the chrome-free full-screen board of one activity, for a monitor of its own. */
  kioskHref = (activity: ActivityFlow): string => {
    const params = new URLSearchParams({ nom: this.data()?.eventNom ?? '', activityId: activity.id });
    return `/presence/${this.eventId}?${params.toString()}`;
  };
}
