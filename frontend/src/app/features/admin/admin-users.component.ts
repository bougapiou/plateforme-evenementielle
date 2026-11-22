import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ApiBase } from '../../core/api';
import { AuthService } from '../../core/auth.service';
import { ApiError, Page, UserSummary, UserType } from '../../core/models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { IconComponent } from '../../shared/icon.component';
import { formatDateTime } from '../../shared/format';
import {
  PERMISSION_GROUPS,
  RoleInfo,
  TYPES,
  UserDetail,
  permissionLabel,
  roleChip,
  roleLabel,
  typeLabel,
} from './rbac';

interface UserHistory {
  commandes: number;
  billets: number;
  inscriptions: number;
  paiements: number;
  factures: number;
  reservationsStands: number;
  structures: number;
  evenementsOrganises: number;
}

type Onglet = 'profil' | 'roles' | 'motdepasse' | 'compte';

/** Gestion des comptes : liste filtrée, création, profil, rôles, mot de passe, statut, suppression. */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [FormsModule, RouterLink, StatusBadgeComponent, IconComponent],
  template: `
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-[1.625rem] text-neutre-900">Utilisateurs</h1>
        <p class="mt-1 text-sm text-neutre-600">
          {{ total() }} compte{{ total() > 1 ? 's' : '' }} · créez des comptes, modifiez leurs informations, leurs rôles et leur mot de passe.
        </p>
      </div>
      <div class="flex flex-wrap gap-2">
        @if (peutGererRoles) {
          <a routerLink="/tableau-de-bord/admin/roles" class="btn-ghost border border-neutre-300">
            <app-icon name="key" class="h-4 w-4" aria-hidden="true" /> Rôles et autorisations
          </a>
        }
        @if (peutGerer) {
          <button type="button" class="btn-primary" (click)="ouvrirCreation()">
            <span class="text-lg leading-none">+</span> Ajouter un utilisateur
          </button>
        }
      </div>
    </div>

    <!-- Filtres -->
    <div class="mt-6 grid gap-3 rounded-xl border border-neutre-200 bg-white p-3 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_1fr]">
      <label class="relative block">
        <span class="sr-only">Rechercher</span>
        <app-icon name="search" class="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-neutre-500" aria-hidden="true" />
        <input class="form-input pl-10" placeholder="Nom ou e-mail…" [(ngModel)]="search" (ngModelChange)="recharger(0)" />
      </label>
      <select class="form-input" [(ngModel)]="filtreType" (ngModelChange)="recharger(0)" aria-label="Type de compte">
        <option value="">Tous les types</option>
        @for (t of types; track t.value) { <option [value]="t.value">{{ t.label }}</option> }
      </select>
      <select class="form-input" [(ngModel)]="filtreRole" (ngModelChange)="recharger(0)" aria-label="Rôle">
        <option value="">Tous les rôles</option>
        @for (r of roles(); track r.id) { <option [value]="r.name">{{ roleLabel(r.name) }}</option> }
      </select>
      <select class="form-input" [(ngModel)]="filtreStatut" (ngModelChange)="recharger(0)" aria-label="Statut">
        <option value="">Tous les statuts</option>
        <option value="ACTIF">Actifs</option>
        <option value="DESACTIVE">Suspendus</option>
        <option value="EN_ATTENTE">En attente</option>
      </select>
    </div>

    @if (message()) {
      <p class="mt-4 rounded-lg px-4 py-3 text-sm" role="status"
         [class]="messageIsError() ? 'bg-rouge-50 text-rouge-700' : 'bg-vert-50 text-vert-800'">
        {{ message() }}
      </p>
    }

    <!-- Liste -->
    <div class="mt-4 overflow-x-auto rounded-xl border border-neutre-200 bg-white">
      <table class="w-full min-w-[760px] text-sm">
        <thead class="bg-neutre-50 text-left text-xs uppercase tracking-wide text-neutre-600">
          <tr>
            <th class="px-4 py-3 font-semibold">Utilisateur</th>
            <th class="px-4 py-3 font-semibold">Type</th>
            <th class="px-4 py-3 font-semibold">Rôles</th>
            <th class="px-4 py-3 font-semibold">Statut</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-neutre-100">
          @for (u of users(); track u.id) {
            <tr class="hover:bg-neutre-25">
              <td class="px-4 py-3">
                <div class="flex items-center gap-3">
                  <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-vert-50 font-display text-xs font-bold text-vert-700">{{ initiales(u.fullName) }}</span>
                  <div class="min-w-0">
                    <p class="truncate font-medium text-neutre-900">
                      {{ u.fullName }}
                      @if (u.id === me()?.id) { <span class="ml-1 text-xs font-normal text-neutre-500">(vous)</span> }
                      @if (u.guest) { <span class="ml-1 badge bg-neutre-100 text-neutre-600">invité</span> }
                    </p>
                    <p class="truncate text-neutre-600">{{ u.email }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3 text-neutre-700">{{ typeLabel(u.type) }}</td>
              <td class="px-4 py-3">
                <div class="flex flex-wrap gap-1">
                  @for (r of u.roles; track r) { <span class="badge" [class]="roleChip(r)">{{ roleLabel(r) }}</span> }
                </div>
              </td>
              <td class="px-4 py-3"><app-status-badge [value]="u.status" /></td>
              <td class="px-4 py-3 text-right">
                <button type="button" class="btn-secondary min-h-[36px] px-3 py-1 text-xs" (click)="ouvrir(u)">Gérer</button>
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="px-4 py-10 text-center text-neutre-500">Aucun utilisateur ne correspond.</td></tr>
          }
        </tbody>
      </table>
    </div>
    @if (pages() > 1) {
      <div class="mt-3 flex items-center justify-between text-sm text-neutre-600">
        <span>Page {{ page() + 1 }} sur {{ pages() }}</span>
        <div class="flex gap-2">
          <button type="button" class="btn-ghost border border-neutre-300" [disabled]="page() === 0" (click)="recharger(page() - 1)">Précédent</button>
          <button type="button" class="btn-ghost border border-neutre-300" [disabled]="page() + 1 >= pages()" (click)="recharger(page() + 1)">Suivant</button>
        </div>
      </div>
    }

    <!-- ================= Panneau « Gérer l'utilisateur » ================= -->
    @if (detail(); as d) {
      <div class="fixed inset-0 z-[410] flex justify-end bg-neutre-900/50" (click)="fermer()">
        <aside class="flex h-full w-full max-w-xl flex-col bg-white shadow-xl" role="dialog" aria-modal="true"
               [attr.aria-label]="'Gérer ' + d.fullName" (click)="$event.stopPropagation()">
          <header class="border-b border-neutre-200 px-5 py-4">
            <div class="flex items-start justify-between gap-3">
              <div class="flex min-w-0 items-center gap-3">
                <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-vert-50 font-display font-bold text-vert-700">{{ initiales(d.fullName) }}</span>
                <div class="min-w-0">
                  <p class="truncate font-display text-lg font-bold text-neutre-900">{{ d.fullName }}</p>
                  <p class="truncate text-sm text-neutre-600">{{ d.email }}</p>
                </div>
              </div>
              <button type="button" class="btn-ghost px-2" (click)="fermer()" aria-label="Fermer"><app-icon name="x" class="h-5 w-5" /></button>
            </div>
            <p class="mt-2 flex flex-wrap items-center gap-2 text-xs text-neutre-600">
              <app-status-badge [value]="d.status" />
              <span>{{ typeLabel(d.type) }}</span>
              <span>· créé le {{ dt(d.createdAt) }}</span>
              <span>· dernière connexion : {{ d.lastLoginAt ? dt(d.lastLoginAt) : 'jamais' }}</span>
            </p>
            <nav class="-mb-4 mt-4 flex gap-1 overflow-x-auto" role="tablist">
              @for (o of onglets; track o.id) {
                <button type="button" role="tab" [attr.aria-selected]="onglet() === o.id" (click)="changerOnglet(o.id)"
                        class="whitespace-nowrap border-b-2 px-3 py-2 font-display text-sm font-semibold"
                        [class]="onglet() === o.id ? 'border-vert-600 text-vert-700' : 'border-transparent text-neutre-600 hover:text-neutre-900'">
                  {{ o.label }}
                </button>
              }
            </nav>
          </header>

          <div class="flex-1 overflow-y-auto px-5 py-5">
            @if (erreurPanneau()) {
              <p class="mb-4 rounded-lg bg-rouge-50 px-3 py-2 text-sm text-rouge-700" role="alert">{{ erreurPanneau() }}</p>
            }
            @if (succesPanneau()) {
              <p class="mb-4 rounded-lg bg-vert-50 px-3 py-2 text-sm text-vert-800" role="status">{{ succesPanneau() }}</p>
            }

            <!-- PROFIL -->
            @if (onglet() === 'profil') {
              <form class="grid gap-4 sm:grid-cols-2" (ngSubmit)="enregistrerProfil()">
                <div><label class="form-label" for="p-prenom">Prénom *</label>
                  <input id="p-prenom" class="form-input" [(ngModel)]="profil.firstName" name="firstName" required [disabled]="!peutGerer" /></div>
                <div><label class="form-label" for="p-nom">Nom *</label>
                  <input id="p-nom" class="form-input" [(ngModel)]="profil.lastName" name="lastName" required [disabled]="!peutGerer" /></div>
                <div class="sm:col-span-2"><label class="form-label" for="p-email">Adresse e-mail (identifiant de connexion) *</label>
                  <input id="p-email" class="form-input" type="email" [(ngModel)]="profil.email" name="email" required [disabled]="!peutGerer" /></div>
                <div><label class="form-label" for="p-tel">Téléphone</label>
                  <input id="p-tel" class="form-input" [(ngModel)]="profil.phone" name="phone" placeholder="+226 70 00 00 00" [disabled]="!peutGerer" /></div>
                <div><label class="form-label" for="p-type">Type de compte</label>
                  <select id="p-type" class="form-input" [(ngModel)]="profil.type" name="type" [disabled]="!peutGerer">
                    @for (t of types; track t.value) { <option [value]="t.value">{{ t.label }}</option> }
                  </select></div>
                <p class="text-xs text-neutre-600 sm:col-span-2">
                  Le type adapte l’espace de l’utilisateur ; ce sont les <b>rôles</b> qui donnent les autorisations.
                </p>
                @if (peutGerer) {
                  <div class="sm:col-span-2"><button type="submit" class="btn-primary" [disabled]="occupe()">Enregistrer le profil</button></div>
                }
              </form>
            }

            <!-- RÔLES -->
            @if (onglet() === 'roles') {
              <p class="text-sm text-neutre-700">Cochez les rôles de cet utilisateur. Ses autorisations sont la somme de celles de ses rôles.</p>
              <ul class="mt-4 space-y-2">
                @for (r of roles(); track r.id) {
                  <li>
                    <label class="flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors"
                           [class]="rolesChoisis().has(r.name) ? 'border-vert-600 bg-vert-50' : 'border-neutre-200 hover:border-neutre-400'">
                      <input type="checkbox" class="mt-1 h-4 w-4 accent-vert-600" [checked]="rolesChoisis().has(r.name)"
                             [disabled]="!peutGererRoles" (change)="basculerRole(r.name)" />
                      <span class="min-w-0 flex-1">
                        <span class="flex flex-wrap items-center gap-2">
                          <span class="font-display font-semibold text-neutre-900">{{ roleLabel(r.name) }}</span>
                          @if (r.systemRole) { <span class="badge bg-neutre-100 text-neutre-600">système</span> }
                          <span class="text-xs text-neutre-500">{{ r.permissions.length }} autorisation{{ r.permissions.length > 1 ? 's' : '' }}</span>
                        </span>
                        @if (r.description) { <span class="mt-0.5 block text-xs text-neutre-600">{{ r.description }}</span> }
                      </span>
                    </label>
                  </li>
                }
              </ul>

              <div class="mt-5 rounded-lg border border-neutre-200 bg-neutre-25 p-4">
                <p class="surtitre text-neutre-600">Ce que cet utilisateur pourra faire</p>
                @if (autorisationsChoisies().length) {
                  @for (g of autorisationsParGroupe(); track g.label) {
                    <p class="mt-3 text-xs font-semibold text-neutre-800">{{ g.label }}</p>
                    <ul class="mt-1 space-y-0.5 text-xs text-neutre-700">
                      @for (p of g.permissions; track p) {
                        <li class="flex items-center gap-1.5"><app-icon name="check" class="h-3.5 w-3.5 text-vert-600" aria-hidden="true" /> {{ permissionLabel(p) }}</li>
                      }
                    </ul>
                  }
                } @else {
                  <p class="mt-2 text-xs text-neutre-600">Aucune autorisation.</p>
                }
              </div>
              @if (peutGererRoles) {
                <button type="button" class="btn-primary mt-5" [disabled]="occupe() || !rolesModifies()" (click)="enregistrerRoles()">Enregistrer les rôles</button>
                <p class="mt-2 text-xs text-neutre-600">Le changement s’applique à la prochaine reconnexion de l’utilisateur (au plus tard sous 15 minutes).</p>
              } @else {
                <p class="mt-4 text-xs text-neutre-600">Vous n’avez pas l’autorisation de modifier les rôles.</p>
              }
            }

            <!-- MOT DE PASSE -->
            @if (onglet() === 'motdepasse') {
              @if (motDePasseObtenu(); as mdp) {
                <div class="rounded-xl border-2 border-vert-600 bg-vert-50 p-4">
                  <p class="font-display font-bold text-vert-800">Nouveau mot de passe défini</p>
                  <p class="mt-1 text-sm text-neutre-700">Communiquez-le à l’utilisateur de façon sûre. <b>Il ne sera plus affiché.</b></p>
                  <div class="mt-3 flex items-center gap-2">
                    <code class="flex-1 rounded-md border border-neutre-300 bg-white px-3 py-2 font-mono text-lg tracking-wider text-neutre-900">{{ mdp }}</code>
                    <button type="button" class="btn-secondary" (click)="copier(identifiants(d.email, mdp))">{{ copie() ? 'Copié ✓' : 'Copier' }}</button>
                  </div>
                </div>
              } @else if (peutGerer) {
                <p class="text-sm text-neutre-700">Définissez un nouveau mot de passe pour <b>{{ d.fullName }}</b>.</p>
                <fieldset class="mt-4 space-y-2">
                  <label class="flex cursor-pointer items-start gap-3 rounded-lg border p-3" [class]="mdpMode === 'auto' ? 'border-vert-600 bg-vert-50' : 'border-neutre-200'">
                    <input type="radio" name="mdpMode" value="auto" [(ngModel)]="mdpMode" class="mt-1 accent-vert-600" />
                    <span><span class="font-semibold text-neutre-900">Générer automatiquement</span> <span class="badge bg-vert-100 text-vert-800">recommandé</span>
                      <span class="block text-xs text-neutre-600">Un mot de passe solide de 12 caractères, affiché une seule fois.</span></span>
                  </label>
                  <label class="flex cursor-pointer items-start gap-3 rounded-lg border p-3" [class]="mdpMode === 'saisi' ? 'border-vert-600 bg-vert-50' : 'border-neutre-200'">
                    <input type="radio" name="mdpMode" value="saisi" [(ngModel)]="mdpMode" class="mt-1 accent-vert-600" />
                    <span class="flex-1"><span class="font-semibold text-neutre-900">Saisir un mot de passe</span>
                      @if (mdpMode === 'saisi') {
                        <span class="mt-2 flex gap-2">
                          <input class="form-input" [type]="voirMdp ? 'text' : 'password'" [(ngModel)]="mdpSaisi" name="mdpSaisi"
                                 placeholder="8 caractères minimum" autocomplete="new-password" />
                          <button type="button" class="btn-ghost border border-neutre-300 px-3" (click)="voirMdp = !voirMdp"
                                  [attr.aria-label]="voirMdp ? 'Masquer' : 'Afficher'"><app-icon [name]="voirMdp ? 'eye-off' : 'eye'" class="h-4 w-4" /></button>
                        </span>
                      }
                    </span>
                  </label>
                </fieldset>
                <p class="mt-3 flex items-start gap-2 rounded-lg bg-ocre-50 px-3 py-2 text-xs text-ocre-800">
                  <app-icon name="info" class="h-4 w-4 shrink-0" aria-hidden="true" />
                  L’utilisateur sera déconnecté de tous ses appareils et devra se reconnecter avec ce mot de passe.
                </p>
                <button type="button" class="btn-primary mt-4" [disabled]="occupe() || (mdpMode === 'saisi' && mdpSaisi.length < 8)" (click)="definirMotDePasse()">
                  {{ mdpMode === 'auto' ? 'Générer un nouveau mot de passe' : 'Définir ce mot de passe' }}
                </button>
              }
            }

            <!-- COMPTE -->
            @if (onglet() === 'compte') {
              <div class="space-y-4">
                <div class="rounded-lg border border-neutre-200 p-4">
                  <p class="font-display font-semibold text-neutre-900">{{ d.status === 'DESACTIVE' ? 'Réactiver le compte' : 'Suspendre le compte' }}</p>
                  <p class="mt-1 text-sm text-neutre-600">
                    {{ d.status === 'DESACTIVE'
                      ? 'L’utilisateur pourra de nouveau se connecter.'
                      : 'L’utilisateur ne peut plus se connecter et est déconnecté de ses appareils. Son historique est conservé.' }}
                  </p>
                  <button type="button" class="mt-3" [class]="d.status === 'DESACTIVE' ? 'btn-primary' : 'btn-secondary'"
                          [disabled]="occupe() || d.id === me()?.id || !peutGerer" (click)="basculerStatut(d)">
                    {{ d.status === 'DESACTIVE' ? 'Réactiver' : 'Suspendre' }}
                  </button>
                </div>
                <div class="rounded-lg border border-rouge-200 bg-rouge-50/40 p-4">
                  <p class="font-display font-semibold text-rouge-700">Supprimer définitivement</p>
                  <p class="mt-1 text-sm text-neutre-700">Efface le compte et, après confirmation, son historique (billets, paiements…). Préférez « Suspendre » pour le conserver.</p>
                  <button type="button" class="btn-danger mt-3" [disabled]="occupe() || d.id === me()?.id || !peutGerer" (click)="supprimer(d)">Supprimer le compte</button>
                </div>
                @if (d.id === me()?.id) {
                  <p class="text-xs text-neutre-600">Vous ne pouvez ni suspendre ni supprimer votre propre compte.</p>
                }
              </div>
            }
          </div>
        </aside>
      </div>
    }

    <!-- ================= Panneau « Ajouter un utilisateur » ================= -->
    @if (creation()) {
      <div class="fixed inset-0 z-[410] flex justify-end bg-neutre-900/50" (click)="creation.set(false)">
        <aside class="flex h-full w-full max-w-xl flex-col bg-white shadow-xl" role="dialog" aria-modal="true"
               aria-label="Ajouter un utilisateur" (click)="$event.stopPropagation()">
          <header class="flex items-center justify-between border-b border-neutre-200 px-5 py-4">
            <div>
              <p class="surtitre text-vert-700">Nouveau compte</p>
              <p class="font-display text-lg font-bold text-neutre-900">Ajouter un utilisateur</p>
            </div>
            <button type="button" class="btn-ghost px-2" (click)="creation.set(false)" aria-label="Fermer"><app-icon name="x" class="h-5 w-5" /></button>
          </header>

          <div class="flex-1 overflow-y-auto px-5 py-5">
            @if (cree(); as c) {
              <div class="rounded-xl border-2 border-vert-600 bg-vert-50 p-5">
                <p class="flex items-center gap-2 font-display text-lg font-bold text-vert-800"><app-icon name="check" class="h-5 w-5" /> Compte créé</p>
                <dl class="mt-3 space-y-2 text-sm">
                  <div><dt class="text-neutre-600">Utilisateur</dt><dd class="font-semibold text-neutre-900">{{ c.nom }}</dd></div>
                  <div><dt class="text-neutre-600">Identifiant (e-mail)</dt><dd class="font-mono text-neutre-900">{{ c.email }}</dd></div>
                  @if (c.motDePasse) {
                    <div><dt class="text-neutre-600">Mot de passe provisoire</dt>
                      <dd><code class="mt-1 inline-block rounded-md border border-neutre-300 bg-white px-3 py-1.5 font-mono text-lg tracking-wider">{{ c.motDePasse }}</code></dd></div>
                  } @else {
                    <div><dt class="text-neutre-600">Mot de passe</dt><dd class="text-neutre-900">celui que vous avez saisi</dd></div>
                  }
                </dl>
                @if (c.motDePasse) {
                  <p class="mt-3 text-sm text-neutre-700">Communiquez ces identifiants de façon sûre. <b>Le mot de passe ne sera plus affiché.</b></p>
                  <button type="button" class="btn-secondary mt-3" (click)="copier(identifiants(c.email, c.motDePasse))">{{ copie() ? 'Copié ✓' : 'Copier les identifiants' }}</button>
                }
              </div>
              <div class="mt-5 flex gap-2">
                <button type="button" class="btn-primary" (click)="ouvrirCreation()">Ajouter un autre utilisateur</button>
                <button type="button" class="btn-ghost border border-neutre-300" (click)="creation.set(false)">Fermer</button>
              </div>
            } @else {
              @if (erreurCreation()) {
                <p class="mb-4 rounded-lg bg-rouge-50 px-3 py-2 text-sm text-rouge-700" role="alert">{{ erreurCreation() }}</p>
              }
              <form class="space-y-6" (ngSubmit)="creer()">
                <section>
                  <p class="surtitre text-neutre-600">Identité</p>
                  <div class="mt-3 grid gap-3 sm:grid-cols-2">
                    <div><label class="form-label" for="c-prenom">Prénom *</label><input id="c-prenom" class="form-input" [(ngModel)]="nouveau.firstName" name="firstName" required /></div>
                    <div><label class="form-label" for="c-nom">Nom *</label><input id="c-nom" class="form-input" [(ngModel)]="nouveau.lastName" name="lastName" required /></div>
                    <div class="sm:col-span-2"><label class="form-label" for="c-email">Adresse e-mail (identifiant) *</label>
                      <input id="c-email" class="form-input" type="email" [(ngModel)]="nouveau.email" name="email" required autocomplete="off" /></div>
                    <div class="sm:col-span-2"><label class="form-label" for="c-tel">Téléphone</label>
                      <input id="c-tel" class="form-input" [(ngModel)]="nouveau.phone" name="phone" placeholder="+226 70 00 00 00" /></div>
                  </div>
                </section>

                <section>
                  <p class="surtitre text-neutre-600">Type de compte</p>
                  <div class="mt-3 grid gap-2 sm:grid-cols-2">
                    @for (t of types; track t.value) {
                      <label class="flex cursor-pointer items-start gap-2 rounded-lg border p-3"
                             [class]="nouveau.type === t.value ? 'border-vert-600 bg-vert-50' : 'border-neutre-200 hover:border-neutre-400'">
                        <input type="radio" name="type" [value]="t.value" [(ngModel)]="nouveau.type" (ngModelChange)="typeChoisi($event)" class="mt-1 accent-vert-600" />
                        <span><span class="block font-semibold text-neutre-900">{{ t.label }}</span><span class="block text-xs text-neutre-600">{{ t.aide }}</span></span>
                      </label>
                    }
                  </div>
                  @if (nouveau.type === 'ORGANISATEUR') {
                    <div class="mt-3"><label class="form-label" for="c-orga">Nom de l’organisation (affiché sur ses événements)</label>
                      <input id="c-orga" class="form-input" [(ngModel)]="nouveau.organisationNom" name="organisationNom" placeholder="Ex. : Comité national d’organisation du SIAO" /></div>
                  }
                </section>

                <section>
                  <p class="surtitre text-neutre-600">Rôles</p>
                  @if (peutGererRoles) {
                    <div class="mt-3 flex flex-wrap gap-2">
                      @for (r of roles(); track r.id) {
                        <label class="inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-sm"
                               [class]="nouveauxRoles().has(r.name) ? 'border-vert-600 bg-vert-50 text-vert-800' : 'border-neutre-300 text-neutre-700'">
                          <input type="checkbox" class="accent-vert-600" [checked]="nouveauxRoles().has(r.name)" (change)="basculerNouveauRole(r.name)" />
                          {{ roleLabel(r.name) }}
                        </label>
                      }
                    </div>
                    <p class="mt-2 text-xs text-neutre-600">Pré-rempli selon le type de compte ; vous pouvez en ajouter.</p>
                  } @else {
                    <p class="mt-2 text-sm text-neutre-700">Rôle par défaut du type : <b>{{ roleLabel(roleParDefaut(nouveau.type)) }}</b>.</p>
                  }
                </section>

                <section>
                  <p class="surtitre text-neutre-600">Mot de passe</p>
                  <div class="mt-3 space-y-2">
                    <label class="flex cursor-pointer items-start gap-3 rounded-lg border p-3" [class]="nouveau.mdpMode === 'auto' ? 'border-vert-600 bg-vert-50' : 'border-neutre-200'">
                      <input type="radio" name="cmdp" value="auto" [(ngModel)]="nouveau.mdpMode" class="mt-1 accent-vert-600" />
                      <span><span class="font-semibold text-neutre-900">Générer automatiquement</span> <span class="badge bg-vert-100 text-vert-800">recommandé</span>
                        <span class="block text-xs text-neutre-600">Affiché une seule fois après la création, à transmettre à l’utilisateur.</span></span>
                    </label>
                    <label class="flex cursor-pointer items-start gap-3 rounded-lg border p-3" [class]="nouveau.mdpMode === 'saisi' ? 'border-vert-600 bg-vert-50' : 'border-neutre-200'">
                      <input type="radio" name="cmdp" value="saisi" [(ngModel)]="nouveau.mdpMode" class="mt-1 accent-vert-600" />
                      <span class="flex-1"><span class="font-semibold text-neutre-900">Saisir un mot de passe</span>
                        @if (nouveau.mdpMode === 'saisi') {
                          <input class="form-input mt-2" type="text" [(ngModel)]="nouveau.password" name="password" placeholder="8 caractères minimum" autocomplete="new-password" />
                        }
                      </span>
                    </label>
                  </div>
                </section>

                <div class="flex gap-2 border-t border-neutre-200 pt-4">
                  <button type="submit" class="btn-primary" [disabled]="occupe() || !creationValide()">Créer le compte</button>
                  <button type="button" class="btn-ghost border border-neutre-300" (click)="creation.set(false)">Annuler</button>
                </div>
              </form>
            }
          </div>
        </aside>
      </div>
    }
  `,
})
export class AdminUsersComponent extends ApiBase {
  private auth = inject(AuthService);

