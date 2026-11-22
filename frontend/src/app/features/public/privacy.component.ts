import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Legal notice and privacy policy (loi n°001-2021/AN du 30 mars 2021).
 * The wording must be validated by the ministry's legal team before production.
 */
@Component({
  selector: 'app-privacy',
  standalone: true,
  imports: [RouterLink],
  template: `
    <article class="card mx-auto max-w-3xl p-6 text-sm leading-relaxed text-slate-700">
      <h1 class="text-2xl font-bold text-slate-900">Confidentialité et mentions légales</h1>

      <h2 class="mt-6 text-lg font-semibold text-slate-800">Responsable du traitement</h2>
      <p class="mt-2">
        La Plateforme Nationale des Événements (PNE) est opérée par le Ministère de la Transition
        Digitale, des Postes et des Communications Électroniques (MTDPCE), Burkina Faso.
        Le traitement est déclaré auprès de la Commission de l'Informatique et des Libertés (CIL).
      </p>

      <h2 class="mt-6 text-lg font-semibold text-slate-800">Données collectées</h2>
      <ul class="mt-2 list-disc space-y-1 pl-5">
        <li>Identité : nom et/ou prénom, selon l'événement.</li>
        <li>Coordonnées : numéro de téléphone, adresse e-mail (facultative).</li>
        <li>Participation : billets, sessions choisies, heures d'entrée et de sortie lors des contrôles.</li>
        <li>Pour les comptes : journal de connexion (date, adresse IP) à des fins de sécurité.</li>
      </ul>

      <h2 class="mt-6 text-lg font-semibold text-slate-800">Finalités</h2>
      <p class="mt-2">
        Émettre vos billets, contrôler l'accès aux sessions, vous informer d'un changement de
        programme et produire des statistiques de fréquentation anonymes. Vos données ne sont
        ni vendues ni utilisées à des fins commerciales.
      </p>

      <h2 class="mt-6 text-lg font-semibold text-slate-800">Durée de conservation</h2>
      <p class="mt-2">
        Les données des participants sont anonymisées automatiquement 12 mois après la fin de
        l'événement ; seules les statistiques agrégées sont conservées.
      </p>

      <h2 class="mt-6 text-lg font-semibold text-slate-800">Vos droits</h2>
      <p class="mt-2">
        Conformément à la loi n°001-2021/AN du 30 mars 2021 portant protection des personnes à
        l'égard du traitement des données à caractère personnel, vous disposez d'un droit d'accès,
        de rectification, d'opposition et de suppression. Adressez votre demande à l'organisateur
        de l'événement ou au MTDPCE. Vous pouvez également saisir la CIL.
      </p>

      <h2 class="mt-6 text-lg font-semibold text-slate-800">Sécurité</h2>
      <p class="mt-2">
        Les échanges sont chiffrés (HTTPS), les accès d'administration sont nominatifs, protégés par
        double authentification, et journalisés.
      </p>

      <p class="mt-8"><a routerLink="/" class="text-brand-700">← Retour aux événements</a></p>
    </article>
  `,
})
export class PrivacyComponent {}
