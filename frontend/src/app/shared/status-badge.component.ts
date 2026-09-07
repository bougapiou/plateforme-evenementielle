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
    return this.value().replace(/_/g, ' ').toLowerCase();
  }
}