  readonly types = TYPES;
  readonly roleLabel = roleLabel;
  readonly typeLabel = typeLabel;
  readonly roleChip = roleChip;
  readonly permissionLabel = permissionLabel;
  readonly peutGerer = this.auth.hasPermission('USER_MANAGE');
  readonly peutGererRoles = this.auth.hasPermission('ROLE_MANAGE');
  readonly onglets: { id: Onglet; label: string }[] = [
    { id: 'profil', label: 'Profil' },
    { id: 'roles', label: 'Rôles et autorisations' },
    { id: 'motdepasse', label: 'Mot de passe' },
    { id: 'compte', label: 'Compte' },
  ];

  users = signal<UserSummary[]>([]);
  roles = signal<RoleInfo[]>([]);
  total = signal(0);
  page = signal(0);
  pages = signal(1);
  me = this.auth.user;
  search = '';
  filtreType = '';
  filtreRole = '';
  filtreStatut = '';

  message = signal<string | null>(null);
  messageIsError = signal(false);
  occupe = signal(false);
  copie = signal(false);

  // panneau « Gérer »
  detail = signal<UserDetail | null>(null);
  onglet = signal<Onglet>('profil');
  erreurPanneau = signal<string | null>(null);
  succesPanneau = signal<string | null>(null);
  profil = { firstName: '', lastName: '', email: '', phone: '', type: 'PARTICULIER' as UserType };
  rolesChoisis = signal<Set<string>>(new Set());
  motDePasseObtenu = signal<string | null>(null);
  mdpMode: 'auto' | 'saisi' = 'auto';
  mdpSaisi = '';
  voirMdp = false;

