import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ApiError } from '../core/models';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="mx-auto max-w-md">
      <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
           class="mx-auto mb-6 h-24 w-auto" />
      <div class="card p-6">
        <h1 class="text-xl font-bold text-slate-800">Connexion</h1>
        <p class="mt-1 text-sm text-slate-500">Accédez à votre espace personnel.</p>

        <form class="mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <div>
            <label class="form-label" for="email">Adresse e-mail</label>
            <input id="email" type="email" class="form-input" formControlName="email" autocomplete="email" />
          </div>
          <div>
            <label class="form-label" for="password">Mot de passe</label>
            <input id="password" type="password" class="form-input" formControlName="password"
                   autocomplete="current-password" />
          </div>

          @if (error()) {
            <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
          }

          <button type="submit" class="btn-primary w-full" [disabled]="loading()">
            {{ loading() ? 'Connexion…' : 'Se connecter' }}
          </button>
        </form>

        <p class="mt-4 text-center text-sm text-slate-500">
          Pas encore de compte ?
          <a routerLink="/inscription" class="font-semibold text-brand-700">Créer un compte</a>
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  loading = signal(false);
  error = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl('/tableau-de-bord'),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        const body = err.error as ApiError | undefined;
        this.error.set(body?.message ?? 'Identifiants invalides.');
      },
    });
  }
}
