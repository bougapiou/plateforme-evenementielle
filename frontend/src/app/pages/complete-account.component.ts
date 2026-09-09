import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ApiError } from '../core/models';

@Component({
  selector: 'app-complete-account',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="mx-auto max-w-md">
      <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
           class="mx-auto mb-6 h-24 w-auto" />

      @if (!auth.isGuest()) {
        <div class="card p-6 text-center">
          @if (auth.isFullyAuthenticated()) {
            <p class="text-sm text-slate-600">Votre compte est déjà actif.</p>
            <a routerLink="/tableau-de-bord" class="btn-primary mt-4 inline-flex">Mon espace</a>
          } @else {
            <p class="text-sm text-slate-600">
              Aucune session invité en cours. Prenez d'abord un billet ou inscrivez-vous
              à un événement.
            </p>
            <a routerLink="/" class="btn-primary mt-4 inline-flex">Voir les événements</a>
          }
        </div>
      } @else {
        <div class="card p-6">
          <h1 class="text-xl font-bold text-slate-800">Finaliser mon compte</h1>
          <p class="mt-1 text-sm text-slate-500">
            Choisissez un mot de passe pour retrouver vos billets, inscriptions et
            factures sur tous vos appareils.
          </p>

          <dl class="mt-4 rounded-lg bg-slate-50 p-3 text-sm">
            <div class="flex justify-between">
              <dt class="text-slate-400">Nom</dt>
              <dd class="font-medium text-slate-700">{{ auth.user()?.fullName }}</dd>
            </div>
            <div class="mt-1 flex justify-between">
              <dt class="text-slate-400">E-mail</dt>
              <dd class="font-medium text-slate-700">{{ auth.user()?.email }}</dd>
            </div>
          </dl>

          <form class="mt-5 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
            <div>
              <label class="form-label" for="password">Mot de passe</label>
              <input id="password" type="password" class="form-input" formControlName="password"
                     autocomplete="new-password" />
              <p class="mt-1 text-xs text-slate-400">8 caractères minimum.</p>
            </div>
            <div>
              <label class="form-label" for="confirm">Confirmer le mot de passe</label>
              <input id="confirm" type="password" class="form-input" formControlName="confirm"
                     autocomplete="new-password" />
            </div>

            @if (error()) {
              <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
            }

            <button type="submit" class="btn-primary w-full" [disabled]="loading()">
              {{ loading() ? 'Création…' : 'Créer mon compte' }}
            </button>
          </form>
        </div>
      }
    </div>
  `,
})
export class CompleteAccountComponent {
  private fb = inject(FormBuilder);
  private router = inject(Router);
  auth = inject(AuthService);

  loading = signal(false);
  error = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', [Validators.required]],
  });

  submit(): void {
    const { password, confirm } = this.form.getRawValue();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (password !== confirm) {
      this.error.set('Les deux mots de passe ne correspondent pas.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.completeRegistration({ password }).subscribe({
      next: () => this.router.navigateByUrl('/tableau-de-bord'),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        const body = err.error as ApiError | undefined;
        this.error.set(body?.fieldErrors?.[0]?.message ?? body?.message ?? 'Création impossible.');
      },
    });
  }
}
