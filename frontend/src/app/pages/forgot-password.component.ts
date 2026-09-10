import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="mx-auto max-w-md">
      <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
           class="mx-auto mb-6 h-24 w-auto" />
      <div class="card p-6">
        <h1 class="text-xl font-bold text-slate-800">Mot de passe oublié</h1>
        <p class="mt-1 text-sm text-slate-500">
          Indiquez l'adresse e-mail de votre compte. Si elle est connue, vous
          recevrez un lien pour choisir un nouveau mot de passe.
        </p>

        @if (sent()) {
          <div class="mt-6 rounded-lg bg-green-50 px-3 py-3 text-sm text-green-800">
            Si un compte existe pour cette adresse, un e-mail vient de partir.
            Le lien est valable 1 heure. Pensez à vérifier vos courriers indésirables.
          </div>
          <a routerLink="/connexion" class="btn-primary mt-4 inline-flex">Retour à la connexion</a>
        } @else {
          <form class="mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
            <div>
              <label class="form-label" for="email">Adresse e-mail</label>
              <input id="email" type="email" class="form-input" formControlName="email"
                     autocomplete="email" />
            </div>
            <button type="submit" class="btn-primary w-full" [disabled]="loading()">
              {{ loading() ? 'Envoi…' : 'Envoyer le lien' }}
            </button>
          </form>
          <p class="mt-4 text-center text-sm text-slate-500">
            <a routerLink="/connexion" class="font-semibold text-brand-700">Retour à la connexion</a>
          </p>
        }
      </div>
    </div>
  `,
})
export class ForgotPasswordComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  loading = signal(false);
  sent = signal(false);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.auth.requestPasswordReset(this.form.getRawValue().email).subscribe({
      next: () => this.sent.set(true),
      // Same UX whether or not the account exists.
      error: () => this.sent.set(true),
    });
  }
}