  // panneau « Ajouter »
  creation = signal(false);
  erreurCreation = signal<string | null>(null);
  cree = signal<{ nom: string; email: string; motDePasse: string | null } | null>(null);
  nouveau = this.nouveauVide();
  nouveauxRoles = signal<Set<string>>(new Set(['PARTICIPANT']));

  autorisationsChoisies = computed(() => {
    const set = new Set<string>();
    for (const r of this.roles()) if (this.rolesChoisis().has(r.name)) r.permissions.forEach((p) => set.add(p));
    return [...set];
  });
  autorisationsParGroupe = computed(() => {
    const choisies = new Set(this.autorisationsChoisies());
    return PERMISSION_GROUPS.map((g) => ({ label: g.label, permissions: g.permissions.filter((p) => choisies.has(p)) }))
      .filter((g) => g.permissions.length);
  });
  rolesModifies = computed(() => {
    const d = this.detail();
    if (!d) return false;
    const a = [...this.rolesChoisis()].sort().join();
    return a !== [...d.roles].sort().join();
  });

  constructor() {
    super();
    this.recharger(0);
    if (this.peutGererRoles) this.get<RoleInfo[]>('/roles').subscribe((r) => this.roles.set(r));
  }

  // ---------------- liste ----------------
  recharger(page: number): void {
    const params: Record<string, string | number> = { search: this.search, page, size: 20, sort: 'createdAt,desc' };
    if (this.filtreType) params['type'] = this.filtreType;
    if (this.filtreRole) params['role'] = this.filtreRole;
    if (this.filtreStatut) params['status'] = this.filtreStatut;
    this.get<Page<UserSummary>>('/users', params).subscribe((p) => {
      this.users.set(p.content);
      this.total.set(p.totalElements);
      this.page.set(p.page);
      this.pages.set(Math.max(1, p.totalPages));
    });
  }

