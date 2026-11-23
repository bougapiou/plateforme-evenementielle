import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { IconComponent } from '../shared/icon.component';

/**
 * Gabarit de l'espace public, conforme à la Charte graphique de l'administration burkinabè v2.0 :
 * barre de service, marque PNE (logo + « Plateforme Nationale des Événements »),
 * navigation principale, filet national sous l'en-tête, pied de page institutionnel.
 */
@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, NgTemplateOutlet],
  template: `
    <div class="flex min-h-full flex-col overflow-x-hidden bg-neutre-25">
      <a href="#contenu"
         class="sr-only z-[600] rounded-md bg-white px-4 py-2 font-semibold text-vert-700 focus:not-sr-only focus:fixed focus:left-4 focus:top-4">
        Aller au contenu
      </a>

      <header class="relative z-[200] bg-white">
        <!-- Marque + navigation -->
        <div class="mx-auto flex max-w-conteneur items-center justify-between gap-2 px-4 py-3 min-[400px]:gap-4 sm:px-6">
          <a routerLink="/" class="group flex min-w-0 shrink items-center gap-2 py-1 min-[400px]:gap-2.5 sm:gap-3" (click)="menuOpen.set(false)"
             aria-label="PNE — Plateforme Nationale des Événements, retour à l'accueil">
            <img src="assets/logo-pne-mark.png" alt="" class="h-6 w-auto shrink-0 min-[400px]:h-8 sm:h-12" width="133" height="48" />
            <span class="flex min-w-0 flex-col border-l border-neutre-300 pl-2 font-display leading-tight min-[400px]:pl-2.5 sm:pl-3">
              <span class="text-[0.75rem] font-extrabold text-neutre-900 group-hover:text-vert-700 min-[400px]:whitespace-nowrap min-[400px]:text-[0.9375rem] sm:text-[1.0625rem]">
                Plateforme Nationale
              </span>
              <span class="whitespace-nowrap text-[0.6875rem] font-medium text-neutre-700 min-[400px]:text-[0.8125rem] sm:text-[0.9375rem]">des Événements</span>
            </span>
          </a>

          <!-- Grand écran : menu horizontal -->
          <nav aria-label="Navigation principale" class="hidden items-center gap-1 whitespace-nowrap lg:flex">
            <ng-container *ngTemplateOutlet="links" />
          </nav>

          <!-- Mobile / tablette : bouton du menu (« Se connecter » est dans le menu) -->
          <button type="button" class="btn-ghost -mr-2 shrink-0 lg:hidden" [attr.aria-label]="menuOpen() ? 'Fermer le menu' : 'Ouvrir le menu'"
                  aria-controls="menu-mobile" [attr.aria-expanded]="menuOpen()"
                  (click)="menuOpen.set(!menuOpen())">
            <app-icon [name]="menuOpen() ? 'x' : 'menu'" class="h-6 w-6" aria-hidden="true" />
            <span class="text-sm">Menu</span>
          </button>
        </div>

        @if (menuOpen()) {
          <nav id="menu-mobile" aria-label="Navigation principale"
               class="flex flex-col gap-1 border-t border-neutre-200 bg-white px-4 pb-4 pt-2 lg:hidden [&>a]:w-full [&>a]:justify-start [&>button]:w-full [&>button]:justify-start"
               (click)="menuOpen.set(false)">
            <ng-container *ngTemplateOutlet="links; context: { mobile: true }" />
          </nav>
        }

        <!-- Filet national : sous l'en-tête officiel, une seule fois -->
        <div class="fs-filet" role="presentation"></div>
      </header>

      <ng-template #links let-mobile="mobile">
        <a routerLink="/" routerLinkActive="!text-vert-700 !bg-vert-50" ariaCurrentWhenActive="page"
           [routerLinkActiveOptions]="{ exact: true }" class="btn-ghost">
          <app-icon name="calendar" class="h-4 w-4" aria-hidden="true" /> Événements
        </a>
        <a routerLink="/presence-en-direct" routerLinkActive="!text-vert-700 !bg-vert-50" ariaCurrentWhenActive="page"
           class="btn-ghost">
          <app-icon name="present" class="h-4 w-4" aria-hidden="true" /> Présence en direct
        </a>
        <a routerLink="/scanner" routerLinkActive="!text-vert-700 !bg-vert-50" ariaCurrentWhenActive="page"
           class="btn-ghost">
          <app-icon name="scan" class="h-4 w-4" aria-hidden="true" /> Scanner un billet
        </a>
        @if (auth.isGuest()) {
          <a routerLink="/tableau-de-bord/billets" class="btn-ghost">
            <app-icon name="ticket" class="h-4 w-4" aria-hidden="true" /> Mes billets
          </a>
          <a routerLink="/finaliser-compte" class="btn-primary lg:ml-2">Créer mon compte</a>
          <button type="button" class="btn-ghost" (click)="auth.logout()">Quitter</button>
        } @else if (auth.isAuthenticated()) {
          <a routerLink="/tableau-de-bord" class="btn-secondary lg:ml-2">Tableau de bord</a>
          <button type="button" class="btn-ghost" (click)="auth.logout()">Déconnexion</button>
        } @else if (mobile) {
          <!-- Menu mobile : « Se connecter » à la place de « Créer un compte » -->
          <a routerLink="/connexion" class="btn-primary mt-2">
            <app-icon name="login" class="h-4 w-4" aria-hidden="true" /> Se connecter
          </a>
          <p class="pt-2 text-center text-sm text-neutre-600">
            Pas encore de compte ? <a routerLink="/inscription" class="lien">Créer un compte</a>
          </p>
        } @else {
          <!-- Grand écran : « Se connecter » à côté de « Créer un compte » -->
          <a routerLink="/connexion" routerLinkActive="!text-vert-700 !bg-vert-50" ariaCurrentWhenActive="page"
             class="btn-secondary lg:ml-2">
            <app-icon name="login" class="h-4 w-4" aria-hidden="true" /> Se connecter
          </a>
          <a routerLink="/inscription" class="btn-primary">Créer un compte</a>
        }
      </ng-template>

      <main id="contenu" tabindex="-1" class="mx-auto w-full max-w-conteneur flex-1 px-4 py-8 focus:outline-none sm:px-6 sm:py-10">
        <router-outlet />
      </main>

      <footer class="mt-12 bg-neutre-900 text-neutre-200">
        <div class="mx-auto grid max-w-conteneur gap-10 px-4 py-12 sm:px-6 md:grid-cols-[1.6fr_1fr_1fr]">
          <div>
            <a routerLink="/" class="inline-flex items-center gap-3" aria-label="PNE — Plateforme Nationale des Événements, retour à l'accueil">
              <!-- Le logo a un fond transparent et des parties claires : on le pose sur un fond blanc -->
              <span class="flex items-center rounded-md bg-white px-2.5 py-1.5">
                <img src="assets/logo-pne-mark.png" alt="" class="h-9 w-auto" width="100" height="36" />
              </span>
              <span class="flex flex-col font-display leading-tight">
                <span class="text-base font-extrabold text-white">Plateforme Nationale</span>
                <span class="text-sm font-medium text-neutre-300">des Événements</span>
              </span>
            </a>
            <p class="mt-5 max-w-sm text-sm leading-relaxed text-neutre-400">
              Inscriptions, billetterie électronique, réservation de stands, paiement en ligne et
              contrôle d'accès pour les grands événements du Burkina Faso.
            </p>
          </div>

          <nav aria-labelledby="pied-evenements">
            <h2 id="pied-evenements" class="surtitre text-neutre-400">Événements</h2>
            <ul class="mt-4 space-y-2.5 text-sm">
              <li><a routerLink="/" class="hover:text-white hover:underline">Catalogue des événements</a></li>
              <li><a routerLink="/presence-en-direct" class="hover:text-white hover:underline">Présence en direct</a></li>
              <li><a routerLink="/scanner" class="hover:text-white hover:underline">Scanner un billet</a></li>
            </ul>
          </nav>

          <nav aria-labelledby="pied-compte">
            <h2 id="pied-compte" class="surtitre text-neutre-400">Mon espace</h2>
            <ul class="mt-4 space-y-2.5 text-sm">
              @if (!auth.isAuthenticated()) {
                <li><a routerLink="/connexion" class="hover:text-white hover:underline">Se connecter</a></li>
                <li><a routerLink="/inscription" class="hover:text-white hover:underline">Créer un compte</a></li>
                <li><a routerLink="/retrouver-billet" class="hover:text-white hover:underline">Retrouver mon billet</a></li>
              } @else {
                <li><a routerLink="/tableau-de-bord" class="hover:text-white hover:underline">Tableau de bord</a></li>
              }
            </ul>
          </nav>
        </div>

        <div class="border-t border-neutre-700">
          <div class="mx-auto flex max-w-conteneur flex-col gap-2 px-4 py-5 text-xs text-neutre-400 sm:flex-row sm:items-center sm:justify-between sm:px-6">
            <span>© {{ year }} PNE — Plateforme Nationale des Événements</span>
          </div>
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
