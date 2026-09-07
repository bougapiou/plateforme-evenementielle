import { Component, inject, signal } from '@angular/core';
import { OrganizersService } from '../organizer/organizers.service';
import { Organizer } from '../organizer/organizer.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

@Component({
  selector: 'app-admin-organizers',
  standalone: true,
  imports: [StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Organisateurs</h1>

    <div class="mt-4 card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-slate-50 text-left text-slate-500">
          <tr>
            <th class="px-4 py-2">Organisateur</th>
            <th class="px-4 py-2">Compte</th>
            <th class="px-4 py-2">Structure</th>
            <th class="px-4 py-2">Statut</th>
            <th class="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          @for (o of organizers(); track o.id) {
            <tr>
              <td class="px-4 py-2 font-medium text-slate-700">{{ o.nomAffichage }}</td>
              <td class="px-4 py-2 text-slate-500">{{ o.userEmail }}</td>
              <td class="px-4 py-2 text-slate-500">{{ o.structureName || '—' }}</td>
              <td class="px-4 py-2"><app-status-badge [value]="o.statut" /></td>
              <td class="px-4 py-2 text-right">
                @if (o.statut !== 'ACTIF') {
                  <button class="btn-ghost text-green-700" (click)="approve(o)">Approuver</button>
                }
                @if (o.statut === 'ACTIF') {
                  <button class="btn-ghost text-red-700" (click)="suspend(o)">Suspendre</button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="px-4 py-6 text-center text-slate-400">Aucun organisateur.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class AdminOrganizersComponent {
  private service = inject(OrganizersService);
  organizers = signal<Organizer[]>([]);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.service.adminList({}).subscribe((p) => this.organizers.set(p.content));
  }

  approve(o: Organizer): void {
    this.service.approve(o.id).subscribe(() => this.reload());
  }

  suspend(o: Organizer): void {
    this.service.suspend(o.id).subscribe(() => this.reload());
  }
}
