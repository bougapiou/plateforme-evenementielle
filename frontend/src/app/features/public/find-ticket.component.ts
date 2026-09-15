import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ApiError } from '../../core/models';
import { PhoneInputComponent } from '../../shared/phone-input.component';

/**
 * Lets a guest visitor get back the session tied to a phone number they used
 * at checkout, without an account or a password — same lookup the guest
 * checkout form already does when it recognises a returning phone number,
 * just exposed as its own entry point next to the public QR scanner.
 */
@Component({
  selector: 'app-find-ticket',
  standalone: true,
  imports: [FormsModule, RouterLink, PhoneInputComponent],
  template: `
    <div class="mx-auto max-w-md">
      <h1 class="text-xl font-bold text-slate-800">Retrouver mon billet</h1>

      @if (auth.isAuthenticated()) {
        <div class="card mt-4 p-5">
          <p class="text-sm text-slate-600">Vous êtes déjà connecté.</p>
          <a routerLink="/tableau-de-bord/billets" class="btn-primary mt-3 inline-block">
            Voir mes billets
          </a>
        </div>
      } @else {
        <p class="mt-1 text-sm text-slate-500">
          Indiquez le nom et le numéro de téléphone utilisés lors de votre achat ou
          inscription : vous retrouvez directement vos billets, sans mot de passe.
        </p>

        <form class="card mt-4 space-y-4 p-5" (ngSubmit)="submit()">
          <div class="grid gap-3 sm:grid-cols-2">
            <div>
              <label class="form-label">Prénom</label>
              <input class="form-input" name="prenom" [(ngModel)]="firstName" required />
            </div>
            <div>
              <label class="form-label">Nom</label>
              <input class="form-input" name="nom" [(ngModel)]="lastName" required />
            </div>
          </div>
          <div>
            <label class="form-label">Téléphone *</label>
            <app-phone-input [(ngModel)]="phone" name="telephone" />
          </div>

          @if (error()) {
            <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
          }

          <button type="submit" class="btn-primary" [disabled]="submitting()">
            {{ submitting() ? 'Recherche…' : 'Retrouver mon billet' }}
          </button>
          <p class="text-xs text-slate-400">
            Déjà un compte avec mot de passe ?
            <a routerLink="/connexion" class="text-brand-700">Connectez-vous</a> à la place.
          </p>
        </form>
      }
    </div>
  `,
})
export class FindTicketComponent {
  auth = inject(AuthService);
  private router = inject(Router);

  firstName = '';
  lastName = '';
  phone = '';
  submitting = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    const firstName = this.firstName.trim();
    const lastName = this.lastName.trim();
    const phone = this.phone.trim();
    if (!firstName || !lastName || !/^\+?[0-9 ]{6,20}$/.test(phone)) {
      this.error.set('Renseignez votre prénom, votre nom et un numéro de téléphone valide.');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.guestSession({ firstName, lastName, phone }).subscribe({
      next: () => this.router.navigateByUrl('/tableau-de-bord/billets'),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const body = err.error as ApiError | undefined;
        this.error.set(
          body?.code === 'ACCOUNT_EXISTS'
            ? `${body.message} Utilisez « Connectez-vous » ci-dessous.`
            : (body?.message ?? 'Aucun billet trouvé pour ce numéro.'),
        );
      },
    });
  }
}