  initiales = (nom: string) => nom.split(/\s+/).filter(Boolean).slice(0, 2).map((m) => m[0]!.toUpperCase()).join('');
  dt = (iso?: string) => formatDateTime(iso);
  identifiants = (email: string, mdp: string) => `Identifiant : ${email}\nMot de passe : ${mdp}`;
  roleParDefaut = (t: UserType) => TYPES.find((x) => x.value === t)?.role ?? 'PARTICIPANT';

  // ---------------- panneau « Gérer » ----------------
  ouvrir(u: UserSummary): void {
    this.message.set(null);
    this.get<UserDetail>(`/users/${u.id}`).subscribe((d) => {
      this.detail.set(d);
      this.onglet.set('profil');
      this.profil = { firstName: d.firstName, lastName: d.lastName, email: d.email, phone: d.phone ?? '', type: d.type };
      this.rolesChoisis.set(new Set(d.roles));
      this.reinitialiserPanneau();
    });
  }

  fermer(): void {
    this.detail.set(null);
  }

  changerOnglet(o: Onglet): void {
    this.onglet.set(o);
    this.reinitialiserPanneau();
  }

  private reinitialiserPanneau(): void {
    this.erreurPanneau.set(null);
    this.succesPanneau.set(null);
    this.motDePasseObtenu.set(null);
    this.mdpMode = 'auto';
    this.mdpSaisi = '';
    this.voirMdp = false;
    this.copie.set(false);
  }

