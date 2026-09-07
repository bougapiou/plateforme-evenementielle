import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { StructuresService } from './structures.service';
import { STRUCTURE_TYPES, StructureSummary } from './structure.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-structures-list',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-slate-800">Mes structures</h1>
      <button type="button" class="btn-primary" (click)="showForm.set(!showForm())">
        {{ showForm() ? 'Annuler' : 'Nouvelle structure' }}
      </button>
    </div>

    @if (showForm()) {
      <form class="card mt-4 space-y-4 p-5" [formGroup]="form" (ngSubmit)="submit()">
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="form-label">Raison sociale</label>
            <input class="form-input" formControlName="raisonSociale" />
          </div>
          <div>
            <label class="form-label">Sigle</label>
            <input class="form-input" formControlName="sigle" />
          </div>
          <div>
            <label class="form-label">Type</label>
            <select class="form-input" formControlName="typeStructure">
              @for (t of types; track t.value) {
                <option [value]="t.value">{{ t.label }}</option>
              }
            </select>
          </div>
          <div>
            <label class="form-label">Secteur d'activité</label>
            <input class="form-input" formControlName="secteurActivite" />
          </div>
          <div>
            <label class="form-label">RCCM</label>
            <input class="form-input" formControlName="rccm" />
          </div>
          <div>
            <label class="form-label">IFU</label>
            <input class="form-input" formControlName="ifu" />
          </div>
          <div>
            <label class="form-label">Ville</label>
            <input class="form-input" formControlName="ville" />
          </div>
          <div>
            <label class="form-label">Téléphone</label>
            <input class="form-input" formControlName="telephone" />
          </div>
        </div>
        @if (error()) {
          <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
        }
        <button type="submit" class="btn-primary" [disabled]="saving()">
          {{ saving() ? 'Création…' : 'Créer la structure' }}
        </button>
      </form>
    }

    <div class="mt-6 space-y-3">
      @for (s of structures(); track s.id) {
        <a [routerLink]="['/tableau-de-bord/structures', s.id]"
           class="card flex items-center justify-between p-4 hover:border-brand-300">
          <div>
            <p class="font-semibold text-slate-800">{{ s.raisonSociale }}</p>
            <p class="text-sm text-slate-500">{{ s.typeStructure }} · {{ s.ville || '—' }}</p>
          </div>
          <app-status-badge [value]="s.statut" />
        </a>
      } @empty {
        <p class="card p-6 text-sm text-slate-500">
          Aucune structure. Créez-en une pour vous inscrire à des événements ou réserver des stands.
        </p>
      }
    </div>
  `,
})
export class StructuresListComponent {
  private fb = inject(FormBuilder);
  private service = inject(StructuresService);

  types = STRUCTURE_TYPES;
  structures = signal<StructureSummary[]>([]);
  showForm = signal(false);
  saving = signal(false);
  error = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    raisonSociale: ['', [Validators.required]],
    sigle: [''],
    typeStructure: ['ENTREPRISE'],
    secteurActivite: [''],
    rccm: [''],
    ifu: [''],
    ville: [''],
    telephone: [''],
  });

  constructor() {
    this.reload();
  }

  reload(): void {
    this.service.mine().subscribe((s) => this.structures.set(s));
  }

  submit(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    this.service.create(this.form.getRawValue() as any).subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.form.reset({ typeStructure: 'ENTREPRISE' });
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set((err.error as ApiError)?.message ?? 'Création impossible.');
      },
    });
  }
}
