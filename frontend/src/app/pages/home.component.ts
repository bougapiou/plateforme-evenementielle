import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="card overflow-hidden">
      <div class="bg-gradient-to-br from-brand-700 to-brand-900 px-8 py-14 text-white">
        <h1 class="max-w-2xl text-3xl font-extrabold sm:text-4xl">
          Digitalisez la gestion de vos grands événements
        </h1>
        <p class="mt-4 max-w-2xl text-brand-100">
          Inscriptions, billetterie électronique, réservation de stands et paiement en ligne —
          pour le SIAO, le FESPACO, la Semaine du Numérique et tout événement national.
        </p>
        <div class="mt-8 flex flex-wrap gap-3">
          <a routerLink="/inscription" class="btn bg-white text-brand-700 hover:bg-brand-50">
            Créer un compte
          </a>
          <a routerLink="/connexion" class="btn border border-white/40 text-white hover:bg-white/10">
            Se connecter
          </a>
        </div>
      </div>
    </section>

    <section class="mt-8 grid gap-4 sm:grid-cols-3">
      @for (f of features; track f.title) {
        <article class="card p-5">
          <h3 class="font-semibold text-slate-800">{{ f.title }}</h3>
          <p class="mt-1 text-sm text-slate-500">{{ f.text }}</p>
        </article>
      }
    </section>

    <section class="mt-8 card p-6">
      <h2 class="text-lg font-semibold text-slate-800">Événements publiés</h2>
      <p class="mt-2 text-sm text-slate-500">
        Le catalogue public des événements (recherche, filtres par catégorie, ville et date)
        sera disponible avec le module Événements.
      </p>
    </section>
  `,
})
export class HomeComponent {
  features = [
    { title: 'Billetterie électronique', text: 'Catégories, quotas, QR codes et contrôle à l’entrée.' },
    { title: 'Réservation de stands', text: 'Types de stands, plan interactif, blocage temporaire.' },
    { title: 'Paiement en ligne', text: 'FasoArzeka, confirmation par webhook sécurisé.' },
  ];
}
