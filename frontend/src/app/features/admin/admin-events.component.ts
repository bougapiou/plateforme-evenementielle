import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EventsService } from '../events/events.service';
import { EventSummary } from '../events/event.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { formatDateRange } from '../../shared/format';

@Component({
  selector: 'app-admin-events',
  standalone: true,
  imports: [FormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Événements — validation & supervision</h1>

    <div class="mt-4 flex flex-wrap gap-3">
      <input class="form-input max-w-sm" placeholder="Rechercher…"
             [(ngModel)]="search" (ngModelChange)="reload()" />
      <select class="form-input max-w-xs" [(ngModel)]="statut" (ngModelChange)="reload()">
        <option value="">Tous les statuts</option>
        @for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }
      </select>
    </div>

    <div class="mt-4 space-y-2">
      @for (e of events(); track e.id) {
        <div class="card flex items-center justify-between p-4">
          <a [routerLink]="['/tableau-de-bord/evenements', e.id]">
            <p class="font-semibold text-slate-800">{{ e.nom }}</p>
            <p class="text-sm text-slate-500">
              {{ range(e) }} · {{ e.ville || '—' }} · {{ e.organizerNom }}
            </p>
          </a>
          <div class="flex items-center gap-3">
            <app-status-badge [value]="e.statut" />
            @if (e.statut === 'SOUMIS') {
              <button class="btn-ghost text-green-700" (click)="act(e, 'validate')">Valider</button>
              <button class="btn-ghost text-red-700" (click)="reject(e)">Refuser</button>
            }
            @if (e.statut === 'VALIDE') {
              <button class="btn-ghost text-green-700" (click)="act(e, 'publish')">Publier</button>
            }
            @if (e.statut === 'PUBLIE' || e.statut === 'INSCRIPTIONS_FERMEES') {
              <button class="btn-ghost text-green-700" (click)="act(e, 'open-registrations')">
                Ouvrir les inscriptions
              </button>
            }
            @if (e.statut === 'PUBLIE' || e.statut === 'INSCRIPTIONS_OUVERTES') {
              <button class="btn-ghost" (click)="act(e, 'close-registrations')">
                Fermer les inscriptions
              </button>
            }
            @if (canSuspend(e.statut)) {
              <button class="btn-ghost text-amber-700" (click)="act(e, 'suspend')">Suspendre</button>
            }
            @if (e.statut !== 'ANNULE' && e.statut !== 'TERMINE') {
              <button class="btn-ghost text-red-700" (click)="confirmCancel(e)">Annuler</button>
            }
          </div>
        </div>
      } @empty {
        <p class="card p-6 text-center text-sm text-slate-400">Aucun événement.</p>
      }
    </div>
  `,
})
export class AdminEventsComponent {
  private service = inject(EventsService);
  events = signal<EventSummary[]>([]);
  search = '';
  statut = '';
  statuses = ['BROUILLON', 'SOUMIS', 'VALIDE', 'PUBLIE', 'INSCRIPTIONS_OUVERTES',
    'INSCRIPTIONS_FERMEES', 'EN_COURS', 'TERMINE', 'SUSPENDU', 'ANNULE', 'REFUSE'];

  constructor() {
    this.reload();
  }

  range = (e: EventSummary) => formatDateRange(e.dateDebut, e.dateFin);

  reload(): void {
    this.service.adminList({ search: this.search, statut: this.statut }).subscribe((p) =>
      this.events.set(p.content),
    );
  }

  canSuspend = (statut: string): boolean =>
    ['PUBLIE', 'INSCRIPTIONS_OUVERTES', 'INSCRIPTIONS_FERMEES', 'EN_COURS'].includes(statut);

  act(e: EventSummary, action: string): void {
    this.service.transition(e.id, action).subscribe({
      next: () => this.reload(),
      error: (err) => alert(err?.error?.message ?? 'Action impossible.'),
    });
  }

  reject(e: EventSummary): void {
    const motif = prompt('Motif du refus ?');
    if (motif) this.service.transition(e.id, 'reject', { motif }).subscribe(() => this.reload());
  }

  confirmCancel(e: EventSummary): void {
    if (confirm(`Annuler définitivement « ${e.nom} » ?`)) this.act(e, 'cancel');
  }
}
