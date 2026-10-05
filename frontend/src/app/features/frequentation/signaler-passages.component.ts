import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/icon.component';
import { StandsService } from '../stands/stands.service';
import { Stand } from '../stands/stand.models';
import { FrequentationService } from './frequentation.service';

type Step = 'select' | 'form' | 'done';

/**
 * Page publique : le visiteur coche, parmi tous les stands de l'événement, ceux qu'il a visités
 * (ou "Tout sélectionner"), valide, puis donne une identité facultative une seule fois pour
 * l'ensemble. Complémentaire du lien/QR propre à chaque stand (SignalerPassageComponent) — même
 * résultat (une ligne de passage par stand), en un seul passage pour un visiteur qui fait le tour.
 */
@Component({
  selector: 'app-signaler-passages',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="mx-auto max-w-md">
      @if (notFound()) {
        <div class="card p-6 text-center">
          <app-icon name="x" class="mx-auto h-8 w-8 text-slate-300" />
          <p class="mt-2 font-medium text-slate-700">Événement introuvable</p>
        </div>
      } @else if (stands() === null) {
        <p class="mt-6 text-center text-sm text-slate-500">Chargement…</p>
      } @else {
        @if (stands(); as list) {
          @switch (step()) {
            @case ('select') {
              <div class="flex items-center justify-between gap-2">
                <div>
                  <h1 class="text-xl font-bold text-slate-800">Stands visités</h1>
                  <p class="text-sm font-medium text-brand-700">
                    {{ selected().size }} / {{ list.length }} sélectionné{{ selected().size > 1 ? 's' : '' }}
                  </p>
                </div>
                <button type="button" class="btn-ghost text-sm whitespace-nowrap" (click)="toggleAll()">
                  {{ allSelected() ? 'Tout désélectionner' : 'Tout sélectionner' }}
                </button>
              </div>
              <p class="mt-1 text-sm text-slate-500">
                Cochez les stands que vous avez visités, puis validez.
              </p>

              <div class="mt-4 space-y-2">
                @for (s of list; track s.id) {
                  <button type="button"
                          class="card flex w-full items-center gap-3 p-3 text-left transition-all duration-150"
                          [class]="selected().has(s.id)
                            ? 'border-brand-500 bg-brand-50 shadow-sm ring-1 ring-brand-200'
                            : 'hover:border-slate-300'"
                          (click)="toggle(s.id)">
                    <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 transition-colors duration-150"
                          [class]="selected().has(s.id)
                            ? 'border-brand-600 bg-brand-600 text-white scale-110'
                            : 'border-slate-300 text-transparent'">
                      <app-icon name="check" class="h-3.5 w-3.5" />
                    </span>
                    <span class="font-medium"
                          [class]="selected().has(s.id) ? 'text-brand-800' : 'text-slate-700'">
                      Stand {{ s.numero }} · {{ s.standTypeNom }}
                    </span>
                  </button>
                } @empty {
                  <p class="text-sm text-slate-400">Aucun stand pour cet événement.</p>
                }
              </div>

              <button type="button" class="btn-primary mt-4 w-full transition-opacity duration-150"
                      [disabled]="selected().size === 0" (click)="proceed()">
                Valider ({{ selected().size }} sélectionné{{ selected().size > 1 ? 's' : '' }})
              </button>
            }
            @case ('form') {
              <button type="button" class="text-sm text-slate-500 hover:underline" (click)="back()">
                ← Retour à la sélection
              </button>
              <h1 class="mt-2 text-xl font-bold text-slate-800">Vos informations</h1>
              <p class="mt-1 text-sm text-slate-500">
                {{ selected().size }} stand{{ selected().size > 1 ? 's' : '' }} sélectionné{{ selected().size > 1 ? 's' : '' }}.
                Nom, prénom et téléphone sont facultatifs.
              </p>
              <form class="card mt-4 space-y-3 p-5" (ngSubmit)="submit()">
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
                  {{ submitting() ? 'Envoi…' : 'Confirmer mon passage' }}
                </button>
              </form>
            }
            @case ('done') {
              <div class="card p-6 text-center">
                <app-icon name="check" class="mx-auto h-8 w-8 text-green-600" />
                <p class="mt-2 font-medium text-slate-800">
                  Passage enregistré sur {{ enregistres() }} stand{{ enregistres() > 1 ? 's' : '' }}, merci !
                </p>
                <a [routerLink]="['/evenements', slug(), 'pointage']"
                   class="mt-4 inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
                  <app-icon name="users" class="h-4 w-4" /> Voir la fréquentation des stands
                </a>
                <p class="mt-3">
                  <button type="button" class="btn-ghost text-sm" (click)="resetAll()">
                    Signaler d'autres passages
                  </button>
                </p>
              </div>
            }
          }
        }
      }
    </div>
  `,
})
export class SignalerPassagesComponent {
  private standsService = inject(StandsService);
  private service = inject(FrequentationService);

  slug = input.required<string>();

  stands = signal<Stand[] | null>(null);
  notFound = signal(false);
  selected = signal<Set<string>>(new Set());
  step = signal<Step>('select');
  submitting = signal(false);
  error = signal<string | null>(null);
  enregistres = signal(0);

  nom = '';
  prenom = '';
  telephone = '';

  allSelected = computed(() => {
    const list = this.stands();
    return !!list && list.length > 0 && this.selected().size === list.length;
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      this.stands.set(null);
      this.notFound.set(false);
      if (!slug) return;
      this.standsService.publicStands(slug).subscribe({
        next: (s) => this.stands.set(s),
        error: () => this.notFound.set(true),
      });
    });
  }

  toggle(standId: string): void {
    const next = new Set(this.selected());
    next.has(standId) ? next.delete(standId) : next.add(standId);
    this.selected.set(next);
  }

  toggleAll(): void {
    const list = this.stands();
    if (!list) return;
    this.selected.set(this.allSelected() ? new Set() : new Set(list.map((s) => s.id)));
  }

  proceed(): void {
    if (this.selected().size === 0) return;
    this.step.set('form');
  }

  back(): void {
    this.step.set('select');
  }

  submit(): void {
    this.error.set(null);
    this.submitting.set(true);
    this.service
      .signalerPlusieurs(this.slug(), {
        standIds: [...this.selected()],
        nom: this.nom.trim() || undefined,
        prenom: this.prenom.trim() || undefined,
        telephone: this.telephone.trim() || undefined,
      })
      .subscribe({
        next: (r) => {
          this.submitting.set(false);
          this.enregistres.set(r.enregistres);
          this.step.set('done');
        },
        error: () => {
          this.submitting.set(false);
          this.error.set('Numéro de téléphone invalide, ou passage impossible à enregistrer.');
        },
      });
  }

  resetAll(): void {
    this.selected.set(new Set());
    this.nom = '';
    this.prenom = '';
    this.telephone = '';
    this.step.set('select');
  }
}
