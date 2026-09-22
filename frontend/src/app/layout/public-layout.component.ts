import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    <div class="flex min-h-full flex-col overflow-x-hidden">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3">
          <a routerLink="/" class="flex shrink-0 items-center">
            <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
                 class="h-10 w-auto" />
          </a>
          <!-- overflow-x-auto : sur un écran étroit, cette barre défile elle-même
               horizontalement au lieu de forcer toute la page à déborder. -->
          <nav class="flex min-w-0 items-center gap-1 overflow-x-auto whitespace-nowrap text-sm font-medium">
            <a routerLink="/" routerLinkActive="text-brand-700"
               [routerLinkActiveOptions]="{ exact: true }" class="btn-ghost">Événements</a>
            <a routerLink="/scanner" routerLinkActive="text-brand-700"
               class="btn-ghost inline-flex items-center gap-1.5">
              <app-icon name="scan" class="h-4 w-4" /> Scanner
            </a>
            @if (!auth.isAuthenticated()) {
              <a routerLink="/retrouver-billet" routerLinkActive="text-brand-700"
                 class="btn-ghost inline-flex items-center gap-1.5">
                <app-icon name="ticket" class="h-4 w-4" /> Retrouver mon billet
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
          </nav>
        </div>
      </header>

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
            <!-- Visible même déconnecté : redirige vers la connexion puis revient
                 ici (voir authGuard), pratique pour le personnel de contrôle. -->
            <a routerLink="/tableau-de-bord/admin/flux" class="hover:text-brand-700">Présence / Flux</a>
          </nav>
        </div>
      </footer>
    </div>
  `,
})
export class PublicLayoutComponent {
  auth = inject(AuthService);
  year = new Date().getFullYear();
}