  enregistrerProfil(): void {
    const d = this.detail();
    if (!d) return;
    this.action(this.http.patch<UserDetail>(`${this.base}/users/${d.id}`, this.profil), (u) => {
      this.detail.set(u);
      this.succesPanneau.set('Profil enregistré.');
    });
  }

  basculerRole(name: string): void {
    const s = new Set(this.rolesChoisis());
    s.has(name) ? s.delete(name) : s.add(name);
    this.rolesChoisis.set(s);
  }

  enregistrerRoles(): void {
    const d = this.detail();
    if (!d) return;
    this.action(this.http.put<UserDetail>(`${this.base}/users/${d.id}/roles`, { roles: [...this.rolesChoisis()] }), (u) => {
      this.detail.set(u);
      this.succesPanneau.set('Rôles enregistrés.');
    });
  }

  definirMotDePasse(): void {
    const d = this.detail();
    if (!d) return;
    const body = this.mdpMode === 'saisi' ? { password: this.mdpSaisi } : {};
    this.action(
      this.http.post<{ motDePasseTemporaire: string | null; sessionsFermees: number }>(`${this.base}/users/${d.id}/password`, body),
      (r) => {
        if (r.motDePasseTemporaire) this.motDePasseObtenu.set(r.motDePasseTemporaire);
        else this.succesPanneau.set('Mot de passe modifié. L’utilisateur a été déconnecté de ses appareils.');
        this.mdpSaisi = '';
      },
    );
  }

