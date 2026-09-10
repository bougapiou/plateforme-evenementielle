import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ApiError, UserType } from '../core/models';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="mx-auto max-w-lg">
      <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
           class="mx-auto mb-6 h-24 w-auto" />
      <div class="card p-6">
        <h1 class="text-xl font-bold text-slate-800">Créer un compte</h1>
        <p class="mt-1 text-sm text-slate-500">
          Particulier ou structure (entreprise / institution).
        </p>

        <form class="mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <div class="grid gap-4 sm:grid-cols-2">
            <div>
              <label class="form-label" for="firstName">Prénom</label>
              <input id="firstName" class="form-input" formControlName="firstName" />
            </div>
            <div>
              <label class="form-label" for="lastName">Nom</label>
              <input id="lastName" class="form-input" formControlName="lastName" />
            </div>
          </div>
          <div>
            <label class="form-label" for="email">Adresse e-mail</label>
            <input id="email" type="email" class="form-input" formControlName="email" />
          </div>
          <div class="grid gap-4 sm:grid-cols-2">
            <div>
              <label class="form-label" for="phone">Téléphone</label>
              <input id="phone" class="form-input" formControlName="phone" placeholder="+226 70 00 00 00" />
            </div>
            <div>
              <label class="form-label" for="type">Type de compte</label>
              <select id="type" class="form-input" formControlName="type">
                <option value="PARTICULIER">Particulier</option>
                <option value="STRUCTURE">Structure</option>
              </select>
            </div>
          </div>
          <div>
            <label class="form-label" for="password">Mot de passe</label>
            <input id="password" type="password" class="form-input" formControlName="password"
                   autocomplete="new-password" />
            <p class="mt-1 text-xs text-slate-400">8 caractères minimum.</p>
          </div>

          @if (error()) {
            <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
          }

          <button type="submit" class="btn-primary w-full" [disabled]="loading()">
            {{ loading() ? 'Création…' : 'Créer mon compte' }}
          </button>
        </form>

        <p class="mt-4 text-center text-sm text-slate-500">
          Déjà inscrit ?
          <a routerLink="/connexion" class="font-semibold text-brand-700">Se connecter</a>
        </p>
      </div>
    </div>
  `,
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  loading = signal(false);
  error = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required, Validators.pattern(/^\+?[0-9 ]{6,20}$/)]],
    type: ['PARTICULIER' as UserType],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/tableau-de-bord'),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        const body = err.error as ApiError | undefined;
        this.error.set(body?.fieldErrors?.[0]?.message ?? body?.message ?? 'Inscription impossible.');
      },
    });
  }
}
