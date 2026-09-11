import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

declare const window: Window & { BarcodeDetector?: any };

/**
 * Camera-based QR scanner open to any visitor — no account needed. Meant for
 * scanning an event's printed QR (poster, flyer): it lands you on that
 * event's page to register or take a ticket, no separate app required.
 */
@Component({
  selector: 'app-qr-scanner',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="mx-auto max-w-md">
      <h1 class="text-xl font-bold text-slate-800">Scanner un QR code</h1>
      <p class="mt-1 text-sm text-slate-500">
        Visez le QR affiché sur l'affiche ou le flyer d'un événement : vous
        arrivez directement sur sa page pour vous inscrire ou prendre votre
        billet.
      </p>

      <div class="card mt-4 overflow-hidden p-0">
        @if (cameraSupported) {
          <div class="flex items-center justify-between p-3">
            <span class="text-sm text-slate-500">
              {{ scanning() ? 'Visez le QR code…' : 'Caméra en pause' }}
            </span>
            <button class="btn-ghost text-brand-700" (click)="toggleCamera()">
              {{ scanning() ? 'Arrêter' : 'Démarrer la caméra' }}
            </button>
          </div>
          @if (scanning()) {
            <video #video autoplay playsinline muted class="w-full bg-black"></video>
          }
        } @else {
          <p class="p-4 text-sm text-slate-500">
            Caméra non disponible sur ce navigateur. Utilisez l'appareil photo /
            l'application de scan de votre téléphone, ou collez le lien reçu ci-dessous.
          </p>
        }
      </div>

      @if (error()) {
        <p class="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
      }

      <div class="mt-4">
        <label class="form-label">Ou collez le lien du QR</label>
        <div class="flex gap-2">
          <input class="form-input" placeholder="https://…" [(ngModel)]="manualLink"
                 (keyup.enter)="go(manualLink)" />
          <button class="btn-primary" (click)="go(manualLink)">Ouvrir</button>
        </div>
      </div>
    </div>
  `,
})
export class QrScannerComponent implements OnDestroy {
  private router = inject(Router);

  cameraSupported = 'BarcodeDetector' in window && !!navigator.mediaDevices;
  scanning = signal(false);
  error = signal<string | null>(null);
  manualLink = '';

  private stream?: MediaStream;
  private raf = 0;
  private busy = false;

  ngOnDestroy(): void {
    this.stopCamera();
  }

  async toggleCamera(): Promise<void> {
    if (this.scanning()) {
      this.stopCamera();
      return;
    }
    this.error.set(null);
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
      });
      this.scanning.set(true);
      setTimeout(() => this.startLoop(), 100);
    } catch {
      this.error.set('Accès à la caméra refusé.');
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
          if (codes.length) {
            this.go(codes[0].rawValue);
            return;
          }
        } catch {
          /* ignore a failed frame, try the next one */
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

  /** Follows the scanned/pasted link — internal ones through the router, others as-is. */
  go(raw: string): void {
    const value = raw.trim();
    if (!value) return;
    this.stopCamera();
    try {
      const url = new URL(value, window.location.origin);
      if (url.origin === window.location.origin) {
        this.router.navigateByUrl(url.pathname + url.search + url.hash);
        return;
      }
      window.location.href = url.toString();
    } catch {
      this.error.set('Ce code ne correspond pas à un lien reconnu.');
    }
  }
}
