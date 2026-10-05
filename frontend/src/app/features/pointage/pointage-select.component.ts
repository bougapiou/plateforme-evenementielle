import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { IconComponent } from '../../shared/icon.component';
import { PointageService } from './pointage.service';
import { PointageStandStat } from './pointage.models';

type Step = 'select' | 'form';
const REFRESH_MS = 8000;

/**
 * Page d'accueil du module Pointage des visiteurs (publique, sans compte) : le visiteur coche
 * tous les stands visités en une fois (ou "Tout sélectionner"), valide, puis donne une identité
 * facultative une seule fois pour l'ensemble. Indépendant des événements.
 */
@Component({
  selector: 'app-pointage-select',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="mx-auto max-w-5xl">
      @if (notFound()) {
        <div class="card p-6 text-center">
          <app-icon name="x" class="mx-auto h-8 w-8 text-slate-300" />
          <p class="mt-2 font-medium text-slate-700">Impossible de charger les stands.</p>
        </div>
      } @else if (stands() === null) {
        <p class="mt-6 text-center text-sm text-slate-500">Chargement…</p>
      } @else {
        @if (stands(); as list) {
          @switch (step()) {
            @case ('select') {
              @if (justSubmitted()) {
                <div class="mb-4 flex items-center justify-between gap-2 rounded-lg bg-green-50 px-4 py-3 text-sm text-green-800">
                  <span class="flex items-center gap-2">
                    <app-icon name="check" class="h-4 w-4" />
                    Passage enregistré sur {{ enregistres() }} stand{{ enregistres() > 1 ? 's' : '' }}, merci !
                  </span>
                  <button type="button" class="text-green-700 hover:underline" (click)="justSubmitted.set(false)">
                    Fermer
                  </button>
                </div>
              }
              <div class="flex items-center justify-between gap-2">
                <div>
                  <h1 class="text-xl font-bold text-slate-800">Pointage des visiteurs</h1>
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

              <div class="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
                @for (s of list; track s.standId) {
                  <button type="button"
                          class="card flex h-full w-full items-start gap-3 p-3 text-left transition-all duration-150"
                          [class]="selected().has(s.standId)
                            ? 'border-brand-500 bg-brand-50 shadow-sm ring-1 ring-brand-200'
                            : 'hover:border-slate-300'"
                          (click)="toggle(s.standId)">
                    <span class="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 transition-colors duration-150"
                          [class]="selected().has(s.standId)
                            ? 'border-brand-600 bg-brand-600 text-white scale-110'
                            : 'border-slate-300 text-transparent'">
                      <app-icon name="check" class="h-3.5 w-3.5" />
                    </span>
                    <span class="min-w-0 flex-1">
                      <span class="block font-medium"
                            [class]="selected().has(s.standId) ? 'text-brand-800' : 'text-slate-700'">
                        {{ s.nom }}
                      </span>
                      <span class="mt-1 block text-xs text-slate-400">
                        {{ s.visites }} passage{{ s.visites > 1 ? 's' : '' }}
                      </span>
                    </span>
                  </button>
                } @empty {
                  <p class="text-sm text-slate-400">Aucun stand pour le moment.</p>
                }
              </div>

              <div class="mx-auto mt-4 max-w-md">
                <button type="button" class="btn-primary w-full"
                        [disabled]="selected().size === 0" (click)="proceed()">
                  Valider ({{ selected().size }} sélectionné{{ selected().size > 1 ? 's' : '' }})
                </button>

                <a routerLink="/pointage-visiteurs/stats"
                   class="btn-ghost mt-3 flex items-center justify-center gap-1.5 border border-slate-200">
                  <app-icon name="chart" class="h-4 w-4" /> Voir les statistiques
                </a>
              </div>
            }
            @case ('form') {
              <div class="mx-auto max-w-md">
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
              </div>
            }
          }
        }
      }
    </div>
  `,
})
export class PointageSelectComponent implements OnDestroy {
  private service = inject(PointageService);

  stands = signal<PointageStandStat[] | null>(null);
  notFound = signal(false);
  selected = signal<Set<string>>(new Set());
  step = signal<Step>('select');
  submitting = signal(false);
  error = signal<string | null>(null);
  enregistres = signal(0);
  justSubmitted = signal(false);
  REFRESH_MS = REFRESH_MS;

  nom = '';
  prenom = '';
  telephone = '';

  allSelected = computed(() => {
    const list = this.stands();
    return !!list && list.length > 0 && this.selected().size === list.length;
  });

  private poll: Subscription = interval(REFRESH_MS)
    .pipe(startWith(0), switchMap(() => this.service.stats()))
    .subscribe({
      next: (s) => {
        this.stands.set(s.stands);
        const live = new Set(s.stands.map((x) => x.standId));
        const next = new Set([...this.selected()].filter((id) => live.has(id)));
        if (next.size !== this.selected().size) this.selected.set(next);
      },
      error: () => this.notFound.set(true),
    });

  ngOnDestroy(): void {
    this.poll.unsubscribe();
  }

  toggle(standId: string): void {
    this.justSubmitted.set(false);
    const next = new Set(this.selected());
    next.has(standId) ? next.delete(standId) : next.add(standId);
    this.selected.set(next);
  }

  toggleAll(): void {
    const list = this.stands();
    if (!list) return;
    this.selected.set(this.allSelected() ? new Set() : new Set(list.map((s) => s.standId)));
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
      .signalerPlusieurs({
        standIds: [...this.selected()],
        nom: this.nom.trim() || undefined,
        prenom: this.prenom.trim() || undefined,
        telephone: this.telephone.trim() || undefined,
      })
      .subscribe({
        next: (r) => {
          this.submitting.set(false);
          this.enregistres.set(r.enregistres);
          this.justSubmitted.set(true);
          this.selected.set(new Set());
          this.nom = '';
          this.prenom = '';
          this.telephone = '';
          this.step.set('select');
        },
        error: () => {
          this.submitting.set(false);
          this.error.set('Numéro de téléphone invalide, ou passage impossible à enregistrer.');
        },
      });
  }
}
