import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import { formatDateTime } from '../../shared/format';

interface AuditLog {
  id: string;
  actorId?: string;
  actorEmail?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  ipAddress?: string;
  details?: string;
  createdAt: string;
}

/** Readable labels of the most frequent audited actions. */
const ACTIONS: Record<string, string> = {
  AUTH_LOGIN: 'Connexion', AUTH_LOGIN_FAILED: 'Échec de connexion', AUTH_ACCOUNT_LOCKED: 'Compte verrouillé',
  AUTH_MFA_ENABLED: 'Double authentification activée', AUTH_MFA_FAILED: 'Code de vérification erroné',
  AUTH_LOGOUT: 'Déconnexion', AUTH_REGISTER: 'Création de compte', AUTH_PASSWORD_RESET: 'Mot de passe réinitialisé',
  USER_PASSWORD_CHANGED: 'Mot de passe changé', USER_STATUS_CHANGED: 'Statut utilisateur modifié',
  USER_MFA_RESET: 'Sécurité du compte réinitialisée', EVENT_SUBMITTED: 'Événement soumis',
  EVENT_VALIDATED: 'Événement validé', EVENT_REJECTED: 'Événement refusé', EVENT_PUBLISHED: 'Événement publié',
  EVENT_CANCELLED: 'Événement annulé', EVENT_SUSPENDED: 'Événement suspendu', EVENT_DELETED: 'Événement supprimé',
  EVENT_HIDDEN: 'Événement masqué du public', EVENT_SHOWN: 'Événement réaffiché',
  PARTICIPANTS_EXPORTED: 'Export de données personnelles', CHECKIN_OFFLINE_PACK: 'Liste hors ligne téléchargée',
  TICKET_ON_SITE: 'Billet émis sur place', DATA_ANONYMIZED: 'Données anonymisées',
};

/**
 * Audit log (SEC-05): log-ins, validations, publications, deletions, exports,
 * with author, date and IP. Read-only — the table refuses updates and deletes.
 */
@Component({
  selector: 'app-admin-audit',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Journal d'audit</h1>
    <p class="mt-1 text-sm text-slate-500">
      Traces non modifiables des actions sensibles : connexions, validations, publications,
      suppressions et exports de données.
    </p>

    <div class="card mt-4 flex flex-wrap items-end gap-3 p-4">
      <div class="min-w-48 flex-1">
        <label class="form-label">Recherche (auteur, détail, objet)</label>
        <input class="form-input" [(ngModel)]="q" (keyup.enter)="reload(0)" />
      </div>
      <div>
        <label class="form-label">Action</label>
        <select class="form-input" [(ngModel)]="action" (ngModelChange)="reload(0)">
          <option value="">Toutes</option>
          @for (a of actionCodes; track a) { <option [value]="a">{{ label(a) }}</option> }
        </select>
      </div>
      <div>
        <label class="form-label">Du</label>
        <input type="date" class="form-input" [(ngModel)]="du" (ngModelChange)="reload(0)" />
      </div>
      <div>
        <label class="form-label">Au</label>
        <input type="date" class="form-input" [(ngModel)]="au" (ngModelChange)="reload(0)" />
      </div>
      <button type="button" class="btn-primary" (click)="reload(0)">Filtrer</button>
    </div>

    <div class="card mt-4 overflow-x-auto p-4">
      <table class="w-full text-sm">
        <thead class="text-left text-xs uppercase text-slate-500">
          <tr><th class="py-1 pr-3">Date</th><th class="pr-3">Auteur</th><th class="pr-3">Action</th>
            <th class="pr-3">Objet</th><th class="pr-3">Adresse IP</th><th>Détail</th></tr>
        </thead>
        <tbody>
          @for (l of page()?.content ?? []; track l.id) {
            <tr class="border-t border-slate-100 align-top">
              <td class="whitespace-nowrap py-1.5 pr-3 text-slate-500">{{ dt(l.createdAt) }}</td>
              <td class="pr-3">{{ l.actorEmail || '—' }}</td>
              <td class="pr-3" [class.text-red-700]="l.action.includes('FAILED') || l.action.includes('LOCKED')">
                {{ label(l.action) }}
              </td>
              <td class="pr-3 text-slate-500">{{ l.entityType || '' }}</td>
              <td class="pr-3 font-mono text-xs">{{ l.ipAddress || '—' }}</td>
              <td class="text-xs text-slate-600">{{ l.details || '' }}</td>
            </tr>
          } @empty {
            <tr><td colspan="6" class="py-6 text-center text-slate-400">Aucune entrée.</td></tr>
          }
        </tbody>
      </table>
      @if (page(); as p) {
        <div class="mt-3 flex items-center justify-between text-sm">
          <span class="text-slate-500">{{ p.totalElements }} entrée(s)</span>
          <span class="flex gap-2">
            <button class="btn-ghost" [disabled]="p.page === 0" (click)="reload(p.page - 1)">← Précédent</button>
            <button class="btn-ghost" [disabled]="p.page + 1 >= p.totalPages" (click)="reload(p.page + 1)">Suivant →</button>
          </span>
        </div>
      }
    </div>
  `,
})
export class AdminAuditComponent extends ApiBase {
  page = signal<Page<AuditLog> | null>(null);
  q = '';
  action = '';
  du = '';
  au = '';
  actionCodes = Object.keys(ACTIONS);

  constructor() {
    super();
    this.reload(0);
  }

  label = (a: string) => ACTIONS[a] ?? a;
  dt = (iso?: string) => formatDateTime(iso);

  reload(pageIndex: number): void {
    this.get<Page<AuditLog>>('/audit-logs', {
      q: this.q.trim(), action: this.action, du: this.du, au: this.au, page: pageIndex, size: 50,
    }).subscribe((p) => this.page.set(p));
  }
}
