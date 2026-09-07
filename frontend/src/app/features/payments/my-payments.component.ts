import { Component, inject, signal } from '@angular/core';
import { Payment, PaymentsService } from './payments.service';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { formatDateTime } from '../../shared/format';

@Component({
  selector: 'app-my-payments',
  standalone: true,
  imports: [StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Mes paiements</h1>

    <div class="mt-4 card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-slate-50 text-left text-slate-500">
          <tr>
            <th class="px-4 py-2">Référence</th>
            <th class="px-4 py-2">Objet</th>
            <th class="px-4 py-2">Moyen</th>
            <th class="px-4 py-2 text-right">Montant</th>
            <th class="px-4 py-2">Statut</th>
            <th class="px-4 py-2">Date</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          @for (p of payments(); track p.id) {
            <tr>
              <td class="px-4 py-2 font-mono text-xs">{{ p.reference }}</td>
              <td class="px-4 py-2 text-slate-500">
                {{ p.targetType === 'TICKET_ORDER' ? 'Billets' : 'Stand' }}
              </td>
              <td class="px-4 py-2 text-slate-500">{{ p.moyen }}</td>
              <td class="px-4 py-2 text-right font-semibold">{{ p.montantFormatte }}</td>
              <td class="px-4 py-2"><app-status-badge [value]="p.statut" /></td>
              <td class="px-4 py-2 text-slate-400">{{ dt(p.paidAt || p.createdAt) }}</td>
            </tr>
          } @empty {
            <tr><td colspan="6" class="px-4 py-6 text-center text-slate-400">Aucun paiement.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class MyPaymentsComponent {
  private service = inject(PaymentsService);
  payments = signal<Payment[]>([]);
  dt = (iso?: string) => formatDateTime(iso);

  constructor() {
    this.service.mine().subscribe((p) => this.payments.set(p.content));
  }
}
