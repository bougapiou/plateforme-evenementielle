import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { OrganizersService } from './organizers.service';
import { StructuresService } from '../structures/structures.service';
import { Organizer } from './organizer.models';
import { StructureSummary } from '../structures/structure.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-organizer',
  standalone: true,
  imports: [ReactiveFormsModule, StatusBadgeComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Espace organisateur</h1>

    @if (loading()) {
      <p class="mt-4 text-sm text-slate-500">Chargement…</p>
    } @else if (organizer()) {
      <div class="mt-4 card p-5">
        <div class="flex items-center gap-3">
          <h2 class="font-semibold text-slate-800">{{ organizer()!.nomAffichage }}</h2>
          <app-status-badge [value]="organizer()!.statut" />
        </div>
        <p class="mt-2 text-sm text-slate-500">
          {{ organizer()!.description || 'Aucune description.' }}
        </p>
        @if (organizer()!.statut === 'EN_ATTENTE') {
          <p class="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
            Votre demande est en cours d'examen par un administrateur.
          </p>
        } @else if (organizer()!.statut === 'ACTIF') {
          <p class="mt-3 rounded-lg bg-green-50 px-3 py-2 text-sm text-green-800">
            Vous pouvez créer et gérer des événements.
          </p>
        }
        @if (organizer()!.structureName) {
          <p class="mt-2 text-sm text-slate-500">Structure : {{ organizer()!.structureName }}</p>
        }
      </div>
    } @else {
      <form class="mt-4 card space-y-4 p-5" [formGroup]="form" (ngSubmit)="submit()">
        <p class="text-sm text-slate-500">
          Devenez organisateur pour créer des événements. Votre demande sera validée par un administrateur.
        </p>
        <div>
          <label class="form-label">Nom affiché (organisateur)</label>
          <input class="form-input" formControlName="nomAffichage" />
        </div>
        <div>
          <label class="form-label">Description</label>
          <textarea class="form-input" rows="3" formControlName="description"></textarea>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="form-label">E-mail de contact</label>
            <input class="form-input" formControlName="contactEmail" />
          </div>
          <div>
            <label class="form-label">Téléphone de contact</label>
            <input class="form-input" formControlName="contactTelephone" />
          </div>
        </div>
        <div>
          <label class="form-label">Agir pour une structure (optionnel)</label>
          <select class="form-input" formControlName="structureId">
            <option value="">— Aucune —</option>
            @for (s of structures(); track s.id) {
              <option [value]="s.id">{{ s.raisonSociale }}</option>
            }
          </select>
        </div>
        @if (error()) {
          <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
        }
        <button type="submit" class="btn-primary" [disabled]="saving()">
          {{ saving() ? 'Envoi…' : 'Soumettre ma demande' }}
        </button>
      </form>
    }
  `,
})
export class OrganizerComponent {
  private fb = inject(FormBuilder);
  private service = inject(OrganizersService);
  private structuresService = inject(StructuresService);

  loading = signal(true);
  saving = signal(false);
  error = signal<string | null>(null);
  organizer = signal<Organizer | null>(null);
  structures = signal<StructureSummary[]>([]);

  form = this.fb.nonNullable.group({
    nomAffichage: ['', [Validators.required]],
    description: [''],
    contactEmail: [''],
    contactTelephone: [''],
    structureId: [''],
  });

  constructor() {
    this.service.me().subscribe({
      next: (o) => {
        this.organizer.set(o);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.structuresService.mine().subscribe((s) => this.structures.set(s));
      },
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    const raw = this.form.getRawValue();
    const payload = { ...raw, structureId: raw.structureId || undefined };
    this.service.apply(payload).subscribe({
      next: (o) => {
        this.saving.set(false);
        this.organizer.set(o);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set((err.error as ApiError)?.message ?? 'Envoi impossible.');
      },
    });
  }
}
