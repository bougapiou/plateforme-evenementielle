import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { EventsService } from './events.service';
import { EventCategory, EventSummary } from './event.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { formatDateRange } from '../../shared/format';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-events-list',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-slate-800">Mes événements</h1>
      <button type="button" class="btn-primary" (click)="showForm.set(!showForm())">
        {{ showForm() ? 'Annuler' : 'Nouvel événement' }}
      </button>
    </div>

    @if (showForm()) {
      <form class="card mt-4 space-y-4 p-5" [formGroup]="form" (ngSubmit)="submit()">
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="sm:col-span-2">
            <label class="form-label">Nom de l'événement</label>
            <input class="form-input" formControlName="nom" />
          </div>
          <div>
            <label class="form-label">Sigle</label>
            <input class="form-input" formControlName="sigle" />
          </div>
          <div>
            <label class="form-label">Catégorie</label>
            <select class="form-input" formControlName="categoryId">
              <option value="">—</option>
              @for (c of categories(); track c.id) {
                <option [value]="c.id">{{ c.nom }}</option>
              }
            </select>
          </div>
          <div>
            <label class="form-label">Début</label>
            <input type="datetime-local" class="form-input" formControlName="dateDebut" />
          </div>
          <div>
            <label class="form-label">Fin</label>
            <input type="datetime-local" class="form-input" formControlName="dateFin" />
          </div>
          <div>
            <label class="form-label">Ville</label>
            <input class="form-input" formControlName="ville" />
          </div>
          <div>
            <label class="form-label">Lieu</label>
            <input class="form-input" formControlName="lieu" />
          </div>
          <div class="sm:col-span-2">
            <label class="form-label">Description courte</label>
            <input class="form-input" formControlName="descriptionCourte" />
          </div>
        </div>
        <div class="flex flex-wrap gap-6">
          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" formControlName="hasActivities" />
            L'événement contient plusieurs activités (programme)
          </label>
          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" formControlName="standsActifs" />
            Réservation de stands activée
          </label>
        </div>
        @if (error()) {
          <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
        }
        <button type="submit" class="btn-primary" [disabled]="saving()">
          {{ saving() ? 'Création…' : 'Créer le brouillon' }}
        </button>
      </form>
    }

    <div class="mt-6 space-y-3">
      @for (e of events(); track e.id) {
        <a [routerLink]="['/tableau-de-bord/evenements', e.id]"
           class="card flex items-center justify-between p-4 hover:border-brand-300">
          <div>
            <p class="font-semibold text-slate-800">{{ e.nom }}</p>
            <p class="text-sm text-slate-500">
              {{ range(e) }} · {{ e.ville || '—' }}{{ e.categoryNom ? ' · ' + e.categoryNom : '' }}
            </p>
          </div>
          <app-status-badge [value]="e.statut" />
        </a>
      } @empty {
        <p class="card p-6 text-sm text-slate-500">
          Aucun événement. Créez votre premier événement (il faut un profil organisateur approuvé).
        </p>
      }
    </div>
  `,
})
export class EventsListComponent {
  private fb = inject(FormBuilder);
  private service = inject(EventsService);

  events = signal<EventSummary[]>([]);
  categories = signal<EventCategory[]>([]);
  showForm = signal(false);
  saving = signal(false);
  error = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    nom: ['', Validators.required],
    sigle: [''],
    categoryId: [''],
    dateDebut: ['', Validators.required],
    dateFin: ['', Validators.required],
    ville: [''],
    lieu: [''],
    descriptionCourte: [''],
    hasActivities: [false],
    standsActifs: [false],
  });

  constructor() {
    this.reload();
    this.service.categories().subscribe((c) => this.categories.set(c));
  }

  range = (e: EventSummary) => formatDateRange(e.dateDebut, e.dateFin);

  reload(): void {
    this.service.mine().subscribe((p) => this.events.set(p.content));
  }

  submit(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const payload = {
      ...v,
      categoryId: v.categoryId || undefined,
      dateDebut: new Date(v.dateDebut).toISOString(),
      dateFin: new Date(v.dateFin).toISOString(),
    };
    this.service.create(payload as any).subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.form.reset({ hasActivities: false, standsActifs: false });
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set((err.error as ApiError)?.message ?? 'Création impossible.');
      },
    });
  }
}
