import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ApiError } from '../core/models';
import { PasswordInputComponent } from '../shared/password-input.component';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, PasswordInputComponent],
  template: `
    <div class="mx-auto max-w-md">
      <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
           class="mx-auto mb-6 h-24 w-auto" />
      <div class="card p-6">
        <h1 class="text-xl font-bold text-slate-800">Nouveau mot de passe</h1>

        @if (!token) {
          <p class="mt-2 text-sm text-slate-600">
            Ce lien est incomplet. Refaites une demande depuis
            <a routerLink="/mot-de-passe-oublie" class="font-semibold text-brand-700">« Mot de passe oublié »</a>.
          </p>
        } @else if (done()) {
          <div class="mt-4 rounded-lg bg-green-50 px-3 py-3 text-sm text-green-800">
            Votre mot de passe a été mis à jour. Vous pouvez vous connecter.
          </div>
          <a routerLink="/connexion" class="btn-primary mt-4 inline-flex">Se connecter</a>
        } @else {
          <p class="mt-1 text-sm text-slate-500">Choisissez un mot de passe (8 caractères minimum).</p>
          <form class="mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
            <div>
              <label class="form-label" for="password">Mot de passe</label>
              <app-password-input inputId="password" formControlName="password" autocomplete="new-password" />
            </div>
            <div>
              <label class="form-label" for="confirm">Confirmer</label>
              <app-password-input inputId="confirm" formControlName="confirm" autocomplete="new-password" />
            </div>

            @if (error()) {
              <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
            }

            <button type="submit" class="btn-primary w-full" [disabled]="loading()">
              {{ loading() ? 'Enregistrement…' : 'Mettre à jour le mot de passe' }}
            </button>
          </form>
        }
      </div>
    </div>
  `,
})
export class ResetPasswordComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
  loading = signal(false);
  done = signal(false);
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
    this.auth.resetPassword(this.token, password).subscribe({
      next: () => {
        this.done.set(true);
        setTimeout(() => this.router.navigateByUrl('/connexion'), 2500);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set((err.error as ApiError)?.message ?? 'Réinitialisation impossible.');
      },
    });
  }
}
