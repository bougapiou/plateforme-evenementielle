import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/icon.component';
import { StandsService } from './stands.service';
import { StandReservation } from './stand.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { formatDateTime } from '../../shared/format';
import { PaymentsService } from '../payments/payments.service';
import { downloadBlob } from '../invoices/invoices.service';

@Component({
  selector: 'app-my-stands',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent, IconComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Mes réservations de stands</h1>

    <div class="mt-4 space-y-2">
      @for (r of reservations(); track r.id) {
        <div class="card flex items-center justify-between p-4 text-sm">
          <div>
            <p class="font-medium text-slate-700">
              {{ r.eventNom }} — {{ r.standTypeNom }} · Stand {{ r.standNumero }}
            </p>
            <p class="text-slate-400">
              {{ r.numeroReservation }} · {{ r.montantFormatte }}
              @if (r.statut === 'RESERVE_TEMP') { · à payer avant {{ dt(r.dateLimitePaiement) }} }
            </p>
          </div>
          <div class="flex items-center gap-2">
            <app-status-badge [value]="r.statut" />
            @if (r.statut === 'RESERVE_TEMP' || r.statut === 'ATTENTE_PAIEMENT') {
              <button class="btn-ghost text-green-700" (click)="pay(r)">Payer</button>
              <button class="btn-ghost text-red-700" (click)="cancel(r)">Annuler</button>
            }
            @if (r.statut === 'CONFIRME') {
              <button class="btn-ghost text-brand-700" (click)="confirmation(r)">Confirmation PDF</button>
            }
          </div>
        </div>
      } @empty {
        <div class="card p-6 text-center">
          <p class="text-slate-600">Vous n'avez encore aucune réservation de stand.</p>
          <a routerLink="/" class="btn-primary mt-4 inline-flex items-center gap-2">
            <app-icon name="calendar" class="h-4 w-4" /> Découvrir les événements
            <app-icon name="arrow-right" class="h-4 w-4" />
          </a>
        </div>
      }
    </div>
  `,
})
export class MyStandsComponent {
  private service = inject(StandsService);
  private paymentsService = inject(PaymentsService);
  reservations = signal<StandReservation[]>([]);
  dt = (iso?: string) => formatDateTime(iso);

  constructor() {
    this.reload();
  }
  reload(): void {
    this.service.myReservations().subscribe((p) => this.reservations.set(p.content));
  }
  pay(r: StandReservation): void {
    this.paymentsService.payOrRedirect('STAND_RESERVATION', r.id).subscribe(() => this.reload());
  }
  cancel(r: StandReservation): void {
    this.service.cancel(r.id).subscribe(() => this.reload());
  }
  confirmation(r: StandReservation): void {
    this.service.confirmationPdf(r.id).subscribe((b) =>
      downloadBlob(b, `reservation-${r.numeroReservation}.pdf`),
    );
  }
}
