import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="mx-auto max-w-md py-16 text-center">
      <p class="text-5xl font-extrabold text-brand-700">404</p>
      <h1 class="mt-4 text-xl font-bold text-slate-800">Page introuvable</h1>
      <p class="mt-2 text-sm text-slate-500">La page demandée n’existe pas ou a été déplacée.</p>
      <a routerLink="/" class="btn-primary mt-6">Retour à l’accueil</a>
    </div>
  `,
})
export class NotFoundComponent {}