  basculerStatut(d: UserDetail): void {
    const status = d.status === 'DESACTIVE' ? 'ACTIF' : 'DESACTIVE';
    this.action(this.http.patch<UserDetail>(`${this.base}/users/${d.id}/status`, { status }), (u) => {
      this.detail.set(u);
      this.succesPanneau.set(status === 'ACTIF' ? 'Compte réactivé.' : 'Compte suspendu : l’utilisateur a été déconnecté.');
    });
  }

  supprimer(d: UserDetail): void {
    this.get<UserHistory>(`/users/${d.id}/history`).subscribe({
      next: (h) => {
        const labels: [keyof UserHistory, string][] = [
          ['commandes', 'commande(s)'], ['billets', 'billet(s)'], ['inscriptions', 'inscription(s)'],
          ['paiements', 'paiement(s)'], ['factures', 'facture(s)'], ['reservationsStands', 'réservation(s) de stand'],
          ['structures', 'structure(s)'], ['evenementsOrganises', 'événement(s) organisé(s)'],
        ];
        const resume = labels.filter(([k]) => h[k] > 0).map(([k, l]) => `${h[k]} ${l}`).join(', ');
        if (h.evenementsOrganises > 0) {
          this.erreurPanneau.set(`Ce compte organise ${h.evenementsOrganises} événement(s) : supprimez-les d’abord, ou suspendez le compte.`);
          return;
        }
        const texte = resume
          ? `ATTENTION — ce compte a un historique : ${resume}.\n\nLe supprimer effacera définitivement ces données. Pour les conserver, utilisez « Suspendre ».\n\nSupprimer quand même ${d.fullName} ?`
          : `Supprimer définitivement ${d.fullName} (${d.email}) ?`;
        if (!confirm(texte)) return;
        this.action(
          this.http.delete<void>(`${this.base}/users/${d.id}`, { params: resume ? { force: 'true' } : {} }),
          () => {
            this.detail.set(null);
            this.notifier('Utilisateur supprimé.', false);
          },
        );
      },
      error: (err: HttpErrorResponse) => this.erreurPanneau.set(this.erreurDe(err, 'Action impossible.')),
    });
  }

