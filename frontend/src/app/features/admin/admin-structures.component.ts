import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StructuresService } from '../structures/structures.service';
import { StructureSummary } from '../structures/structure.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

@Component({
  selector: 'app-admin-structures',
  standalone: true,
  imports: [FormsModule, StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Structures</h1>

    <input class="form-input mt-4 max-w-sm" placeholder="Rechercher (raison sociale, RCCM, IFU)…"
           [(ngModel)]="search" (ngModelChange)="reload()" />

    <div class="mt-4 card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-slate-50 text-left text-slate-500">
          <tr>
            <th class="px-4 py-2">Raison sociale</th>
            <th class="px-4 py-2">Type</th>
            <th class="px-4 py-2">Ville</th>
            <th class="px-4 py-2">Statut</th>
            <th class="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          @for (s of structures(); track s.id) {
            <tr>
              <td class="px-4 py-2 font-medium text-slate-700">{{ s.raisonSociale }}</td>
              <td class="px-4 py-2 text-slate-500">{{ s.typeStructure }}</td>
              <td class="px-4 py-2 text-slate-500">{{ s.ville || '—' }}</td>
              <td class="px-4 py-2"><app-status-badge [value]="s.statut" /></td>
              <td class="px-4 py-2 text-right">
                @if (s.statut !== 'VERIFIEE') {
                  <button class="btn-ghost text-green-700" (click)="setStatus(s, 'VERIFIEE')">Vérifier</button>
                }
                @if (s.statut !== 'SUSPENDUE') {
                  <button class="btn-ghost text-red-700" (click)="setStatus(s, 'SUSPENDUE')">Suspendre</button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="px-4 py-6 text-center text-slate-400">Aucune structure.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class AdminStructuresComponent {
  private service = inject(StructuresService);
  structures = signal<StructureSummary[]>([]);
  search = '';

  constructor() {
    this.reload();
  }

  reload(): void {
    this.service.adminList({ search: this.search }).subscribe((p) => this.structures.set(p.content));
  }

  setStatus(s: StructureSummary, statut: 'VERIFIEE' | 'SUSPENDUE'): void {
    this.service.setStatus(s.id, statut).subscribe(() => this.reload());
  }
}
