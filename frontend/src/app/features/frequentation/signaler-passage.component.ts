import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/icon.component';
import { FrequentationService } from './frequentation.service';
import { StandInfo } from './frequentation.models';

/**
 * Page publique propre à UN stand (un lien/QR par stand, affiché sur place) : le visiteur y
 * signale son passage en un clic, sans compte, identité facultative. Indépendant du contrôle
 * d'accès par billet et des capteurs physiques — ceci compte la fréquentation par stand, pas
 * l'entrée à l'événement.
 */
@Component({
  selector: 'app-signaler-passage',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="mx-auto max-w-md">
      @if (notFound()) {
        <div class="card p-6 text-center">
          <app-icon name="x" class="mx-auto h-8 w-8 text-slate-300" />
          <p class="mt-2 font-medium text-slate-700">Stand introuvable</p>
          <p class="mt-1 text-sm text-slate-500">
            Ce lien ne correspond à aucun stand publié.
          </p>
        </div>
      } @else if (stand() === null) {
        <p class="mt-6 text-center text-sm text-slate-500">Chargement…</p>
      } @else {
        @if (stand(); as s) {
          <div class="card p-6 text-center">
            <app-icon name="building" class="mx-auto h-8 w-8 text-brand-600" />
            <p class="mt-3 text-xs font-semibold uppercase tracking-widest text-slate-400">
              {{ s.eventNom }}
            </p>
            <h1 class="mt-1 text-xl font-bold text-slate-800">Stand {{ s.numero }}</h1>
            <p class="mt-1 text-sm text-slate-500">
              {{ s.standTypeNom }}{{ s.exposantNom ? ' · ' + s.exposantNom : '' }}
            </p>
          </div>

          @if (envoye()) {
            <div class="card mt-4 p-6 text-center">
              <app-icon name="check" class="mx-auto h-8 w-8 text-green-600" />
              <p class="mt-2 font-medium text-slate-800">Passage enregistré, merci !</p>
              <a [routerLink]="['/evenements', s.eventSlug, 'pointage']"
                 class="mt-4 inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
                <app-icon name="users" class="h-4 w-4" /> Voir la fréquentation des stands
              </a>
              <p class="mt-3">
                <button type="button" class="btn-ghost text-sm" (click)="envoye.set(false)">
                  Signaler un autre passage
                </button>
              </p>
            </div>
          } @else {
            <form class="card mt-4 space-y-3 p-5" (ngSubmit)="submit(s)">
              <p class="text-sm text-slate-500">
                Un simple clic suffit. Votre nom et votre téléphone sont facultatifs.
              </p>
              <div>
                <label class="form-label">Prénom</label>
                <input class="form-input" [(ngModel)]="prenom" name="prenom" autocomplete="given-name" />
              </div>
              <div>
                <label class="form-label">Nom</label>
                <input class="form-input" [(ngModel)]="nom" name="nom" autocomplete="family-name" />
              </div>
              <div>
                <label class="form-label">Téléphone</label>
                <input class="form-input" type="tel" [(ngModel)]="telephone" name="telephone"
                       autocomplete="tel" placeholder="70 00 00 00" />
              </div>
              @if (error()) {
                <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
              }
              <button type="submit" class="btn-primary w-full" [disabled]="submitting()">
                {{ submitting() ? 'Envoi…' : 'Signaler mon passage' }}
              </button>
            </form>
          }
        }
      }
    </div>
  `,
})
export class SignalerPassageComponent {
  private service = inject(FrequentationService);

  slug = input.required<string>();
  standId = input.required<string>();

  stand = signal<StandInfo | null>(null);
  notFound = signal(false);
  envoye = signal(false);
  submitting = signal(false);
  error = signal<string | null>(null);

  nom = '';
  prenom = '';
  telephone = '';

  constructor() {
    effect(() => {
      this.service.standInfo(this.slug(), this.standId()).subscribe({
        next: (s) => this.stand.set(s),
        error: () => this.notFound.set(true),
      });
    });
  }

  submit(s: StandInfo): void {
    this.error.set(null);
    this.submitting.set(true);
    this.service
      .signaler(s.eventSlug, s.id, {
        nom: this.nom.trim() || undefined,
        prenom: this.prenom.trim() || undefined,
        telephone: this.telephone.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.envoye.set(true);
        },
        error: () => {
          this.submitting.set(false);
          this.error.set('Numéro de téléphone invalide, ou passage impossible à enregistrer.');
        },
      });
  }
}
