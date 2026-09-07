import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TicketsService } from './tickets.service';
import { MyTicket, TicketOrder } from '../events/event.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { formatDate, formatDateTime } from '../../shared/format';

@Component({
  selector: 'app-my-tickets',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Mes billets</h1>

    <div class="mt-4 grid gap-3 sm:grid-cols-2">
      @for (t of tickets(); track t.id) {
        <div class="card p-4">
          <div class="flex items-start justify-between">
            <div>
              <p class="font-semibold text-slate-800">{{ t.eventNom }}</p>
              <p class="text-sm text-slate-500">{{ t.categorieNom }} · {{ date(t.eventDateDebut) }}</p>
            </div>
            <app-status-badge [value]="t.statut" />
          </div>
          <p class="mt-3 font-mono text-xs text-slate-500">N° {{ t.numero }}</p>
          <div class="mt-3 grid h-24 w-24 place-items-center rounded-lg border border-dashed border-slate-300 text-[10px] text-slate-400">
            QR (module M8)
          </div>
        </div>
      } @empty {
        <p class="card p-6 text-sm text-slate-500 sm:col-span-2">
          Aucun billet. Parcourez les <a routerLink="/" class="text-brand-700">événements</a>.
        </p>
      }
    </div>

    <h2 class="mt-8 text-lg font-semibold text-slate-800">Mes commandes</h2>
    <div class="mt-3 space-y-2">
      @for (o of orders(); track o.id) {
        <div class="card flex items-center justify-between p-4 text-sm">
          <div>
            <p class="font-medium text-slate-700">{{ o.eventNom }} — {{ o.montantFormatte }}</p>
            <p class="text-slate-400">
              {{ o.reference }} · {{ dt(o.createdAt) }}
              @if (o.statut === 'EN_ATTENTE') { · expire {{ dt(o.expireLe) }} }
            </p>
          </div>
          <div class="flex items-center gap-2">
            <app-status-badge [value]="o.statut" />
            @if (o.statut === 'EN_ATTENTE') {
              <button class="btn-ghost text-green-700" (click)="pay(o)">Payer</button>
              <button class="btn-ghost text-red-700" (click)="cancel(o)">Annuler</button>
            }
          </div>
        </div>
      } @empty {
        <p class="text-sm text-slate-400">Aucune commande.</p>
      }
    </div>
  `,
})
export class MyTicketsComponent {
  private service = inject(TicketsService);
  tickets = signal<MyTicket[]>([]);
  orders = signal<TicketOrder[]>([]);

  date = (iso?: string) => formatDate(iso);
  dt = (iso?: string) => formatDateTime(iso);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.service.myTickets().subscribe((t) => this.tickets.set(t));
    this.service.myOrders().subscribe((p) => this.orders.set(p.content));
  }

  pay(o: TicketOrder): void {
    this.service.paySandbox(o.id).subscribe(() => this.reload());
  }
  cancel(o: TicketOrder): void {
    this.service.cancelOrder(o.id).subscribe(() => this.reload());
  }
}
