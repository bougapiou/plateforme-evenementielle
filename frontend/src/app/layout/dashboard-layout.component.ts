import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { NotificationBellComponent } from '../features/notifications/notification-bell.component';
import { IconComponent, IconName } from '../shared/icon.component';

interface NavItem {
  label: string;
  path: string;
  icon: IconName;
  permission?: string;
  exact?: boolean;
  /** Hidden for guest checkout sessions (no real account yet). */
  fullAccount?: boolean;
}

@Component({
  selector: 'app-dashboard-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationBellComponent, IconComponent],
  template: `
    <div class="flex min-h-full overflow-x-hidden">
      <aside class="hidden w-64 shrink-0 border-r border-slate-200 bg-white md:block">
        <a routerLink="/" class="flex h-16 items-center gap-2 border-b border-slate-200 px-5">
          <img src="assets/logo-192.png" alt="PNE" class="h-8 w-8" />
          <span class="font-extrabold text-brand-700">Espace</span>
        </a>
        <nav class="space-y-1 p-3 text-sm font-medium">
          @for (item of visibleNav(); track item.path) {
            <a [routerLink]="item.path" routerLinkActive="bg-brand-50 text-brand-700"
               [routerLinkActiveOptions]="{ exact: !!item.exact }"
               class="flex items-center gap-2.5 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100">
              <app-icon [name]="item.icon" class="h-4 w-4 shrink-0 text-slate-400" />
              {{ item.label }}
            </a>
          }
          @if (hasAdminSection()) {
            <p class="px-3 pb-1 pt-4 text-xs font-semibold uppercase tracking-wide text-slate-400">
              Administration
            </p>
            @for (item of visibleAdminNav(); track item.path) {
              <a [routerLink]="item.path" routerLinkActive="bg-brand-50 text-brand-700"
                 class="flex items-center gap-2.5 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100">
                <app-icon [name]="item.icon" class="h-4 w-4 shrink-0 text-slate-400" />
                {{ item.label }}
              </a>
            }
          }
        </nav>
      </aside>

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="flex h-16 items-center justify-between gap-3 border-b border-slate-200 bg-white px-5">
          <a routerLink="/" class="shrink-0 text-sm text-slate-500 hover:text-slate-700">← Retour au site</a>
          <!-- overflow-x-auto : sur un écran étroit, ce bloc défile lui-même
               horizontalement au lieu de forcer toute la page à déborder. -->
          <div class="flex min-w-0 items-center gap-3 overflow-x-auto whitespace-nowrap text-sm">
            <app-notification-bell />
            <span class="font-medium text-slate-700">{{ auth.user()?.fullName }}</span>
            @if (auth.isGuest()) {
              <span class="badge bg-amber-100 text-amber-800">Mode invité</span>
              <a routerLink="/finaliser-compte" class="btn-primary">Créer mon compte</a>
              <button type="button" routerLink="/" class="btn-ghost" (click)="auth.logout()">Quitter</button>
            } @else {
              <span class="badge bg-slate-100 text-slate-600">{{ auth.user()?.roles?.join(', ') }}</span>
              <button routerLink="/" type="button" class="btn-ghost" (click)="auth.logout()">Déconnexion</button>
            }
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
    { label: 'Vue d’ensemble', path: '/tableau-de-bord', icon: 'home', exact: true },
    { label: 'Mes événements', path: '/tableau-de-bord/evenements', icon: 'calendar', permission: 'EVENT_CREATE' },
    { label: 'Contrôle à l’entrée', path: '/tableau-de-bord/controle', icon: 'scan', permission: 'CHECKIN_SCAN' },
    { label: 'Présence / Flux', path: '/tableau-de-bord/presence', icon: 'present', permission: 'CHECKIN_SCAN', exact: true },
    { label: 'Détail des billets', path: '/tableau-de-bord/presence/billets', icon: 'qr', permission: 'CHECKIN_SCAN' },
    { label: 'Mes inscriptions', path: '/tableau-de-bord/inscriptions', icon: 'check' },
    { label: 'Mes billets', path: '/tableau-de-bord/billets', icon: 'ticket' },
    { label: 'Mes stands', path: '/tableau-de-bord/stands', icon: 'building' },
    { label: 'Mes paiements', path: '/tableau-de-bord/paiements', icon: 'card' },
    { label: 'Mes factures', path: '/tableau-de-bord/factures', icon: 'chart' },
    { label: 'Mes structures', path: '/tableau-de-bord/structures', icon: 'building', fullAccount: true },
    { label: 'Espace organisateur', path: '/tableau-de-bord/organisateur', icon: 'users', fullAccount: true },
  ];

  private readonly adminNav: NavItem[] = [
    { label: 'Événements', path: '/tableau-de-bord/admin/evenements', icon: 'calendar', permission: 'EVENT_VALIDATE' },
    { label: 'Utilisateurs', path: '/tableau-de-bord/admin/utilisateurs', icon: 'users', permission: 'USER_READ' },
    { label: 'Structures', path: '/tableau-de-bord/admin/structures', icon: 'building', permission: 'STRUCTURE_READ' },
    { label: 'Organisateurs', path: '/tableau-de-bord/admin/organisateurs', icon: 'badge', permission: 'ORGANIZER_MANAGE' },
  ];

  visibleNav = computed(() =>
    this.nav.filter(
      (i) =>
        (!i.permission || this.auth.hasPermission(i.permission)) &&
        (!i.fullAccount || !this.auth.isGuest()),
    ),
  );

  visibleAdminNav = computed(() =>
    this.adminNav.filter((i) => !i.permission || this.auth.hasPermission(i.permission)),
  );

  hasAdminSection = computed(() => this.visibleAdminNav().length > 0);
}
