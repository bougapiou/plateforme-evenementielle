import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ApiError, AuthResponse, MfaSetup } from '../core/models';
import { FormsModule } from '@angular/forms';
import { PasswordInputComponent } from '../shared/password-input.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, RouterLink, PasswordInputComponent],
  template: `
    <div class="mx-auto max-w-md">
      <img src="assets/logo.png" alt="Plateforme Nationale des Événements"
           class="mx-auto mb-6 h-24 w-auto" />
      <div class="card p-6">
        <h1 class="text-xl font-bold text-slate-800">Connexion</h1>
        <p class="mt-1 text-sm text-slate-500">Accédez à votre espace personnel.</p>

        @if (step() === 'password') {
        <form class="mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()">
          <div>
            <label class="form-label" for="email">Adresse e-mail</label>
            <input id="email" type="email" class="form-input" formControlName="email" autocomplete="email" />
          </div>
          <div>
            <label class="form-label" for="password">Mot de passe</label>
            <app-password-input inputId="password" formControlName="password" autocomplete="current-password" />
          </div>

          @if (error()) {
            <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
          }

          <button type="submit" class="btn-primary w-full" [disabled]="loading()">
            {{ loading() ? 'Connexion…' : 'Se connecter' }}
          </button>
        </form>
        } @else {
          <form class="mt-6 space-y-4" (ngSubmit)="submitCode()">
            @if (step() === 'setup') {
              <div class="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
                <p class="font-semibold">Double authentification obligatoire</p>
                <p class="mt-1">
                  Votre rôle exige un second facteur. Installez une application d'authentification
                  (Google Authenticator, Microsoft Authenticator, FreeOTP…), scannez ce QR code puis
                  saisissez le code à 6 chiffres affiché.
                </p>
              </div>
              @if (setup(); as st) {
                <img [src]="st.qrDataUri" alt="QR code à scanner dans l'application d'authentification"
                     class="mx-auto h-48 w-48" />
                <p class="text-center text-xs text-slate-500">
                  Saisie manuelle : <code class="select-all break-all font-mono">{{ st.secret }}</code>
                </p>
              } @else {
                <p class="text-center text-sm text-slate-400">Préparation…</p>
              }
            } @else {
              <p class="text-sm text-slate-600">
                Saisissez le code à 6 chiffres affiché par votre application d'authentification.
              </p>
            }
            <div>
              <label class="form-label" for="code">Code de vérification</label>
              <input id="code" class="form-input text-center font-mono text-2xl tracking-[0.4em]"
                     inputmode="numeric" autocomplete="one-time-code" maxlength="7" placeholder="000000"
                     [(ngModel)]="code" name="code" required />
            </div>
            @if (error()) {
              <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
            }
            <button type="submit" class="btn-primary w-full" [disabled]="loading()">
              {{ loading() ? 'Vérification…' : (step() === 'setup' ? 'Activer et se connecter' : 'Valider') }}
            </button>
            <button type="button" class="btn-ghost w-full" (click)="restart()">← Recommencer</button>
          </form>
        }

        <p class="mt-3 text-center text-sm">
          <a routerLink="/mot-de-passe-oublie" class="text-slate-500 hover:text-brand-700">
            Mot de passe oublié ?
          </a>
        </p>
        <p class="mt-2 text-center text-sm text-slate-500">
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
  private route = inject(ActivatedRoute);

  loading = signal(false);
  error = signal<string | null>(null);
  step = signal<'password' | 'verify' | 'setup'>('password');
  setup = signal<MfaSetup | null>(null);
  code = '';
  private mfaToken = '';

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
      next: (res) => this.afterPassword(res),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        const body = err.error as ApiError | undefined;
        this.error.set(body?.message ?? 'Identifiants invalides.');
      },
    });
  }

  private afterPassword(res: AuthResponse): void {
    if (!res.mfa) {
      this.done();
      return;
    }
    this.loading.set(false);
    this.mfaToken = res.mfaToken ?? '';
    this.code = '';
    this.step.set(res.mfa === 'SETUP' ? 'setup' : 'verify');
    if (res.mfa === 'SETUP') {
      this.auth.mfaSetup(this.mfaToken).subscribe({
        next: (st) => this.setup.set(st),
        error: (err: HttpErrorResponse) => this.fail(err),
      });
    }
  }

  submitCode(): void {
    if (!/^\s*\d{3}\s?\d{3}\s*$/.test(this.code)) {
      this.error.set('Saisissez le code à 6 chiffres.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const call = this.step() === 'setup'
      ? this.auth.mfaActivate(this.mfaToken, this.code)
      : this.auth.mfaVerify(this.mfaToken, this.code);
    call.subscribe({ next: () => this.done(), error: (err: HttpErrorResponse) => this.fail(err) });
  }

  restart(): void {
    this.step.set('password');
    this.setup.set(null);
    this.error.set(null);
    this.form.controls.password.reset('');
  }

  private fail(err: HttpErrorResponse): void {
    this.loading.set(false);
    const body = err.error as ApiError | undefined;
    if (body?.code === 'MFA_TOKEN_EXPIRED') this.restart();
    this.error.set(err.status === 401 ? "Code incorrect. Vérifiez l'heure de votre téléphone."
      : (body?.message ?? 'Vérification impossible.'));
  }

  private done(): void {
    const redirect = this.route.snapshot.queryParamMap.get('redirect');
    this.router.navigateByUrl(redirect && redirect.startsWith('/') ? redirect : '/tableau-de-bord');
  }
}
