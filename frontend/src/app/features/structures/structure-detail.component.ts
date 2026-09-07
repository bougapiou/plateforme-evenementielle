import { Component, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { StructuresService } from './structures.service';
import { Structure, StructureMember } from './structure.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-structure-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <a routerLink="/tableau-de-bord/structures" class="text-sm text-slate-500">← Mes structures</a>

    @if (structure(); as s) {
      <div class="mt-2 flex items-center gap-3">
        <h1 class="text-xl font-bold text-slate-800">{{ s.raisonSociale }}</h1>
        <app-status-badge [value]="s.statut" />
      </div>

      <div class="mt-4 grid gap-4 lg:grid-cols-2">
        <div class="card p-5">
          <h2 class="font-semibold text-slate-800">Informations</h2>
          <dl class="mt-3 grid grid-cols-2 gap-2 text-sm">
            <dt class="text-slate-400">Type</dt><dd>{{ s.typeStructure }}</dd>
            <dt class="text-slate-400">Secteur</dt><dd>{{ s.secteurActivite || '—' }}</dd>
            <dt class="text-slate-400">RCCM</dt><dd>{{ s.rccm || '—' }}</dd>
            <dt class="text-slate-400">IFU</dt><dd>{{ s.ifu || '—' }}</dd>
            <dt class="text-slate-400">Ville</dt><dd>{{ s.ville || '—' }}</dd>
            <dt class="text-slate-400">Téléphone</dt><dd>{{ s.telephone || '—' }}</dd>
          </dl>
        </div>

        <div class="card p-5">
          <div class="flex items-center justify-between">
            <h2 class="font-semibold text-slate-800">Représentants</h2>
            @if (canManage()) {
              <button type="button" class="btn-ghost text-brand-700"
                      (click)="showMemberForm.set(!showMemberForm())">+ Ajouter</button>
            }
          </div>

          @if (showMemberForm()) {
            <form class="mt-3 space-y-2" [formGroup]="memberForm" (ngSubmit)="addMember()">
              <input class="form-input" placeholder="E-mail d'un utilisateur inscrit"
                     formControlName="email" />
              <div class="flex gap-2">
                <select class="form-input" formControlName="roleInterne">
                  <option value="MEMBRE">Membre</option>
                  <option value="ADMINISTRATEUR">Administrateur</option>
                </select>
                <input class="form-input" placeholder="Fonction" formControlName="fonction" />
              </div>
              @if (memberError()) {
                <p class="text-sm text-red-700">{{ memberError() }}</p>
              }
              <button type="submit" class="btn-primary">Ajouter</button>
            </form>
          }

          <ul class="mt-3 divide-y divide-slate-100 text-sm">
            @for (m of members(); track m.id) {
              <li class="flex items-center justify-between py-2">
                <div>
                  <p class="font-medium text-slate-700">{{ m.fullName }}</p>
                  <p class="text-slate-400">{{ m.email }} · {{ m.roleInterne }}</p>
                </div>
                @if (canManage() && m.roleInterne !== 'PROPRIETAIRE') {
                  <button type="button" class="text-xs text-red-600" (click)="remove(m)">Retirer</button>
                }
              </li>
            }
          </ul>
        </div>
      </div>
    } @else {
      <p class="mt-6 text-sm text-slate-500">Chargement…</p>
    }
  `,
})
export class StructureDetailComponent {
  private fb = inject(FormBuilder);
  private service = inject(StructuresService);

  id = input.required<string>();
  structure = signal<Structure | null>(null);
  members = signal<StructureMember[]>([]);
  showMemberForm = signal(false);
  memberError = signal<string | null>(null);

  memberForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    roleInterne: ['MEMBRE'],
    fonction: [''],
  });

  constructor() {
    effect(() => {
      const id = this.id();
      if (id) this.reload(id);
    });
  }

  canManage(): boolean {
    const r = this.structure()?.myRole;
    return r === 'PROPRIETAIRE' || r === 'ADMINISTRATEUR';
  }

  private reload(id = this.id()): void {
    this.service.byId(id).subscribe((s) => this.structure.set(s));
    this.service.members(id).subscribe((m) => this.members.set(m));
  }

  addMember(): void {
    if (this.memberForm.invalid) return;
    this.memberError.set(null);
    this.service.addMember(this.id(), this.memberForm.getRawValue()).subscribe({
      next: () => {
        this.showMemberForm.set(false);
        this.memberForm.reset({ roleInterne: 'MEMBRE' });
        this.reload();
      },
      error: (err: HttpErrorResponse) =>
        this.memberError.set((err.error as ApiError)?.message ?? 'Ajout impossible.'),
    });
  }

  remove(m: StructureMember): void {
    this.service.removeMember(this.id(), m.userId).subscribe(() => this.reload());
  }
}
