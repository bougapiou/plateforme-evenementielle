import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationBellComponent, IconComponent, NgTemplateOutlet],
  template: `
    <div class="flex min-h-full overflow-x-hidden">
      <!-- Barre latérale (ordinateur / grande tablette) -->
      <aside class="hidden w-64 shrink-0 border-r border-slate-200 bg-white md:block">
        <a routerLink="/" class="flex h-16 items-center gap-2 border-b border-slate-200 px-5">
          <img src="assets/logo-192.png" alt="PNE" class="h-8 w-8" />
          <span class="font-extrabold text-brand-700">Espace</span>
        </a>
        <nav class="space-y-1 p-3 text-sm font-medium">
          <ng-container *ngTemplateOutlet="navLinks" />
        </nav>
      </aside>

      <!-- Tiroir de navigation (mobile) -->
      @if (drawerOpen()) {
        <div class="fixed inset-0 z-40 md:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <div class="absolute inset-0 bg-slate-900/50" (click)="drawerOpen.set(false)"></div>
          <aside class="absolute inset-y-0 left-0 flex w-72 max-w-[85%] flex-col overflow-y-auto bg-white shadow-xl">
            <div class="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 px-5">
              <span class="flex items-center gap-2">
                <img src="assets/logo-192.png" alt="PNE" class="h-8 w-8" />
                <span class="font-extrabold text-brand-700">Espace</span>
              </span>
              <button type="button" class="btn-ghost -mr-2" aria-label="Fermer le menu"
                      (click)="drawerOpen.set(false)">
                <app-icon name="x" class="h-5 w-5" />
              </button>
            </div>
            <nav class="space-y-1 p-3 text-sm font-medium" (click)="drawerOpen.set(false)">
              <ng-container *ngTemplateOutlet="navLinks" />
            </nav>
          </aside>
        </div>
      }

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="flex h-16 items-center justify-between gap-3 border-b border-slate-200 bg-white px-3 sm:px-5">
          <div class="flex min-w-0 items-center gap-2">
            <button type="button" class="btn-ghost -ml-1 md:hidden" aria-label="Ouvrir le menu"
                    aria-haspopup="dialog" (click)="drawerOpen.set(true)">
              <app-icon name="menu" class="h-6 w-6" />
              <span class="ml-1 text-sm font-medium">Menu</span>
            </button>
            <a routerLink="/" class="hidden shrink-0 text-sm text-slate-500 hover:text-slate-700 sm:block">
              ← Retour au site
            </a>
          </div>
          <div class="flex min-w-0 items-center gap-2 overflow-x-auto whitespace-nowrap text-sm sm:gap-3">
            <app-notification-bell />
            <span class="hidden font-medium text-slate-700 sm:inline">{{ auth.user()?.fullName }}</span>
            @if (auth.isGuest()) {
              <span class="badge hidden bg-amber-100 text-amber-800 sm:inline-flex">Mode invité</span>
              <a routerLink="/finaliser-compte" class="btn-primary">Créer mon compte</a>
              <button type="button" routerLink="/" class="btn-ghost" (click)="auth.logout()">Quitter</button>
            } @else {
              <span class="badge hidden bg-slate-100 text-slate-600 lg:inline-flex">{{ auth.user()?.roles?.join(', ') }}</span>
              <button routerLink="/" type="button" class="btn-ghost" (click)="auth.logout()">Déconnexion</button>
            }
          </div>
        </header>
        <main class="flex-1 bg-slate-50 p-3 sm:p-5"><router-outlet /></main>
      </div>
    </div>

    <ng-template #navLinks>
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
    </ng-template>
  `,
})
export class DashboardLayoutComponent {
  auth = inject(AuthService);
  drawerOpen = signal(false);

  private readonly nav: NavItem[] = [
    { label: 'Vue d’ensemble', path: '/tableau-de-bord', icon: 'home', exact: true },
    { label: 'Mes événements', path: '/tableau-de-bord/evenements', icon: 'calendar', permission: 'EVENT_CREATE' },
    { label: 'Contrôle à la porte', path: '/tableau-de-bord/controle', icon: 'scan', permission: 'CHECKIN_SCAN' },
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
