import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ApiError } from '../../core/models';
import { PhoneInputComponent } from '../../shared/phone-input.component';
import { TicketsService } from '../tickets/tickets.service';
import { MyTicket } from '../events/event.models';
import { formatDateTime } from '../../shared/format';

interface EventGroup {
  eventId: string;
  eventNom: string;
  eventDateDebut: string;
  lieu?: string;
  tickets: MyTicket[];
}

/**
 * Lets a guest visitor get their tickets back with just their phone number —
 * no name, no password. Enter the phone → pick the event (if they have
 * tickets for more than one) → the ticket(s) for that event download right
 * away.
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
      } @else if (events()) {
        <div class="card mt-4 p-5">
          <label class="form-label">Événement</label>
          <select class="form-input" [ngModel]="selectedEventId()" (ngModelChange)="onSelect($event)"
                  [disabled]="downloadingEventId() !== null">
            <option value="">— Choisir un événement —</option>
            @for (e of events()!; track e.eventId) {
              <option [value]="e.eventId">
                {{ e.eventNom }} · {{ dt(e.eventDateDebut) }} ({{ e.tickets.length }}
                billet{{ e.tickets.length > 1 ? 's' : '' }})
              </option>
            }
          </select>
          <p class="mt-2 text-sm text-slate-500">
            @if (downloadingEventId()) {
              Téléchargement en cours…
            } @else if (downloaded()) {
              Billet téléchargé. Vous pouvez en choisir un autre.
            } @else {
              Votre billet se télécharge dès que vous choisissez l'événement.
            }
          </p>
        </div>
        <button type="button" class="btn-ghost mt-4 text-sm" (click)="restart()">
          ← Utiliser un autre numéro
        </button>
      } @else {
        <p class="mt-1 text-sm text-slate-500">
          Indiquez le numéro de téléphone utilisé lors de votre achat ou inscription :
          vous retrouvez directement vos billets, sans mot de passe.
        </p>

        <form class="card mt-4 space-y-4 p-5" (ngSubmit)="submit()">
          @if (!codeSent()) {
            <div>
              <label class="form-label">Téléphone *</label>
              <app-phone-input [(ngModel)]="phone" name="telephone" />
            </div>
          } @else {
            <p class="rounded-lg bg-brand-50 px-3 py-2 text-sm text-brand-800">
              Si ce numéro a des billets, un code à 6 chiffres vient de lui être envoyé par SMS
              (valable 10 minutes).
            </p>
            <div>
              <label class="form-label" for="otp">Code reçu par SMS *</label>
              <input id="otp" class="form-input max-w-48 text-center font-mono text-xl tracking-[0.3em]"
                     inputmode="numeric" autocomplete="one-time-code" maxlength="7" [(ngModel)]="code" name="code" />
            </div>
            <button type="button" class="text-xs text-brand-700 underline" (click)="resend()">Renvoyer un code</button>
          }

          @if (error()) {
            <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
          }

          <button type="submit" class="btn-primary w-full sm:w-auto" [disabled]="submitting()">
            {{ submitting() ? 'Un instant…' : codeSent() ? 'Valider le code' : 'Retrouver mes billets' }}
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
  private ticketsService = inject(TicketsService);

  phone = '';
  submitting = signal(false);
  error = signal<string | null>(null);
  events = signal<EventGroup[] | null>(null);
  downloadingEventId = signal<string | null>(null);
  selectedEventId = signal('');
  downloaded = signal(false);

  dt = (iso: string) => formatDateTime(iso);

  codeSent = signal(false);
  code = '';

  submit(): void {
    const phone = this.phone.trim();
    if (!/^\+?[0-9 ]{6,20}$/.test(phone)) {
      this.error.set('Renseignez un numéro de téléphone valide.');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    if (this.codeSent()) {
      this.auth.ticketOtpVerify(phone, this.code.trim()).subscribe({
        next: () => this.loadTickets(),
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.error.set((err.error as ApiError)?.message ?? 'Code incorrect.');
        },
      });
      return;
    }
    this.auth.guestLookup(phone).subscribe({
      next: () => this.loadTickets(),
      error: (err: HttpErrorResponse) => {
        const body = err.error as ApiError | undefined;
        if (body?.code === 'OTP_REQUIRED') {
          this.resend();
          return;
        }
        this.submitting.set(false);
        this.error.set(
          body?.code === 'ACCOUNT_EXISTS'
            ? `${body.message} Utilisez « Connectez-vous » ci-dessous.`
            : (body?.message ?? 'Aucun billet trouvé pour ce numéro.'),
        );
      },
    });
  }

  /** Asks the server to send (again) a code by SMS. */
  resend(): void {
    this.submitting.set(true);
    this.auth.ticketOtpRequest(this.phone.trim()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.codeSent.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set((err.error as ApiError)?.message ?? "Le code n'a pas pu être envoyé.");
      },
    });
  }

  private loadTickets(): void {
    this.ticketsService.myTickets().subscribe({
      next: (tickets) => {
        this.submitting.set(false);
        const groups = new Map<string, EventGroup>();
        for (const t of tickets) {
          const g = groups.get(t.eventId);
          if (g) {
            g.tickets.push(t);
          } else {
            groups.set(t.eventId, {
              eventId: t.eventId, eventNom: t.eventNom,
              eventDateDebut: t.eventDateDebut, lieu: t.lieu, tickets: [t],
            });
          }
        }
        if (groups.size === 0) {
          this.error.set('Aucun billet trouvé pour ce numéro.');
          return;
        }
        this.events.set([...groups.values()]);
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Aucun billet trouvé pour ce numéro.');
      },
    });
  }

  onSelect(eventId: string): void {
    this.selectedEventId.set(eventId);
    const e = this.events()?.find((x) => x.eventId === eventId);
    if (e) this.chooseEvent(e);
  }

  chooseEvent(e: EventGroup): void {
    this.downloadingEventId.set(e.eventId);
    this.downloaded.set(false);
    let remaining = e.tickets.length;
    const done = (ok: boolean) => {
      if (ok) this.downloaded.set(true);
      if (--remaining === 0) this.downloadingEventId.set(null);
    };
    for (const t of e.tickets) {
      this.ticketsService.pdfBlob(t.id).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `billet-${t.numero}.pdf`;
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 2000);
          done(true);
        },
        error: () => done(false),
      });
    }
  }

  restart(): void {
    this.selectedEventId.set('');
    this.downloaded.set(false);
    this.events.set(null);
    this.phone = '';
    this.code = '';
    this.codeSent.set(false);
    this.error.set(null);
  }
}
