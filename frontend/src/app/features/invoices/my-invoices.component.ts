import { Component, inject, signal } from '@angular/core';
import { Invoice, InvoicesService, downloadBlob } from './invoices.service';
import { formatDate } from '../../shared/format';

@Component({
  selector: 'app-my-invoices',
  standalone: true,
  template: `
    <h1 class="text-xl font-bold text-slate-800">Mes factures &amp; reçus</h1>

    <div class="mt-4 card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-slate-50 text-left text-slate-500">
          <tr>
            <th class="px-4 py-2">Numéro</th>
            <th class="px-4 py-2">Type</th>
            <th class="px-4 py-2">Objet</th>
            <th class="px-4 py-2 text-right">Montant</th>
            <th class="px-4 py-2">Date</th>
            <th class="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          @for (i of invoices(); track i.id) {
            <tr>
              <td class="px-4 py-2 font-mono text-xs">{{ i.numero }}</td>
              <td class="px-4 py-2">
                <span class="badge" [class.bg-blue-100]="i.type === 'FACTURE'"
                      [class.text-blue-800]="i.type === 'FACTURE'"
                      [class.bg-green-100]="i.type === 'RECU'"
                      [class.text-green-800]="i.type === 'RECU'">
                  {{ i.type === 'FACTURE' ? 'Facture' : 'Reçu' }}
                </span>
              </td>
              <td class="px-4 py-2 text-slate-500">{{ i.lignes }}</td>
              <td class="px-4 py-2 text-right font-semibold">{{ i.montantFormatte }}</td>
              <td class="px-4 py-2 text-slate-400">{{ date(i.emiseLe) }}</td>
              <td class="px-4 py-2 text-right">
                <button class="btn-ghost text-brand-700" (click)="dl(i)">PDF</button>
              </td>
            </tr>
          } @empty {
            <tr><td colspan="6" class="px-4 py-6 text-center text-slate-400">Aucun document.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class MyInvoicesComponent {
  private service = inject(InvoicesService);
  invoices = signal<Invoice[]>([]);
  date = (iso?: string) => formatDate(iso);

  constructor() {
    this.service.mine().subscribe((i) => this.invoices.set(i));
  }

  dl(i: Invoice): void {
    this.service.pdfBlob(i.id).subscribe((b) => downloadBlob(b, `${i.numero}.pdf`));
  }
}
