import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, forkJoin, interval, startWith, switchMap } from 'rxjs';
import { CheckinService, TicketFlowView } from './checkin.service';
import { Activity, EventSummary } from '../events/event.models';
import { IconComponent } from '../../shared/icon.component';
import { formatDateTime } from '../../shared/format';

const REFRESH_MS = 2000;

/**
 * Complete entry/exit/re-entry statistics, billet par billet, for an event or
 * one of its activities. Same audience as « Présence / Flux » (owner,
 * assigned control staff, admin).
 */
@Component({
  selector: 'app-ticket-flow',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-slate-800">Détail des billets — entrées / sorties</h1>
      <a routerLink="/tableau-de-bord/presence" class="btn-ghost text-brand-700">
        ← Vue d'ensemble
      </a>
    </div>

    <div class="mt-4 card grid gap-3 p-4 sm:grid-cols-3">
      <div>
        <label class="form-label">Événement</label>
        <select class="form-input" [(ngModel)]="eventId" (ngModelChange)="onEventChange()">
          <option value="">— Choisir —</option>
          @for (e of events(); track e.id) { <option [value]="e.id">{{ e.nom }}</option> }
        </select>
      </div>
      @if (eventId && activities().length) {
        <div>
          <label class="form-label">Activité</label>
          <select class="form-input" [(ngModel)]="activityId" (ngModelChange)="onActivityChange()">
            <option value="">Entrée générale (tout l'événement)</option>
            @for (a of activities(); track a.id) { <option [value]="a.id">{{ a.titre }}</option> }
          </select>
        </div>
      }
      @if (eventId) {
        <div>
          <label class="form-label">Rechercher un billet</label>
          <input class="form-input" placeholder="N° de billet ou nom…" [(ngModel)]="search" />
        </div>
      }
    </div>
    @if (loaded() && events().length === 0) {
      <p class="mt-2 text-xs text-slate-400">Aucun événement à superviser.</p>
    }

    @if (eventId) {
      <p class="mt-4 text-sm text-slate-500">
        Actualisation automatique toutes les {{ refreshSeconds }} s
      </p>

      <div class="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div class="rounded-lg bg-green-50 p-3 text-center">
          <app-icon name="login" class="mx-auto h-5 w-5 text-green-600" />
          <p class="text-2xl font-bold tabular-nums text-green-700">{{ stats()['entrees'] || 0 }}</p>
          <p class="text-xs text-green-600">entrées</p>
        </div>
        <div class="rounded-lg bg-slate-100 p-3 text-center">
          <app-icon name="logout" class="mx-auto h-5 w-5 text-slate-600" />
          <p class="text-2xl font-bold tabular-nums text-slate-700">{{ stats()['sorties'] || 0 }}</p>
          <p class="text-xs text-slate-600">sorties</p>
        </div>
        <div class="rounded-lg bg-brand-50 p-3 text-center">
          <app-icon name="present" class="mx-auto h-5 w-5 text-brand-600" />
          <p class="text-2xl font-bold tabular-nums text-brand-700">{{ stats()['presents'] || 0 }}</p>
          <p class="text-xs text-brand-600">présents</p>
        </div>
        <div class="rounded-lg bg-amber-50 p-3 text-center">
          <app-icon name="repeat" class="mx-auto h-5 w-5 text-amber-600" />
          <p class="text-2xl font-bold tabular-nums text-amber-700">{{ stats()['reentrees'] || 0 }}</p>
          <p class="text-xs text-amber-600">ré-entrées</p>
        </div>
      </div>

      <div class="mt-4 overflow-x-auto">
        <table class="w-full min-w-[680px] text-sm">
          <thead>
            <tr class="border-b border-slate-200 text-left text-slate-500">
              <th class="py-2 pr-3">Billet</th>
              <th class="px-2">Catégorie</th>
              <th class="px-2 text-center">Entrées</th>
              <th class="px-2 text-center">Sorties</th>
              <th class="px-2 text-center">Ré-entrées</th>
              <th class="px-2 text-center">État</th>
              <th class="pl-2">Dernier scan</th>
            </tr>
          </thead>
          <tbody>
            @for (t of filtered(); track t.ticketId) {
              <tr class="border-b border-slate-100">
                <td class="py-2 pr-3">
                  <p class="font-medium text-slate-800">{{ t.numero }}</p>
                  @if (t.participantNom) { <p class="text-xs text-slate-400">{{ t.participantNom }}</p> }
                </td>
                <td class="px-2 text-slate-600">{{ t.categorieNom || '—' }}</td>
                <td class="px-2 text-center font-semibold tabular-nums text-green-700">{{ t.entrees }}</td>
                <td class="px-2 text-center font-semibold tabular-nums text-slate-700">{{ t.sorties }}</td>
                <td class="px-2 text-center font-semibold tabular-nums text-amber-700">{{ t.reentrees }}</td>
                <td class="px-2 text-center">
                  @if (t.present) {
                    <span class="badge bg-green-100 text-green-800">Présent</span>
                  } @else {
                    <span class="badge bg-slate-100 text-slate-600">Sorti</span>
                  }
                </td>
                <td class="pl-2 text-xs text-slate-400">{{ dt(t.dernierScan) }}</td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="py-6 text-center text-sm text-slate-400">
                Aucun billet scanné pour l'instant.
              </td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class TicketFlowComponent implements OnDestroy {
  private checkin = inject(CheckinService);

  events = signal<EventSummary[]>([]);
  activities = signal<Activity[]>([]);
  loaded = signal(false);
  eventId = '';
  activityId = '';
  search = '';

  stats = signal<Record<string, number>>({});
  tickets = signal<TicketFlowView[]>([]);
  refreshSeconds = REFRESH_MS / 1000;

  filtered = computed(() => {
    const q = this.search.trim().toLowerCase();
    if (!q) return this.tickets();
    return this.tickets().filter(
      (t) =>
        t.numero.toLowerCase().includes(q) || (t.participantNom ?? '').toLowerCase().includes(q),
    );
  });

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

  dt = (iso?: string) => formatDateTime(iso);

  onEventChange(): void {
    this.activityId = '';
    this.activities.set([]);
    this.stats.set({});
    this.tickets.set([]);
    if (!this.eventId) {
      this.poll?.unsubscribe();
      return;
    }
    this.checkin.eventActivities(this.eventId).subscribe({
      next: (list) => this.activities.set(list),
      error: () => this.activities.set([]),
    });
    this.startPolling();
  }

  onActivityChange(): void {
    this.startPolling();
  }

  private startPolling(): void {
    this.poll?.unsubscribe();
    if (!this.eventId) return;
    const eventId = this.eventId;
    const activityId = this.activityId || undefined;
    this.poll = interval(REFRESH_MS)
      .pipe(
        startWith(0),
        switchMap(() =>
          forkJoin({
            stats: this.checkin.stats(eventId, activityId),
            tickets: this.checkin.ticketDetails(eventId, activityId),
          }),
        ),
      )
      .subscribe({
        next: ({ stats, tickets }) => {
          this.stats.set(stats);
          this.tickets.set(tickets);
        },
        error: () => {},
      });
  }
}
