import { Component, inject } from '@angular/core';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-dashboard-overview',
  standalone: true,
  template: `
    <h1 class="text-xl font-bold text-slate-800">Bonjour {{ auth.user()?.fullName }}</h1>
    <p class="mt-1 text-sm text-slate-500">
      Bienvenue sur votre espace. Les indicateurs et modules apparaîtront ici au fur et à mesure
      de la mise en service de la plateforme.
    </p>

    <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      @for (card of cards; track card.label) {
        <div class="card p-4">
          <p class="text-xs font-medium uppercase tracking-wide text-slate-400">{{ card.label }}</p>
          <p class="mt-2 text-2xl font-bold text-slate-800">{{ card.value }}</p>
        </div>
      }
    </div>

    <div class="mt-6 card p-5">
      <h2 class="font-semibold text-slate-800">Votre profil</h2>
      <dl class="mt-3 grid gap-2 text-sm sm:grid-cols-2">
        <div><dt class="text-slate-400">E-mail</dt><dd>{{ auth.user()?.email }}</dd></div>
        <div><dt class="text-slate-400">Type</dt><dd>{{ auth.user()?.type }}</dd></div>
        <div class="sm:col-span-2">
          <dt class="text-slate-400">Rôles</dt>
          <dd>{{ auth.user()?.roles?.join(', ') }}</dd>
        </div>
        <div class="sm:col-span-2">
          <dt class="text-slate-400">Permissions</dt>
          <dd class="flex flex-wrap gap-1">
            @for (p of auth.user()?.permissions ?? []; track p) {
              <span class="badge bg-slate-100 text-slate-600">{{ p }}</span>
            }
          </dd>
        </div>
      </dl>
    </div>
  `,
})
export class DashboardOverviewComponent {
  auth = inject(AuthService);
  cards = [
    { label: 'Événements', value: '—' },
    { label: 'Inscriptions', value: '—' },
    { label: 'Billets vendus', value: '—' },
    { label: 'Revenus (FCFA)', value: '—' },
  ];
}
