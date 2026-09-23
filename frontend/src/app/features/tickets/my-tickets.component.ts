import { Component, OnDestroy, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { TicketsService } from './tickets.service';
import { PaymentsService } from '../payments/payments.service';
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
        <div class="card overflow-hidden p-0">
          @if (t.eventCoverUrl) {
            <img [src]="t.eventCoverUrl" alt="" class="h-28 w-full object-cover" />
          }
          <div class="p-4">
          <div class="flex items-start justify-between">
            <div>
              <p class="font-semibold text-slate-800">{{ t.eventNom }}</p>
              <p class="text-sm text-slate-500">{{ t.categorieNom }} · {{ date(t.eventDateDebut) }}</p>
              <p class="mt-1 font-mono text-xs text-slate-400">N° {{ t.numero }}</p>
            </div>
            <app-status-badge [value]="t.statut" />
          </div>
          <!-- Le QR reste toujours visible, quelle que soit la présence d'une image de couverture. -->
          <div class="mt-3 flex items-center gap-4">
            @if (qr()[t.id]; as src) {
              <img [src]="src" alt="QR code" class="h-28 w-28 rounded border border-slate-200" />
            } @else {
              <div class="grid h-28 w-28 place-items-center rounded border border-dashed border-slate-300 text-xs text-slate-300">
                QR…
              </div>
            }
            <button class="btn-ghost border border-slate-300" (click)="downloadPdf(t)">
              Télécharger le PDF
            </button>
          </div>
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
            <p class="font-medium text-slate-700">
              {{ o.eventNom }} — {{ o.montantTotal > 0 ? o.montantFormatte : 'Gratuit' }}
            </p>
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
export class MyTicketsComponent implements OnDestroy {
  private service = inject(TicketsService);
  private paymentsService = inject(PaymentsService);
  private sanitizer = inject(DomSanitizer);

  tickets = signal<MyTicket[]>([]);
  orders = signal<TicketOrder[]>([]);
  qr = signal<Record<string, SafeUrl>>({});
  private objectUrls: string[] = [];

  date = (iso?: string) => formatDate(iso);
  dt = (iso?: string) => formatDateTime(iso);

  constructor() {
    this.reload();
  }

  ngOnDestroy(): void {
    this.objectUrls.forEach((u) => URL.revokeObjectURL(u));
  }

  reload(): void {
    this.service.myTickets().subscribe((t) => {
      this.tickets.set(t);
      t.forEach((ticket) =>
        this.service.qrBlob(ticket.id).subscribe((blob) => {
          const url = URL.createObjectURL(blob);
          this.objectUrls.push(url);
          this.qr.set({ ...this.qr(), [ticket.id]: this.sanitizer.bypassSecurityTrustUrl(url) });
        }),
      );
    });
    this.service.myOrders().subscribe((p) => this.orders.set(p.content));
  }

  downloadPdf(t: MyTicket): void {
    this.service.pdfBlob(t.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `billet-${t.numero}.pdf`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    });
  }

  pay(o: TicketOrder): void {
    this.paymentsService.payOrRedirect('TICKET_ORDER', o.id).subscribe(() => this.reload());
  }
  cancel(o: TicketOrder): void {
    this.service.cancelOrder(o.id).subscribe(() => this.reload());
  }
}
