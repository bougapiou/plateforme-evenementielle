import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ApiBase } from '../../core/api';
import { ApiError } from '../../core/models';
import { IconComponent } from '../../shared/icon.component';
import { PERMISSION_GROUPS, RoleInfo, permissionLabel, roleChip, roleLabel } from './rbac';

/**
 * Rôles et autorisations : les rôles système (verrouillés) et les rôles personnalisés,
 * que l'admin peut créer, modifier (description, autorisations) et supprimer s'ils sont inutilisés.
 */
@Component({
  selector: 'app-admin-roles',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <a routerLink="/tableau-de-bord/admin/utilisateurs" class="inline-flex items-center gap-1 text-sm text-vert-700 hover:underline">
      <app-icon name="arrow-left" class="h-4 w-4" aria-hidden="true" /> Utilisateurs
    </a>
    <div class="mt-2 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-[1.625rem] text-neutre-900">Rôles et autorisations</h1>
        <p class="mt-1 max-w-2xl text-sm text-neutre-600">
          Un rôle regroupe des autorisations. Les rôles système sont fixes ; créez des rôles personnalisés
          pour des besoins précis (ex. « Agent d’accueil » qui ne fait que scanner les billets).
        </p>
      </div>
      <button type="button" class="btn-primary" (click)="nouveau()"><span class="text-lg leading-none">+</span> Nouveau rôle</button>
    </div>

    @if (message()) {
      <p class="mt-4 rounded-lg px-4 py-3 text-sm" role="status"
         [class]="erreur() ? 'bg-rouge-50 text-rouge-700' : 'bg-vert-50 text-vert-800'">{{ message() }}</p>
    }

    <div class="mt-6 grid gap-4 lg:grid-cols-2">
      @for (r of roles(); track r.id) {
        <article class="flex flex-col rounded-xl border border-neutre-200 bg-white p-5">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <span class="badge" [class]="roleChip(r.name)">{{ roleLabel(r.name) }}</span>
                @if (r.systemRole) {
                  <span class="inline-flex items-center gap-1 text-xs text-neutre-500"><app-icon name="key" class="h-3.5 w-3.5" /> système</span>
                } @else {
                  <span class="text-xs text-neutre-500">personnalisé</span>
                }
              </div>
              <p class="mt-1 font-mono text-xs text-neutre-500">{{ r.name }}</p>
              @if (r.description) { <p class="mt-2 text-sm text-neutre-700">{{ r.description }}</p> }
            </div>
            <a [routerLink]="'/tableau-de-bord/admin/utilisateurs'" class="shrink-0 text-right text-sm text-neutre-600">
              <span class="block font-display text-2xl font-extrabold tabular-nums text-neutre-900">{{ r.utilisateurs }}</span>
              utilisateur{{ r.utilisateurs > 1 ? 's' : '' }}
            </a>
          </div>
          <details class="mt-4 rounded-lg bg-neutre-25 px-3 py-2">
            <summary class="cursor-pointer text-sm font-medium text-neutre-800">{{ r.permissions.length }} autorisation{{ r.permissions.length > 1 ? 's' : '' }}</summary>
            <ul class="mt-2 space-y-1 pb-1 text-xs text-neutre-700">
              @for (p of r.permissions; track p) {
                <li class="flex items-center gap-1.5"><app-icon name="check" class="h-3.5 w-3.5 text-vert-600" aria-hidden="true" /> {{ permissionLabel(p) }}</li>
              }
            </ul>
          </details>
          @if (!r.systemRole) {
            <div class="mt-4 flex gap-2">
              <button type="button" class="btn-secondary min-h-[36px] py-1 text-sm" (click)="modifier(r)">Modifier</button>
              <button type="button" class="btn-ghost min-h-[36px] py-1 text-sm text-rouge-700" [disabled]="r.utilisateurs > 0"
                      [title]="r.utilisateurs > 0 ? 'Retirez d’abord ce rôle à ses utilisateurs' : ''" (click)="supprimer(r)">Supprimer</button>
            </div>
          }
        </article>
      }
    </div>

    <!-- Éditeur de rôle -->
    @if (edition(); as e) {
      <div class="fixed inset-0 z-[410] flex justify-end bg-neutre-900/50" (click)="edition.set(null)">
        <aside class="flex h-full w-full max-w-xl flex-col bg-white shadow-xl" role="dialog" aria-modal="true"
               [attr.aria-label]="e.id ? 'Modifier le rôle' : 'Nouveau rôle'" (click)="$event.stopPropagation()">
          <header class="flex items-center justify-between border-b border-neutre-200 px-5 py-4">
            <p class="font-display text-lg font-bold text-neutre-900">{{ e.id ? 'Modifier le rôle ' + roleLabel(e.name) : 'Nouveau rôle' }}</p>
            <button type="button" class="btn-ghost px-2" (click)="edition.set(null)" aria-label="Fermer"><app-icon name="x" class="h-5 w-5" /></button>
          </header>
          <form class="flex-1 space-y-5 overflow-y-auto px-5 py-5" (ngSubmit)="enregistrer()">
            @if (erreurEdition()) {
              <p class="rounded-lg bg-rouge-50 px-3 py-2 text-sm text-rouge-700" role="alert">{{ erreurEdition() }}</p>
            }
            <div>
              <label class="form-label" for="r-nom">Code du rôle *</label>
              <input id="r-nom" class="form-input font-mono uppercase" [(ngModel)]="e.name" name="name" [disabled]="!!e.id"
                     placeholder="AGENT_ACCUEIL" (ngModelChange)="e.name = normaliser($event)" />
              <p class="mt-1 text-xs text-neutre-600">Majuscules et « _ » uniquement. Il ne pourra plus être changé.</p>
            </div>
            <div>
              <label class="form-label" for="r-desc">Description</label>
              <input id="r-desc" class="form-input" [(ngModel)]="e.description" name="description" placeholder="À quoi sert ce rôle ?" />
            </div>
            <div>
              <p class="form-label">Autorisations <span class="font-normal text-neutre-500">({{ e.permissions.size }} cochée{{ e.permissions.size > 1 ? 's' : '' }})</span></p>
              <div class="space-y-3">
                @for (g of groupes; track g.label) {
                  <fieldset class="rounded-lg border border-neutre-200 p-3">
                    <legend class="flex w-full items-center justify-between px-1">
                      <span class="font-display text-sm font-semibold text-neutre-900">{{ g.label }}</span>
                    </legend>
                    <button type="button" class="mb-1 text-xs text-vert-700 hover:underline" (click)="toutCocher(g.permissions)">
                      {{ toutCoche(g.permissions) ? 'Tout décocher' : 'Tout cocher' }}
                    </button>
                    @for (p of g.permissions; track p) {
                      <label class="flex cursor-pointer items-center gap-2 py-1 text-sm text-neutre-800">
                        <input type="checkbox" class="h-4 w-4 accent-vert-600" [checked]="e.permissions.has(p)" (change)="basculer(p)" />
                        {{ permissionLabel(p) }}
                        @if (p === 'ROLE_MANAGE' || p === 'USER_MANAGE') { <span class="badge bg-rouge-50 text-rouge-700">sensible</span> }
                      </label>
                    }
                  </fieldset>
                }
              </div>
            </div>
            <div class="flex gap-2 border-t border-neutre-200 pt-4">
              <button type="submit" class="btn-primary" [disabled]="occupe() || !e.name || !e.permissions.size">{{ e.id ? 'Enregistrer' : 'Créer le rôle' }}</button>
              <button type="button" class="btn-ghost border border-neutre-300" (click)="edition.set(null)">Annuler</button>
            </div>
          </form>
        </aside>
      </div>
    }
  `,
})
export class AdminRolesComponent extends ApiBase {
  readonly groupes = PERMISSION_GROUPS;
  readonly roleLabel = roleLabel;
  readonly roleChip = roleChip;
  readonly permissionLabel = permissionLabel;

  roles = signal<RoleInfo[]>([]);
  message = signal<string | null>(null);
  erreur = signal(false);
  occupe = signal(false);
  edition = signal<{ id?: string; name: string; description: string; permissions: Set<string> } | null>(null);
  erreurEdition = signal<string | null>(null);

  constructor() {
    super();
    this.charger();
  }

  charger(): void {
    // rôles système d'abord, puis les rôles personnalisés
    this.get<RoleInfo[]>('/roles').subscribe((r) =>
      this.roles.set([...r].sort((a, b) => Number(b.systemRole) - Number(a.systemRole) || a.name.localeCompare(b.name))),
    );
  }

  normaliser = (v: string) => v.toUpperCase().replace(/[^A-Z0-9_]/g, '_');

  nouveau(): void {
    this.erreurEdition.set(null);
    this.edition.set({ name: '', description: '', permissions: new Set() });
  }

  modifier(r: RoleInfo): void {
    this.erreurEdition.set(null);
    this.edition.set({ id: r.id, name: r.name, description: r.description ?? '', permissions: new Set(r.permissions) });
  }

  basculer(p: string): void {
    const e = this.edition();
    if (!e) return;
    e.permissions.has(p) ? e.permissions.delete(p) : e.permissions.add(p);
    this.edition.set({ ...e, permissions: new Set(e.permissions) });
  }

  toutCoche = (ps: string[]) => ps.every((p) => this.edition()?.permissions.has(p));

  toutCocher(ps: string[]): void {
    const e = this.edition();
    if (!e) return;
    const s = new Set(e.permissions);
    const tous = ps.every((p) => s.has(p));
    ps.forEach((p) => (tous ? s.delete(p) : s.add(p)));
    this.edition.set({ ...e, permissions: s });
  }

  enregistrer(): void {
    const e = this.edition();
    if (!e) return;
    const permissions = [...e.permissions];
    const req = e.id
      ? this.http.put(`${this.base}/roles/${e.id}`, { description: e.description, permissions })
      : this.http.post(`${this.base}/roles`, { name: e.name.replace(/^_+|_+$/g, ''), description: e.description, permissions });
    this.occupe.set(true);
    req.subscribe({
      next: () => {
        this.occupe.set(false);
        this.edition.set(null);
        this.notifier(e.id ? 'Rôle modifié.' : 'Rôle créé.', false);
        this.charger();
      },
      error: (err: HttpErrorResponse) => {
        this.occupe.set(false);
        this.erreurEdition.set(this.erreurDe(err, 'Enregistrement impossible.'));
      },
    });
  }

  supprimer(r: RoleInfo): void {
    if (!confirm(`Supprimer le rôle « ${roleLabel(r.name)} » ?`)) return;
    this.http.delete(`${this.base}/roles/${r.id}`).subscribe({
      next: () => {
        this.notifier('Rôle supprimé.', false);
        this.charger();
      },
      error: (err: HttpErrorResponse) => this.notifier(this.erreurDe(err, 'Suppression impossible.'), true),
    });
  }

  private notifier(t: string, e: boolean): void {
    this.message.set(t);
    this.erreur.set(e);
  }

  private erreurDe(err: HttpErrorResponse, defaut: string): string {
    if (err.status === 403) return 'Vous n’avez pas l’autorisation pour cette action.';
    const body = err.error as (ApiError & { errors?: { field: string; message: string }[] }) | undefined;
    return body?.message ?? defaut;
  }
}
