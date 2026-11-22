import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckinDirection, CheckinService, ScanResponse } from './checkin.service';
import { Activity, EventSummary } from '../events/event.models';
import { IconComponent } from '../../shared/icon.component';
import { PhoneInputComponent } from '../../shared/phone-input.component';
import { HttpErrorResponse } from '@angular/common/http';
import { formatDateTime } from '../../shared/format';

declare const window: Window & { BarcodeDetector?: any };

@Component({
  selector: 'app-scanner',
  standalone: true,
  imports: [FormsModule, IconComponent, PhoneInputComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Contrôle à la porte</h1>

    <div class="mt-4 card p-4">
      <label class="form-label">Événement</label>
      <select class="form-input max-w-md" [(ngModel)]="eventId" (ngModelChange)="onEventChange()">
        <option value="">— Choisir —</option>
        @for (e of events(); track e.id) { <option [value]="e.id">{{ e.nom }}</option> }
      </select>
      @if (loaded() && events().length === 0) {
        <p class="mt-2 text-xs text-slate-400">
          Aucun événement à contrôler. Vous devez être l'organisateur de l'événement,
          y être ajouté comme personnel de contrôle, ou être administrateur ;
          l'événement doit être publié.
        </p>
      }

      @if (eventId && activities().length) {
        <label class="form-label mt-3">Contrôle</label>
        <select class="form-input max-w-md" [(ngModel)]="activityId" (ngModelChange)="onActivityChange()">
          <option value="">Entrée générale (tout l'événement)</option>
          @for (a of activities(); track a.id) {
            <option [value]="a.id">
              {{ a.titre }}{{ a.acces === 'GRATUIT' ? ' · gratuit' : a.acces === 'PAYANT' ? ' · payant' : '' }}
            </option>
          }
        </select>
      }

      @if (eventId) {
        <label class="form-label mt-3">Sens du contrôle</label>
        <div class="grid max-w-md grid-cols-2 gap-2">
          <button type="button"
                  class="flex items-center justify-center gap-2 rounded-lg border-2 px-3 py-3 font-semibold"
                  [class.border-brand-600]="sens === 'ENTREE'" [class.bg-brand-600]="sens === 'ENTREE'"
                  [class.text-white]="sens === 'ENTREE'" [class.border-slate-200]="sens !== 'ENTREE'"
                  (click)="setSens('ENTREE')">
            <app-icon name="login" class="h-5 w-5" /> Contrôle à la porte
          </button>
          <button type="button"
                  class="flex items-center justify-center gap-2 rounded-lg border-2 px-3 py-3 font-semibold"
                  [class.border-slate-700]="sens === 'SORTIE'" [class.bg-slate-700]="sens === 'SORTIE'"
                  [class.text-white]="sens === 'SORTIE'" [class.border-slate-200]="sens !== 'SORTIE'"
                  (click)="setSens('SORTIE')">
            <app-icon name="logout" class="h-5 w-5" /> Contrôle à la sortie
          </button>
        </div>
      }
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
          <label class="mt-3 flex items-center gap-2 text-xs text-slate-500">
            <input type="checkbox" [(ngModel)]="sound" /> Signal sonore à chaque scan
          </label>

          <details class="mt-4 rounded-lg border border-slate-200 p-3" [open]="onSiteOpen()">
            <summary class="cursor-pointer text-sm font-semibold text-slate-700" (click)="onSiteOpen.set(!onSiteOpen()); $event.preventDefault()">
              Visiteur non inscrit ? Inscription sur place
            </summary>
            <div class="mt-3 grid gap-2">
              <input class="form-input" placeholder="Nom et prénom" [(ngModel)]="walkInName" />
              <app-phone-input [(ngModel)]="walkInPhone" />
              @if (onSiteError()) { <p class="text-sm text-red-700">{{ onSiteError() }}</p> }
              <button type="button" class="btn-primary" [disabled]="onSiteBusy()" (click)="registerOnSite()">
                {{ onSiteBusy() ? 'Un instant…' : 'Créer le billet et faire entrer' }}
              </button>
              <p class="text-xs text-slate-400">
                Billet gratuit {{ activityId ? 'de cette activité' : "de l'événement" }}, marqué « sur place » dans les statistiques.
              </p>
            </div>
          </details>
        </div>

        <div class="card p-4">
          @if (last(); as r) {
            <div class="rounded-lg p-4 text-white"
                 [class.bg-green-600]="r.resultat === 'VALIDE'"
                 [class.bg-orange-500]="r.resultat === 'DEJA_UTILISE'"
                 [class.bg-red-600]="r.resultat === 'INVALIDE'">
              <p class="flex items-center gap-2 text-2xl font-extrabold">
                @if (r.sens === 'SORTIE') { <app-icon name="logout" class="h-6 w-6" /> }
                @else { <app-icon name="login" class="h-6 w-6" /> }
                {{ r.resultat === 'VALIDE' ? (r.sens === 'SORTIE' ? 'SORTIE OK' : (r.reentree ? 'RÉ-ENTRÉE' : 'VALIDE')) : r.resultat === 'DEJA_UTILISE' ? 'REFUSÉ' : 'INVALIDE' }}
              </p>
              <p class="text-sm opacity-90">{{ r.message }}</p>
              @if (r.activiteNom) {
                <p class="text-sm font-semibold opacity-90">Activité : {{ r.activiteNom }}</p>
              }
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

          <div class="mt-4 grid grid-cols-4 gap-2 text-center text-sm">
            <div class="rounded-lg bg-green-50 p-2">
              <app-icon name="login" class="mx-auto h-4 w-4 text-green-600" />
              <p class="text-lg font-bold text-green-700">{{ stats()['entrees'] || 0 }}</p>
              <p class="text-green-600">entrées</p>
            </div>
            <div class="rounded-lg bg-slate-100 p-2">
              <app-icon name="logout" class="mx-auto h-4 w-4 text-slate-600" />
              <p class="text-lg font-bold text-slate-700">{{ stats()['sorties'] || 0 }}</p>
              <p class="text-slate-600">sorties</p>
            </div>
            <div class="rounded-lg bg-brand-50 p-2">
              <app-icon name="present" class="mx-auto h-4 w-4 text-brand-600" />
              <p class="text-lg font-bold text-brand-700">{{ stats()['presents'] || 0 }}</p>
              <p class="text-brand-600">présents</p>
            </div>
            <div class="rounded-lg bg-amber-50 p-2">
              <app-icon name="repeat" class="mx-auto h-4 w-4 text-amber-600" />
              <p class="text-lg font-bold text-amber-700">{{ stats()['reentrees'] || 0 }}</p>
              <p class="text-amber-600">ré-entrées</p>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class ScannerComponent implements OnDestroy {
  private checkin = inject(CheckinService);

  events = signal<EventSummary[]>([]);
  activities = signal<Activity[]>([]);
  loaded = signal(false);
  eventId = '';
  activityId = '';
  sens: CheckinDirection = 'ENTREE';
  manualToken = '';
  last = signal<ScanResponse | null>(null);
  stats = signal<Record<string, number>>({});
  scanning = signal(false);

  cameraSupported = 'BarcodeDetector' in window && !!navigator.mediaDevices;
  private stream?: MediaStream;
  private raf = 0;
  private busy = false;

  constructor() {
    this.checkin.controllableEvents().subscribe({
      next: (list) => {
        this.events.set(list);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }

  dt = (iso?: string) => formatDateTime(iso);

  onEventChange(): void {
    this.last.set(null);
    this.activityId = '';
    this.activities.set([]);
    if (!this.eventId) return;
    this.refreshStats();
    this.checkin.eventActivities(this.eventId).subscribe({
      next: (list) => this.activities.set(list),
      error: () => this.activities.set([]),
    });
  }

  onActivityChange(): void {
    this.last.set(null);
    this.sens = 'ENTREE';
    if (this.eventId) this.refreshStats();
  }

  setSens(s: CheckinDirection): void {
    this.sens = s;
    this.last.set(null);
  }

  refreshStats(): void {
    this.checkin
      .stats(this.eventId, this.activityId || undefined)
      .subscribe((s) => this.stats.set(s));
  }

  sound = true;
  walkInName = '';
  walkInPhone = '';
  onSiteOpen = signal(false);
  onSiteBusy = signal(false);
  onSiteError = signal<string | null>(null);
  private audio?: AudioContext;
  /** Same QR seen again within this delay by the camera loop is ignored. */
  private lastToken = '';
  private lastTokenAt = 0;

  submit(token: string): void {
    const t = (token || '').trim();
    if (!t || !this.eventId) return;
    const now = Date.now();
    if (t === this.lastToken && now - this.lastTokenAt < 3000) return;
    this.lastToken = t;
    this.lastTokenAt = now;
    this.checkin.scan(t, this.eventId, this.activityId || undefined, this.sens).subscribe({
      next: (r) => {
        this.show(r);
        this.manualToken = '';
        this.refreshStats();
      },
      error: (err: HttpErrorResponse) => this.show({
        resultat: 'INVALIDE', sens: this.sens, reentree: false,
        message: err.status === 403 ? (err.error?.message ?? 'Non autorisé pour cette salle') : 'Erreur réseau',
        eventNom: '',
      }),
    });
  }

  registerOnSite(): void {
    const nom = this.walkInName.trim();
    const tel = this.walkInPhone.trim();
    if (!nom || !/^\+?[0-9 ]{6,20}$/.test(tel)) {
      this.onSiteError.set('Renseignez le nom et un numéro de téléphone valide.');
      return;
    }
    this.onSiteBusy.set(true);
    this.onSiteError.set(null);
    this.checkin.onSite(this.eventId, { activityId: this.activityId || undefined, nom, telephone: tel }).subscribe({
      next: (res) => {
        this.onSiteBusy.set(false);
        this.walkInName = '';
        this.walkInPhone = '';
        this.show({ ...res.scan, message: `Inscrit sur place — billet ${res.numeroBillet}` });
        this.refreshStats();
      },
      error: (err: HttpErrorResponse) => {
        this.onSiteBusy.set(false);
        this.onSiteError.set(err.error?.message ?? 'Inscription impossible.');
      },
    });
  }

  /** Shows the result with a colour, a sound and a vibration (green / orange / red). */
  private show(r: ScanResponse): void {
    this.last.set(r);
    navigator.vibrate?.(r.resultat === 'VALIDE' ? 80 : [120, 60, 120]);
    if (!this.sound) return;
    try {
      this.audio ??= new AudioContext();
      const tones: [number, number][] = r.resultat === 'VALIDE' ? [[880, 0.12]]
        : r.resultat === 'DEJA_UTILISE' ? [[520, 0.12], [520, 0.12]] : [[220, 0.45]];
      let at = this.audio.currentTime;
      for (const [freq, dur] of tones) {
        const osc = this.audio.createOscillator();
        const gain = this.audio.createGain();
        osc.frequency.value = freq;
        osc.type = r.resultat === 'INVALIDE' ? 'square' : 'sine';
        gain.gain.value = 0.15;
        osc.connect(gain).connect(this.audio.destination);
        osc.start(at);
        osc.stop(at + dur);
        at += dur + 0.08;
      }
    } catch {
      /* audio unavailable */
    }
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
