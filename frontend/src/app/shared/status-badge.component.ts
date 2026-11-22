import { Component, input } from '@angular/core';

const STYLES: Record<string, string> = {
  // structures / organizers
  EN_ATTENTE: 'bg-amber-100 text-amber-800',
  VERIFIEE: 'bg-green-100 text-green-800',
  ACTIF: 'bg-green-100 text-green-800',
  SUSPENDUE: 'bg-red-100 text-red-800',
  SUSPENDU: 'bg-red-100 text-red-800',
  DESACTIVE: 'bg-red-100 text-red-800',
  ACTIF_USER: 'bg-green-100 text-green-800',
  // events (used later)
  BROUILLON: 'bg-slate-100 text-slate-700',
  SOUMIS: 'bg-amber-100 text-amber-800',
  VALIDE: 'bg-blue-100 text-blue-800',
  PUBLIE: 'bg-green-100 text-green-800',
  INSCRIPTIONS_OUVERTES: 'bg-green-100 text-green-800',
  INSCRIPTIONS_FERMEES: 'bg-orange-100 text-orange-800',
  EN_COURS: 'bg-indigo-100 text-indigo-800',
  TERMINE: 'bg-slate-100 text-slate-500',
  ANNULE: 'bg-red-100 text-red-800',
  REFUSE: 'bg-red-100 text-red-800',
  // payments / orders / reservations
  REUSSI: 'bg-green-100 text-green-800',
  PAYEE: 'bg-green-100 text-green-800',
  PAYE: 'bg-green-100 text-green-800',
  CONFIRME: 'bg-green-100 text-green-800',
  CONFIRMEE: 'bg-green-100 text-green-800',
  ECHOUE: 'bg-red-100 text-red-800',
  EXPIRE: 'bg-slate-100 text-slate-500',
  EXPIREE: 'bg-slate-100 text-slate-500',
  ANNULEE: 'bg-red-100 text-red-800',
  REFUSEE: 'bg-red-100 text-red-800',
  REMBOURSE: 'bg-purple-100 text-purple-800',
  RESERVE_TEMP: 'bg-amber-100 text-amber-800',
  ATTENTE_PAIEMENT: 'bg-amber-100 text-amber-800',
  EMISE: 'bg-green-100 text-green-800',
  UTILISE: 'bg-slate-100 text-slate-500',
};

/** Human-readable French labels for status codes (events, orders, payments…). */
const LABELS: Record<string, string> = {
  EN_ATTENTE: 'En attente', VERIFIEE: 'Vérifiée', ACTIF: 'Actif', ACTIF_USER: 'Actif',
  SUSPENDUE: 'Suspendue', SUSPENDU: 'Suspendu', DESACTIVE: 'Désactivé', APPROUVE: 'Approuvé',
  BROUILLON: 'Brouillon', SOUMIS: 'Soumis', VALIDE: 'Validé', REFUSE: 'Refusé', PUBLIE: 'Publié',
  INSCRIPTIONS_OUVERTES: 'Inscriptions ouvertes', INSCRIPTIONS_FERMEES: 'Inscriptions fermées',
  EN_COURS: 'En cours', TERMINE: 'Terminé', ANNULE: 'Annulé',
  REUSSI: 'Réussi', PAYEE: 'Payée', PAYE: 'Payé', CONFIRME: 'Confirmé', CONFIRMEE: 'Confirmée',
  ECHOUE: 'Échoué', EXPIRE: 'Expiré', EXPIREE: 'Expirée', ANNULEE: 'Annulée', REFUSEE: 'Refusée',
  REMBOURSE: 'Remboursé', RESERVE_TEMP: 'Blocage temporaire', ATTENTE_PAIEMENT: 'Attente de paiement',
  EMISE: 'Valide', UTILISE: 'Utilisé', DISPONIBLE: 'Disponible', RESERVE: 'Réservé', OCCUPE: 'Occupé',
  EN_ATTENTE_PAIEMENT: 'Attente de paiement', INITIE: 'Initié', REVOQUE: 'Révoqué', ACTIVE: 'Active',
};

/** French label of a status code; unknown codes are prettified ("A_B" → "A b"). */
export function statutLabel(code: string | null | undefined): string {
  if (!code) return '—';
  const known = LABELS[code];
  if (known) return known;
  const s = code.replace(/_/g, ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span class="badge {{ style }}">{{ label() }}</span>`,
})
export class StatusBadgeComponent {
  value = input.required<string>();

  get style(): string {
    return STYLES[this.value()] ?? 'bg-slate-100 text-slate-700';
  }

  label(): string {
    return statutLabel(this.value());
  }
}
