import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

interface NavItem {
  label: string;
  path: string;
  permission?: string;
  exact?: boolean;
}

@Component({
  selector: 'app-dashboard-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="flex min-h-full">
      <aside class="hidden w-64 shrink-0 border-r border-slate-200 bg-white md:block">
        <div class="flex h-16 items-center gap-2 border-b border-slate-200 px-5 font-extrabold text-brand-700">
          <span class="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 text-white text-xs">PN</span>
          Espace
        </div>
        <nav class="space-y-1 p-3 text-sm font-medium">
          @for (item of visibleNav(); track item.path) {
            <a [routerLink]="item.path" routerLinkActive="bg-brand-50 text-brand-700"
               [routerLinkActiveOptions]="{ exact: !!item.exact }"
               class="block rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100">
              {{ item.label }}
            </a>
          }
          @if (hasAdminSection()) {
            <p class="px-3 pb-1 pt-4 text-xs font-semibold uppercase tracking-wide text-slate-400">
              Administration
            </p>
            @for (item of visibleAdminNav(); track item.path) {
              <a [routerLink]="item.path" routerLinkActive="bg-brand-50 text-brand-700"
                 class="block rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100">
                {{ item.label }}
              </a>
            }
          }
        </nav>
      </aside>

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-5">
          <a routerLink="/" class="text-sm text-slate-500 hover:text-slate-700">← Retour au site</a>
          <div class="flex items-center gap-3 text-sm">
            <span class="font-medium text-slate-700">{{ auth.user()?.fullName }}</span>
            <span class="badge bg-slate-100 text-slate-600">{{ auth.user()?.roles?.join(', ') }}</span>
            <button type="button" class="btn-ghost" (click)="auth.logout()">Déconnexion</button>
          </div>
        </header>
        <main class="flex-1 bg-slate-50 p-5"><router-outlet /></main>
      </div>
    </div>
  `,
})
export class DashboardLayoutComponent {
  auth = inject(AuthService);

  private readonly nav: NavItem[] = [
    { label: 'Vue d’ensemble', path: '/tableau-de-bord', exact: true },
    { label: 'Mes événements', path: '/tableau-de-bord/evenements', permission: 'EVENT_CREATE' },
    { label: 'Contrôle à l’entrée', path: '/tableau-de-bord/controle', permission: 'CHECKIN_SCAN' },
    { label: 'Mes inscriptions', path: '/tableau-de-bord/inscriptions' },
    { label: 'Mes billets', path: '/tableau-de-bord/billets' },
    { label: 'Mes stands', path: '/tableau-de-bord/stands' },
    { label: 'Mes paiements', path: '/tableau-de-bord/paiements' },
    { label: 'Mes factures', path: '/tableau-de-bord/factures' },
    { label: 'Mes structures', path: '/tableau-de-bord/structures' },
    { label: 'Espace organisateur', path: '/tableau-de-bord/organisateur' },
  ];

  private readonly adminNav: NavItem[] = [
    { label: 'Événements', path: '/tableau-de-bord/admin/evenements', permission: 'EVENT_VALIDATE' },
    { label: 'Utilisateurs', path: '/tableau-de-bord/admin/utilisateurs', permission: 'USER_READ' },
    { label: 'Structures', path: '/tableau-de-bord/admin/structures', permission: 'STRUCTURE_READ' },
    { label: 'Organisateurs', path: '/tableau-de-bord/admin/organisateurs', permission: 'ORGANIZER_MANAGE' },
  ];

  visibleNav = computed(() =>
    this.nav.filter((i) => !i.permission || this.auth.hasPermission(i.permission)),
  );

  visibleAdminNav = computed(() =>
    this.adminNav.filter((i) => !i.permission || this.auth.hasPermission(i.permission)),
  );

  hasAdminSection = computed(() => this.visibleAdminNav().length > 0);
}
