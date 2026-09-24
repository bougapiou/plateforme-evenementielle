import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiBase } from '../../core/api';
import { AuthService } from '../../core/auth.service';
import { ApiError, Page, UserSummary } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

interface UserDetail {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
}

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [FormsModule, StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Utilisateurs</h1>

    <input class="form-input mt-4 max-w-sm" placeholder="Rechercher (nom, e-mail)…"
           [(ngModel)]="search" (ngModelChange)="reload()" />

    @if (message()) {
      <p class="mt-3 rounded-lg px-3 py-2 text-sm"
         [class]="messageIsError() ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'">
        {{ message() }}
      </p>
    }

    @if (editing(); as f) {
      <form class="card mt-4 grid gap-3 p-4 sm:grid-cols-2" (ngSubmit)="save()">
        <h2 class="font-semibold text-slate-800 sm:col-span-2">Modifier l'utilisateur</h2>
        <div>
          <label class="form-label">Prénom *</label>
          <input class="form-input" [(ngModel)]="f.firstName" name="firstName" required />
        </div>
        <div>
          <label class="form-label">Nom *</label>
          <input class="form-input" [(ngModel)]="f.lastName" name="lastName" required />
        </div>
        <div>
          <label class="form-label">E-mail *</label>
          <input class="form-input" type="email" [(ngModel)]="f.email" name="email" required />
        </div>
        <div>
          <label class="form-label">Téléphone</label>
          <input class="form-input" [(ngModel)]="f.phone" name="phone" />
        </div>
        <div class="flex gap-2 sm:col-span-2">
          <button type="submit" class="btn-primary">Enregistrer</button>
          <button type="button" class="btn-ghost" (click)="editing.set(null)">Annuler</button>
        </div>
      </form>
    }

    <div class="mt-4 card overflow-x-auto">
      <table class="w-full min-w-[720px] text-sm">
        <thead class="bg-slate-50 text-left text-slate-500">
          <tr>
            <th class="px-4 py-2">Nom</th>
            <th class="px-4 py-2">E-mail</th>
            <th class="px-4 py-2">Type</th>
            <th class="px-4 py-2">Rôles</th>
            <th class="px-4 py-2">Statut</th>
            <th class="px-4 py-2"></th>
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
              <td class="whitespace-nowrap px-4 py-2 text-right">
                <button class="text-xs text-brand-700" (click)="edit(u)">Modifier</button>
                @if (u.id !== me()?.id) {
                  <button class="ml-3 text-xs text-amber-700" (click)="toggleStatus(u)">
                    {{ u.status === 'DESACTIVE' ? 'Activer' : 'Suspendre' }}
                  </button>
                  <button class="ml-3 text-xs text-red-600" (click)="remove(u)">Supprimer</button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="6" class="px-4 py-6 text-center text-slate-400">Aucun utilisateur.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class AdminUsersComponent extends ApiBase {
  private auth = inject(AuthService);

  users = signal<UserSummary[]>([]);
  editing = signal<(UserDetail & { phone: string }) | null>(null);
  message = signal<string | null>(null);
  messageIsError = signal(false);
  me = this.auth.user;
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

  edit(u: UserSummary): void {
    this.message.set(null);
    this.get<UserDetail>(`/users/${u.id}`).subscribe((d) =>
      this.editing.set({ ...d, phone: d.phone ?? '' }),
    );
  }

  save(): void {
    const f = this.editing();
    if (!f) return;
    this.http
      .patch(`${this.base}/users/${f.id}`, {
        email: f.email, firstName: f.firstName, lastName: f.lastName, phone: f.phone,
      })
      .subscribe({
        next: () => {
          this.editing.set(null);
          this.notify('Utilisateur mis à jour.', false);
          this.reload();
        },
        error: (err: HttpErrorResponse) => this.notify(this.errorOf(err, 'Modification impossible.'), true),
      });
  }

  toggleStatus(u: UserSummary): void {
    const status = u.status === 'DESACTIVE' ? 'ACTIF' : 'DESACTIVE';
    this.http.patch(`${this.base}/users/${u.id}/status`, { status }).subscribe({
      next: () => {
        this.notify(status === 'ACTIF' ? 'Compte activé.' : 'Compte suspendu.', false);
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.notify(this.errorOf(err, 'Action impossible.'), true),
    });
  }

  remove(u: UserSummary): void {
    if (!confirm(`Supprimer définitivement ${u.fullName} (${u.email}) ?`)) return;
    this.http.delete<void>(`${this.base}/users/${u.id}`).subscribe({
      next: () => {
        this.notify('Utilisateur supprimé.', false);
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.notify(this.errorOf(err, 'Suppression impossible.'), true),
    });
  }

  private notify(text: string, isError: boolean): void {
    this.message.set(text);
    this.messageIsError.set(isError);
  }

  private errorOf(err: HttpErrorResponse, fallback: string): string {
    return (err.error as ApiError | undefined)?.message ?? fallback;
  }
}
