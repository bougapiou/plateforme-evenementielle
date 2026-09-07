import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

interface NavItem {
  label: string;
  path: string;
  permission?: string;
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
               [routerLinkActiveOptions]="{ exact: true }"
               class="block rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100">
              {{ item.label }}
            </a>
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
    { label: 'Vue d’ensemble', path: '/tableau-de-bord' },
    { label: 'Événements', path: '/tableau-de-bord/evenements', permission: 'EVENT_READ' },
    { label: 'Utilisateurs', path: '/tableau-de-bord/utilisateurs', permission: 'USER_READ' },
    { label: 'Rôles & permissions', path: '/tableau-de-bord/roles', permission: 'ROLE_MANAGE' },
    { label: 'Journaux', path: '/tableau-de-bord/journaux', permission: 'AUDIT_READ' },
  ];

  visibleNav = computed(() =>
    this.nav.filter((item) => !item.permission || this.auth.hasPermission(item.permission)),
  );
}
