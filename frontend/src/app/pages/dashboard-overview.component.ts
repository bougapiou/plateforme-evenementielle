import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { StatMap, StatsService } from '../features/stats/stats.service';
import { formatFcfa } from '../shared/format';

@Component({
  selector: 'app-dashboard-overview',
  standalone: true,
  imports: [RouterLink],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Bonjour {{ auth.user()?.fullName }}</h1>

    @if (auth.hasPermission('STATS_OWN_READ') && orga()) {
      <section class="mt-6">
        <h2 class="text-sm font-semibold uppercase tracking-wide text-slate-400">Mes événements</h2>
        <div class="mt-2 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div class="card p-4"><p class="stat-label">Événements</p><p class="stat-value">{{ orga()!['evenements'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Billets vendus</p><p class="stat-value">{{ orga()!['billetsVendus'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Inscriptions confirmées</p><p class="stat-value">{{ orga()!['inscriptionsConfirmees'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Stands confirmés</p><p class="stat-value">{{ orga()!['standsConfirmes'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Revenus</p><p class="stat-value">{{ fcfa(num(orga()!['revenus'])) }}</p></div>
          <div class="card p-4"><p class="stat-label">Paiements en attente</p><p class="stat-value">{{ orga()!['paiementsEnAttente'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Billets restants</p><p class="stat-value">{{ orga()!['billetsRestants'] }}</p></div>
        </div>
        <a routerLink="/tableau-de-bord/evenements" class="mt-3 inline-block text-sm text-brand-700">
          Gérer mes événements →
        </a>
      </section>
    }

    @if (auth.hasPermission('STATS_GLOBAL_READ') && admin()) {
      <section class="mt-8">
        <h2 class="text-sm font-semibold uppercase tracking-wide text-slate-400">Plateforme (global)</h2>
        <div class="mt-2 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div class="card p-4"><p class="stat-label">Événements</p><p class="stat-value">{{ admin()!['evenementsTotal'] }}</p></div>
          <div class="card p-4"><p class="stat-label">À valider</p><p class="stat-value">{{ admin()!['evenementsAValider'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Actifs</p><p class="stat-value">{{ admin()!['evenementsActifs'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Terminés</p><p class="stat-value">{{ admin()!['evenementsTermines'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Utilisateurs</p><p class="stat-value">{{ admin()!['utilisateurs'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Structures</p><p class="stat-value">{{ admin()!['structures'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Billets vendus</p><p class="stat-value">{{ admin()!['billetsVendus'] }}</p></div>
          <div class="card p-4"><p class="stat-label">Chiffre d'affaires</p><p class="stat-value">{{ fcfa(num(admin()!['chiffreAffaires'])) }}</p></div>
        </div>
        <a routerLink="/tableau-de-bord/admin/evenements" class="mt-3 inline-block text-sm text-brand-700">
          Superviser les événements →
        </a>
      </section>
    }

    <section class="mt-8 card p-5">
      <h2 class="font-semibold text-slate-800">Mon profil</h2>
      <dl class="mt-3 grid gap-2 text-sm sm:grid-cols-2">
        <div><dt class="text-slate-400">E-mail</dt><dd>{{ auth.user()?.email }}</dd></div>
        <div><dt class="text-slate-400">Type</dt><dd>{{ auth.user()?.type }}</dd></div>
        <div class="sm:col-span-2"><dt class="text-slate-400">Rôles</dt><dd>{{ auth.user()?.roles?.join(', ') }}</dd></div>
      </dl>
    </section>
  `,
  styles: [
    `.stat-label { @apply text-xs font-medium uppercase tracking-wide text-slate-400; }
     .stat-value { @apply mt-2 text-2xl font-bold text-slate-800; }`,
  ],
})
export class DashboardOverviewComponent {
  auth = inject(AuthService);
  private stats = inject(StatsService);
  orga = signal<StatMap | null>(null);
  admin = signal<StatMap | null>(null);

  fcfa = (n: number) => formatFcfa(n);
  num = (v: number | string) => (typeof v === 'number' ? v : Number(v));

  constructor() {
    if (this.auth.hasPermission('STATS_OWN_READ')) {
      this.stats.organizerOverview().subscribe({ next: (s) => this.orga.set(s), error: () => {} });
    }
    if (this.auth.hasPermission('STATS_GLOBAL_READ')) {
      this.stats.adminOverview().subscribe({ next: (s) => this.admin.set(s), error: () => {} });
    }
  }
}
