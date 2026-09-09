import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="flex min-h-full flex-col">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <a routerLink="/" class="flex items-center">
            <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
                 class="h-10 w-auto" />
          </a>
          <nav class="flex items-center gap-1 text-sm font-medium">
            <a routerLink="/" routerLinkActive="text-brand-700"
               [routerLinkActiveOptions]="{ exact: true }" class="btn-ghost">Événements</a>
            @if (auth.isAuthenticated()) {
              <a routerLink="/tableau-de-bord" class="btn-ghost">Tableau de bord</a>
              <button type="button" class="btn-ghost" (click)="auth.logout()">Déconnexion</button>
            } @else {
              <a routerLink="/connexion" class="btn-ghost">Connexion</a>
              <a routerLink="/inscription" class="btn-primary">Créer un compte</a>
            }
          </nav>
        </div>
      </header>

      <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <router-outlet />
      </main>

      <footer class="border-t border-slate-200 bg-white">
        <div class="mx-auto max-w-6xl px-4 py-6 text-sm text-slate-500">
          © {{ year }} — Plateforme Nationale de Gestion des Événements · Burkina Faso
        </div>
      </footer>
    </div>
  `,
})
export class PublicLayoutComponent {
  auth = inject(AuthService);
  year = new Date().getFullYear();
}
