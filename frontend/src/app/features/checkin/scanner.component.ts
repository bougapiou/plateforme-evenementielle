import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckinService, ScanResponse } from './checkin.service';
import { EventsService } from '../events/events.service';
import { EventSummary } from '../events/event.models';
import { formatDateTime } from '../../shared/format';

declare const window: Window & { BarcodeDetector?: any };

@Component({
  selector: 'app-scanner',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Contrôle à l'entrée</h1>

    <div class="mt-4 card p-4">
      <label class="form-label">Événement</label>
      <select class="form-input max-w-md" [(ngModel)]="eventId" (ngModelChange)="onEventChange()">
        <option value="">— Choisir —</option>
        @for (e of events(); track e.id) { <option [value]="e.id">{{ e.nom }}</option> }
      </select>
    </div>

    @if (eventId) {
      <div class="mt-4 grid gap-4 lg:grid-cols-2">
        <div class="card p-4">
          <div class="flex items-center justify-between">
            <h2 class="font-semibold text-slate-800">Scanner</h2>
            @if (cameraSupported) {
              <button class="btn-ghost text-brand-700" (click)="toggleCamera()">
                {{ scanning() ? 'Arrêter la caméra' : 'Démarrer la caméra' }}
              </button>
            }
          </div>

          @if (scanning()) {
            <video #video autoplay playsinline muted class="mt-3 w-full rounded-lg bg-black"></video>
          }
          @if (!cameraSupported) {
            <p class="mt-2 text-xs text-slate-400">
              Caméra non disponible sur ce navigateur — saisissez le code manuellement.
            </p>
          }

          <div class="mt-3 flex gap-2">
            <input class="form-input" placeholder="Coller / saisir le code du QR"
                   [(ngModel)]="manualToken" (keyup.enter)="submit(manualToken)" />
            <button class="btn-primary" (click)="submit(manualToken)">Valider</button>
          </div>
        </div>

        <div class="card p-4">
          @if (last(); as r) {
            <div class="rounded-lg p-4 text-white"
                 [class.bg-green-600]="r.resultat === 'VALIDE'"
                 [class.bg-orange-500]="r.resultat === 'DEJA_UTILISE'"
                 [class.bg-red-600]="r.resultat === 'INVALIDE'">
              <p class="text-2xl font-extrabold">
                {{ r.resultat === 'VALIDE' ? 'VALIDE' : r.resultat === 'DEJA_UTILISE' ? 'DÉJÀ UTILISÉ' : 'INVALIDE' }}
              </p>
              @if (r.participantNom) {
                <p class="mt-1">{{ r.participantNom }} · {{ r.categorieNom }}</p>
                <p class="text-sm opacity-90">Billet {{ r.numeroBillet }}</p>
              }
              @if (r.heureEntree) { <p class="text-sm opacity-90">Entrée : {{ dt(r.heureEntree) }}</p> }
              @if (r.premierControleLe) {
                <p class="text-sm opacity-90">1er contrôle : {{ dt(r.premierControleLe) }}</p>
              }
            </div>
          } @else {
            <p class="text-sm text-slate-400">En attente d'un scan…</p>
          }

          <div class="mt-4 grid grid-cols-3 gap-2 text-center text-sm">
            <div class="rounded-lg bg-green-50 p-2">
              <p class="text-lg font-bold text-green-700">{{ stats()['valides'] || 0 }}</p>
              <p class="text-green-600">valides</p>
            </div>
            <div class="rounded-lg bg-orange-50 p-2">
              <p class="text-lg font-bold text-orange-700">{{ stats()['dejaUtilises'] || 0 }}</p>
              <p class="text-orange-600">déjà scannés</p>
            </div>
            <div class="rounded-lg bg-red-50 p-2">
              <p class="text-lg font-bold text-red-700">{{ stats()['invalides'] || 0 }}</p>
              <p class="text-red-600">invalides</p>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class ScannerComponent implements OnDestroy {
  private checkin = inject(CheckinService);
  private eventsService = inject(EventsService);

  events = signal<EventSummary[]>([]);
  eventId = '';
  manualToken = '';
  last = signal<ScanResponse | null>(null);
  stats = signal<Record<string, number>>({});
  scanning = signal(false);

  cameraSupported = 'BarcodeDetector' in window && !!navigator.mediaDevices;
  private stream?: MediaStream;
  private raf = 0;
  private busy = false;

  constructor() {
    this.eventsService.mine({ statut: '' }).subscribe((p) =>
      this.events.set(p.content.filter((e) =>
        ['PUBLIE', 'INSCRIPTIONS_OUVERTES', 'INSCRIPTIONS_FERMEES', 'EN_COURS'].includes(e.statut),
      )),
    );
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }

  dt = (iso?: string) => formatDateTime(iso);

  onEventChange(): void {
    this.last.set(null);
    if (this.eventId) this.refreshStats();
  }

  refreshStats(): void {
    this.checkin.stats(this.eventId).subscribe((s) => this.stats.set(s));
  }

  submit(token: string): void {
    const t = (token || '').trim();
    if (!t || !this.eventId) return;
    this.checkin.scan(t, this.eventId).subscribe({
      next: (r) => {
        this.last.set(r);
        this.manualToken = '';
        this.refreshStats();
      },
      error: () => this.last.set({ resultat: 'INVALIDE', message: 'Erreur', eventNom: '' } as ScanResponse),
    });
  }

  async toggleCamera(): Promise<void> {
    if (this.scanning()) {
      this.stopCamera();
      return;
    }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      this.scanning.set(true);
      setTimeout(() => this.startLoop(), 100);
    } catch {
      this.scanning.set(false);
    }
  }

  private startLoop(): void {
    const video = document.querySelector('video') as HTMLVideoElement | null;
    if (!video || !this.stream) return;
    video.srcObject = this.stream;
    const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
    const tick = async () => {
      if (!this.scanning()) return;
      if (!this.busy && video.readyState === 4) {
        this.busy = true;
        try {
          const codes = await detector.detect(video);
          if (codes.length) this.submit(codes[0].rawValue);
        } catch {
          /* ignore */
        }
        this.busy = false;
      }
      this.raf = requestAnimationFrame(tick);
    };
    tick();
  }

  private stopCamera(): void {
    this.scanning.set(false);
    cancelAnimationFrame(this.raf);
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = undefined;
  }
}
