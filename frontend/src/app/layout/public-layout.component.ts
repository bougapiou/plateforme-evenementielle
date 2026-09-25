import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, NgTemplateOutlet],
  template: `
    <div class="flex min-h-full flex-col overflow-x-hidden">
      <header class="sticky top-0 z-30 border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3">
          <a routerLink="/" class="flex shrink-0 items-center" (click)="menuOpen.set(false)">
            <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
                 class="h-10 w-auto" />
          </a>

          <!-- Grand écran : menu horizontal -->
          <nav class="hidden items-center gap-1 whitespace-nowrap text-sm font-medium lg:flex">
            <ng-container *ngTemplateOutlet="links" />
          </nav>

          <!-- Mobile / tablette : bouton pour ouvrir le menu -->
          <button type="button" class="btn-ghost -mr-2 lg:hidden" aria-label="Ouvrir le menu"
                  aria-controls="menu-mobile" [attr.aria-expanded]="menuOpen()"
                  (click)="menuOpen.set(!menuOpen())">
            <app-icon [name]="menuOpen() ? 'x' : 'menu'" class="h-6 w-6" />
            <span class="ml-1 text-sm font-medium">Menu</span>
          </button>
        </div>

        @if (menuOpen()) {
          <nav id="menu-mobile"
               class="flex flex-col gap-1 border-t border-slate-100 bg-white px-4 pb-4 pt-2 text-sm font-medium lg:hidden [&>*]:w-full [&>*]:justify-start"
               (click)="menuOpen.set(false)">
            <ng-container *ngTemplateOutlet="links" />
          </nav>
        }
      </header>

      <ng-template #links>
        <a routerLink="/" routerLinkActive="text-brand-700"
           [routerLinkActiveOptions]="{ exact: true }" class="btn-ghost inline-flex items-center gap-1.5">
          <app-icon name="calendar" class="h-4 w-4" /> Événements
        </a>
        <a routerLink="/presence-en-direct" routerLinkActive="text-brand-700"
           class="btn-ghost inline-flex items-center gap-1.5">
          <app-icon name="present" class="h-4 w-4" /> Présence en direct
        </a>
        <a routerLink="/scanner" routerLinkActive="text-brand-700"
           class="btn-ghost inline-flex items-center gap-1.5">
          <app-icon name="scan" class="h-4 w-4" /> Scanner
        </a>
        @if (!auth.isAuthenticated()) {
          <a routerLink="/retrouver-billet" routerLinkActive="text-brand-700"
             class="btn-ghost inline-flex items-center gap-1.5">
            <app-icon name="ticket" class="h-4 w-4" /> Retrouver mon billet
          </a>
          <a routerLink="/connexion" routerLinkActive="text-brand-700"
             class="btn-ghost inline-flex items-center gap-1.5">
            <app-icon name="login" class="h-4 w-4" /> Connexion
          </a>
        }
        @if (auth.isGuest()) {
          <a routerLink="/tableau-de-bord" class="btn-ghost">Mes billets</a>
          <a routerLink="/finaliser-compte" class="btn-primary">Créer mon compte</a>
          <button type="button" class="btn-ghost" (click)="auth.logout()">Quitter</button>
        } @else if (auth.isAuthenticated()) {
          <a routerLink="/tableau-de-bord" class="btn-ghost">Tableau de bord</a>
          <button type="button" class="btn-ghost" (click)="auth.logout()">Déconnexion</button>
        }
      </ng-template>

      <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <router-outlet />
      </main>

      <footer class="border-t border-slate-200 bg-white">
        <div class="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-6 text-sm text-slate-500">
          <span>© {{ year }} — Plateforme Nationale de Gestion des Événements · Burkina Faso</span>
          <nav class="flex flex-wrap items-center gap-4">
            @if (!auth.isAuthenticated()) {
              <a routerLink="/connexion" class="hover:text-brand-700">Connexion</a>
              <a routerLink="/inscription" class="hover:text-brand-700">Créer un compte</a>
            }
          </nav>
        </div>
      </footer>
    </div>
  `,
})
export class PublicLayoutComponent {
  auth = inject(AuthService);
  year = new Date().getFullYear();
  menuOpen = signal(false);
}
