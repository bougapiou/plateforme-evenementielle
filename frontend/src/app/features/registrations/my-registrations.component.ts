import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RegistrationsService } from './registrations.service';
import { Registration } from './registration.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { formatDate } from '../../shared/format';
import { TicketsService } from '../tickets/tickets.service';
import { downloadBlob } from '../invoices/invoices.service';

@Component({
  selector: 'app-my-registrations',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Mes inscriptions</h1>

    <div class="mt-4 space-y-2">
      @for (r of registrations(); track r.id) {
        <div class="card p-4 text-sm">
          <div class="flex items-center justify-between">
            <div>
              <p class="font-semibold text-slate-800">{{ r.eventNom }}</p>
              <p class="text-slate-400">
                {{ r.reference }} · {{ r.type }} · {{ r.nombreParticipants }} participant(s)
                @if (r.confirmeeLe) { · confirmée le {{ date(r.confirmeeLe) }} }
              </p>
              @if (r.motifRefus) { <p class="text-red-700">Refus : {{ r.motifRefus }}</p> }
            </div>
            <div class="flex items-center gap-2">
              <app-status-badge [value]="r.statut" />
              @if (r.ticketOrderStatut === 'EN_ATTENTE') {
                <button class="btn-ghost text-green-700" (click)="pay(r)">Payer</button>
              }
              @if (r.statut === 'CONFIRMEE') {
                <button class="btn-ghost text-brand-700" (click)="confirmation(r)">Confirmation PDF</button>
              }
              @if (r.statut === 'EN_ATTENTE') {
                <button class="btn-ghost text-red-700" (click)="cancel(r)">Annuler</button>
              }
            </div>
          </div>
        </div>
      } @empty {
        <p class="card p-6 text-sm text-slate-500">
          Aucune inscription. Parcourez les <a routerLink="/" class="text-brand-700">événements</a>.
        </p>
      }
    </div>
  `,
})
export class MyRegistrationsComponent {
  private service = inject(RegistrationsService);
  private ticketsService = inject(TicketsService);
  registrations = signal<Registration[]>([]);
  date = (iso?: string) => formatDate(iso);

  constructor() {
    this.reload();
  }
  reload(): void {
    this.service.mine().subscribe((p) => this.registrations.set(p.content));
  }
  pay(r: Registration): void {
    if (r.ticketOrderId) {
      this.ticketsService.paySandbox(r.ticketOrderId).subscribe(() => this.reload());
    }
  }
  cancel(r: Registration): void {
    this.service.cancel(r.id).subscribe(() => this.reload());
  }
  confirmation(r: Registration): void {
    this.service.confirmationPdf(r.id).subscribe((b) => downloadBlob(b, `inscription-${r.reference}.pdf`));
  }
}
