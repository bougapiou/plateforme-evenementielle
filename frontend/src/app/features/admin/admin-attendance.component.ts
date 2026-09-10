import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { AttendanceView, CheckinService } from '../checkin/checkin.service';
import { EventSummary } from '../events/event.models';
import { IconComponent } from '../../shared/icon.component';
import { formatDateTime } from '../../shared/format';

const REFRESH_MS = 8000;

@Component({
  selector: 'app-admin-attendance',
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-slate-800">Présence / Flux — temps réel</h1>
      @if (data()) {
        <button class="btn-ghost text-brand-700" (click)="refreshNow()">Actualiser</button>
      }
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
    </div>

    @if (data(); as d) {
      <p class="mt-4 text-sm text-slate-500">
        {{ d.controleSortie ? 'Contrôle des sorties actif' : 'Entrées uniquement' }}
        · mise à jour automatique toutes les {{ refreshSeconds }} s
      </p>

      <h2 class="mt-3 font-semibold text-slate-800">{{ d.eventNom }}</h2>
      <div class="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-4">
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
                    <p class="text-xs text-slate-400">
                      {{ a.dateDebut ? dt(a.dateDebut) : '' }}
                      {{ a.acces === 'PAYANT' ? '· payant' : a.acces === 'GRATUIT' ? '· gratuit' : '' }}
                    </p>
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

  dt = (iso?: string) => formatDateTime(iso);

  onEventChange(): void {
    this.poll?.unsubscribe();
    this.data.set(null);
    if (!this.eventId) return;
    const id = this.eventId;
    this.poll = interval(REFRESH_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.checkin.attendance(id)),
      )
      .subscribe({
        next: (v) => this.data.set(v),
        error: () => {},
      });
  }

  refreshNow(): void {
    if (!this.eventId) return;
    this.checkin.attendance(this.eventId).subscribe((v) => this.data.set(v));
  }
}