  // ---------------- panneau « Ajouter » ----------------
  private nouveauVide() {
    return {
      firstName: '', lastName: '', email: '', phone: '', type: 'PARTICULIER' as UserType,
      organisationNom: '', mdpMode: 'auto' as 'auto' | 'saisi', password: '',
    };
  }

  ouvrirCreation(): void {
    this.nouveau = this.nouveauVide();
    this.nouveauxRoles.set(new Set(['PARTICIPANT']));
    this.erreurCreation.set(null);
    this.cree.set(null);
    this.copie.set(false);
    this.creation.set(true);
  }

  typeChoisi(t: UserType): void {
    this.nouveauxRoles.set(new Set([this.roleParDefaut(t)]));
  }

  basculerNouveauRole(name: string): void {
    const s = new Set(this.nouveauxRoles());
    s.has(name) ? s.delete(name) : s.add(name);
    this.nouveauxRoles.set(s);
  }

  creationValide(): boolean {
    const n = this.nouveau;
    return !!(n.firstName.trim() && n.lastName.trim() && n.email.includes('@')
      && (n.mdpMode === 'auto' || n.password.length >= 8)
      && (!this.peutGererRoles || this.nouveauxRoles().size > 0));
  }

  creer(): void {
    const n = this.nouveau;
    const body = {
      email: n.email.trim(), firstName: n.firstName.trim(), lastName: n.lastName.trim(), phone: n.phone.trim(),
      type: n.type, organisationNom: n.organisationNom.trim() || undefined,
      password: n.mdpMode === 'saisi' ? n.password : undefined,
      roles: this.peutGererRoles ? [...this.nouveauxRoles()] : undefined,
    };
    this.occupe.set(true);
    this.erreurCreation.set(null);
    this.http.post<{ user: UserDetail; motDePasseTemporaire: string | null }>(`${this.base}/users`, body).subscribe({
      next: (r) => {
        this.occupe.set(false);
        this.cree.set({ nom: r.user.fullName, email: r.user.email, motDePasse: r.motDePasseTemporaire });
        this.recharger(0);
        if (this.peutGererRoles) this.get<RoleInfo[]>('/roles').subscribe((x) => this.roles.set(x));
      },
      error: (err: HttpErrorResponse) => {
        this.occupe.set(false);
        this.erreurCreation.set(this.erreurDe(err, 'Création impossible.'));
      },
    });
  }

  // ---------------- outils ----------------
  copier(texte: string): void {
    navigator.clipboard?.writeText(texte).then(() => {
      this.copie.set(true);
      setTimeout(() => this.copie.set(false), 2500);
    });
  }

  private action<T>(obs: import('rxjs').Observable<T>, ok: (r: T) => void): void {
    this.occupe.set(true);
    this.erreurPanneau.set(null);
    this.succesPanneau.set(null);
    obs.subscribe({
      next: (r) => {
        this.occupe.set(false);
        ok(r);
        this.recharger(this.page());
      },
      error: (err: HttpErrorResponse) => {
        this.occupe.set(false);
        this.erreurPanneau.set(this.erreurDe(err, 'Action impossible.'));
      },
    });
  }

  private notifier(texte: string, erreur: boolean): void {
    this.message.set(texte);
    this.messageIsError.set(erreur);
  }

  private erreurDe(err: HttpErrorResponse, defaut: string): string {
    const body = err.error as (ApiError & { errors?: { field: string; message: string }[] }) | undefined;
    if (err.status === 403) return 'Vous n’avez pas l’autorisation pour cette action.';
    return body?.message ?? defaut;
  }
}
