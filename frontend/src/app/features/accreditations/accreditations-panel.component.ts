import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ACCREDITATION_ROLES,
  Accreditation,
  AccreditationsService,
} from './accreditations.service';
import { EventsService } from '../events/events.service';
import { Activity } from '../events/event.models';
import { downloadBlob } from '../invoices/invoices.service';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-accreditations-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="grid gap-4 lg:grid-cols-2">
      <form class="card p-4" [formGroup]="form" (ngSubmit)="submit()">
        <h3 class="font-semibold text-slate-800">Nouveau badge</h3>
        <div class="mt-3 space-y-3">
          <input class="form-input" placeholder="Nom de la personne *" formControlName="personneNom" />
          <input class="form-input" placeholder="Organisation" formControlName="organisation" />
          <input class="form-input" type="email" placeholder="E-mail (optionnel)"
                 formControlName="personneEmail" />
          <select class="form-input" formControlName="fonction">
            @for (r of roles; track r.value) { <option [value]="r.value">{{ r.label }}</option> }
          </select>
          @if (form.controls.fonction.value === 'AUTRE') {
            <input class="form-input" placeholder="Fonction (texte libre) *" formControlName="fonctionLibre" />
          }
          <select class="form-input" formControlName="activityId">
            <option value="">Toutes les activités (badge événement)</option>
            @for (a of activities(); track a.id) { <option [value]="a.id">{{ a.titre }}</option> }
          </select>
        </div>
        @if (error()) { <p class="mt-2 text-sm text-red-700">{{ error() }}</p> }
        <button type="submit" class="btn-primary mt-3" [disabled]="saving()">
          {{ saving() ? 'Création…' : 'Délivrer le badge' }}
        </button>
      </form>

      <div class="card overflow-hidden p-0">
        <table class="w-full text-sm">
          <thead class="bg-slate-50 text-left text-slate-500">
            <tr>
              <th class="px-3 py-2">Personne</th>
              <th class="px-3 py-2">Fonction</th>
              <th class="px-3 py-2">Portée</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            @for (a of list(); track a.id) {
              <tr [class.opacity-50]="a.statut === 'REVOQUEE'">
                <td class="px-3 py-2">
                  {{ a.personneNom }}
                  @if (a.organisation) { <span class="text-slate-400">· {{ a.organisation }}</span> }
                  <div class="font-mono text-xs text-slate-400">{{ a.numero }}</div>
                </td>
                <td class="px-3 py-2">{{ a.fonctionLibelle }}</td>
                <td class="px-3 py-2 text-slate-500">{{ a.activiteNom || 'Événement entier' }}</td>
                <td class="px-3 py-2 text-right">
                  <button class="btn-ghost text-brand-700" (click)="badge(a)">Badge PDF</button>
                  @if (a.statut === 'ACTIVE') {
                    <button class="btn-ghost text-red-600" (click)="revoke(a)">Révoquer</button>
                  } @else {
                    <span class="text-xs text-red-600">révoqué</span>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="4" class="px-3 py-6 text-center text-slate-400">Aucune accréditation.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class AccreditationsPanelComponent {
  eventId = input.required<string>();

  private fb = inject(FormBuilder);
  private service = inject(AccreditationsService);
  private events = inject(EventsService);

  roles = ACCREDITATION_ROLES;
  list = signal<Accreditation[]>([]);
  activities = signal<Activity[]>([]);
  saving = signal(false);
  error = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    personneNom: ['', Validators.required],
    organisation: [''],
    personneEmail: [''],
    fonction: ['CONFERENCIER'],
    fonctionLibre: [''],
    activityId: [''],
  });

  constructor() {
    queueMicrotask(() => {
      this.service.forEvent(this.eventId()).subscribe((l) => this.list.set(l));
      this.events.activities(this.eventId()).subscribe((a) => this.activities.set(a));
    });
  }

  submit(): void {
    const v = this.form.getRawValue();
    if (!v.personneNom.trim() || (v.fonction === 'AUTRE' && !v.fonctionLibre.trim())) {
      this.error.set('Nom requis ; fonction à préciser pour « Autre ».');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.service
      .issue(this.eventId(), {
        personneNom: v.personneNom.trim(),
        organisation: v.organisation.trim() || undefined,
        personneEmail: v.personneEmail.trim() || undefined,
        fonction: v.fonction as Accreditation['fonction'],
        fonctionLibre: v.fonctionLibre.trim() || undefined,
        activityId: v.activityId || undefined,
      })
      .subscribe({
        next: (a) => {
          this.saving.set(false);
          this.list.set([...this.list(), a]);
          this.form.reset({ fonction: 'CONFERENCIER', activityId: '' });
        },
        error: (err: HttpErrorResponse) => {
          this.saving.set(false);
          this.error.set((err.error as ApiError)?.message ?? 'Création impossible.');
        },
      });
  }

  revoke(a: Accreditation): void {
    this.service.revoke(a.id).subscribe((updated) =>
      this.list.set(this.list().map((x) => (x.id === a.id ? updated : x))),
    );
  }

  badge(a: Accreditation): void {
    this.service.badgePdfBlob(a.id).subscribe((b) => downloadBlob(b, `badge-${a.numero}.pdf`));
  }
}
