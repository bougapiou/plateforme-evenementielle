import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiBase } from '../../core/api';
import { Page, UserSummary } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [FormsModule, StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Utilisateurs</h1>

    <input class="form-input mt-4 max-w-sm" placeholder="Rechercher (nom, e-mail)…"
           [(ngModel)]="search" (ngModelChange)="reload()" />

    <div class="mt-4 card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-slate-50 text-left text-slate-500">
          <tr>
            <th class="px-4 py-2">Nom</th>
            <th class="px-4 py-2">E-mail</th>
            <th class="px-4 py-2">Type</th>
            <th class="px-4 py-2">Rôles</th>
            <th class="px-4 py-2">Statut</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          @for (u of users(); track u.id) {
            <tr>
              <td class="px-4 py-2 font-medium text-slate-700">{{ u.fullName }}</td>
              <td class="px-4 py-2 text-slate-500">{{ u.email }}</td>
              <td class="px-4 py-2 text-slate-500">{{ u.type }}</td>
              <td class="px-4 py-2 text-slate-500">{{ u.roles.join(', ') }}</td>
              <td class="px-4 py-2"><app-status-badge [value]="u.status" /></td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="px-4 py-6 text-center text-slate-400">Aucun utilisateur.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class AdminUsersComponent extends ApiBase {
  users = signal<UserSummary[]>([]);
  search = '';

  constructor() {
    super();
    this.reload();
  }

  reload(): void {
    this.get<Page<UserSummary>>('/users', { search: this.search, size: 30 }).subscribe((p) =>
      this.users.set(p.content),
    );
  }
}
