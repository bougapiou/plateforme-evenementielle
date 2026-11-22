import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CheckinService, ScanResponse, SelfCheckinInfo } from '../checkin/checkin.service';
import { PhoneInputComponent } from '../../shared/phone-input.component';
import { formatDateTime, formatTime } from '../../shared/format';

/**
 * Self check-in (backup mode): the participant scans the QR displayed at the
 * room entrance and confirms his presence with the phone number he registered with.
 */
@Component({
  selector: 'app-self-checkin',
  standalone: true,
  imports: [FormsModule, PhoneInputComponent],
  template: `
    <div class="mx-auto max-w-md">
      @if (info(); as i) {
        <div class="card p-6">
          <p class="text-xs font-semibold uppercase tracking-wide text-brand-700">{{ i.evenement }}</p>
          <h1 class="mt-1 text-xl font-bold text-slate-900">{{ i.activite }}</h1>
          <p class="text-sm text-slate-500">
            {{ dt(i.debut) }}{{ i.fin ? ' – ' + time(i.fin) : '' }}{{ i.salle ? ' · ' + i.salle : '' }}
          </p>

          @if (result(); as r) {
            <div class="mt-5 rounded-lg p-4 text-white"
                 [class.bg-green-600]="r.resultat === 'VALIDE'"
                 [class.bg-orange-500]="r.resultat !== 'VALIDE'">
              <p class="text-lg font-bold">{{ r.message }}</p>
              @if (r.participantNom) { <p class="mt-1">{{ r.participantNom }}</p> }
              @if (r.numeroBillet) { <p class="text-sm opacity-90">Billet {{ r.numeroBillet }}</p> }
            </div>
          } @else {
            <label class="form-label mt-5">Numéro de téléphone utilisé à l'inscription</label>
            <app-phone-input [(ngModel)]="phone" />
            @if (error()) { <p class="mt-2 text-sm text-red-700">{{ error() }}</p> }
            <button type="button" class="btn-primary mt-4 w-full" [disabled]="busy()" (click)="confirm()">
              {{ busy() ? 'Un instant…' : 'Je suis présent(e)' }}
            </button>
          }
        </div>
      } @else if (loadError()) {
        <p class="card p-6 text-center text-slate-500">{{ loadError() }}</p>
      } @else {
        <p class="py-16 text-center text-slate-400">Chargement…</p>
      }
    </div>
  `,
})
export class SelfCheckinComponent {
  private checkin = inject(CheckinService);
  jeton = input.required<string>();
  info = signal<SelfCheckinInfo | null>(null);
  result = signal<ScanResponse | null>(null);
  error = signal<string | null>(null);
  loadError = signal<string | null>(null);
  busy = signal(false);
  phone = '';

  dt = (iso?: string) => formatDateTime(iso);
  time = (iso?: string) => formatTime(iso);

  constructor() {
    effect(() => {
      const j = this.jeton();
      this.checkin.publicSelfCheckinInfo(j).subscribe({
        next: (i) => this.info.set(i),
        error: () => this.loadError.set("Ce QR d'auto-enregistrement n'est pas (ou plus) actif."),
      });
    });
  }

  confirm(): void {
    if (!/^\+?[0-9 ]{6,20}$/.test(this.phone.trim())) {
      this.error.set('Renseignez un numéro de téléphone valide.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.checkin.publicSelfCheckin(this.jeton(), this.phone.trim()).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.result.set(r);
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(err.error?.message ?? 'Enregistrement impossible.');
      },
    });
  }
}
