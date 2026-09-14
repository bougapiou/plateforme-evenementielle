import { Component, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { Payment, PaymentsService } from './payments.service';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { formatDateTime } from '../../shared/format';

const POLL_MS = 4000;
const MAX_POLLS = 15; // ~1 min, then the payer can still tap "Revérifier" by hand

@Component({
  selector: 'app-my-payments',
  standalone: true,
  imports: [StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Mes paiements</h1>
    @if (highlighted()) {
      <p class="mt-1 text-sm text-slate-500">
        Retour du paiement {{ highlighted() }} — vérification automatique en cours…
      </p>
    }

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
            <tr [class.bg-amber-50]="p.reference === highlighted()">
              <td class="px-4 py-2 font-mono text-xs">{{ p.reference }}</td>
              <td class="px-4 py-2 text-slate-500">
                {{ p.targetType === 'TICKET_ORDER' ? 'Billets' : 'Stand' }}
              </td>
              <td class="px-4 py-2 text-slate-500">{{ p.moyen }}</td>
              <td class="px-4 py-2 text-right font-semibold">{{ p.montantFormatte }}</td>
              <td class="px-4 py-2">
                <div class="flex items-center gap-2">
                  <app-status-badge [value]="p.statut" />
                  @if (p.statut === 'EN_ATTENTE') {
                    <button class="text-xs font-medium text-brand-700 hover:underline"
                            (click)="recheck(p)">
                      Revérifier
                    </button>
                  }
                </div>
              </td>
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
export class MyPaymentsComponent implements OnDestroy {
  private service = inject(PaymentsService);
  private route = inject(ActivatedRoute);

  payments = signal<Payment[]>([]);
  highlighted = signal<string | null>(null);
  dt = (iso?: string) => formatDateTime(iso);

  private poll?: Subscription;
  private pollCount = 0;

  constructor() {
    this.highlighted.set(this.route.snapshot.queryParamMap.get('reference'));
    this.reload();
    if (this.highlighted()) {
      this.startPolling();
    }
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
  }

  reload(): void {
    this.service.mine().subscribe((p) => this.payments.set(p.content));
  }

  recheck(p: Payment): void {
    this.service.recheck(p.reference).subscribe(() => this.reload());
  }

  /** Right after a redirect back from a real gateway, chase the still-pending payment a while. */
  private startPolling(): void {
    this.poll = interval(POLL_MS).subscribe(() => {
      this.pollCount++;
      const target = this.payments().find((p) => p.reference === this.highlighted());
      if (this.pollCount > MAX_POLLS || (target && target.statut !== 'EN_ATTENTE')) {
        this.poll?.unsubscribe();
        return;
      }
      if (target) {
        this.service.recheck(target.reference).subscribe(() => this.reload());
      } else {
        this.reload();
      }
    });
  }
}
