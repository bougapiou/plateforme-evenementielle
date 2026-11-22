import { Component, HostListener, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { IconComponent } from '../../shared/icon.component';
import { TicketsService } from './tickets.service';

/** Ce dont le visualiseur a besoin d'un billet. */
export interface BilletVisualisable {
  id: string;
  numero: string;
  eventNom?: string;
  categorieNom?: string;
}

/** Télécharge le PDF d'un billet (nom de fichier « billet-<numéro>.pdf »). */
export function enregistrerPdf(blob: Blob, numero: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `billet-${numero}.pdf`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

/**
 * Fenêtre « Voir le billet » : aperçu du PDF dans la page, avec Télécharger et
 * Ouvrir dans un onglet (certains navigateurs mobiles n'affichent pas les PDF intégrés).
 */
@Component({
  selector: 'app-ticket-viewer',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="fixed inset-0 z-[410] flex items-end justify-center bg-neutre-900/60 p-0 backdrop-blur-sm sm:items-center sm:p-6"
         role="dialog" aria-modal="true" [attr.aria-label]="'Billet ' + billet().numero" (click)="fermer.emit()">
      <div class="flex h-[92vh] w-full max-w-5xl flex-col overflow-hidden rounded-t-2xl bg-white shadow-xl sm:h-[86vh] sm:rounded-2xl"
           (click)="$event.stopPropagation()">
        <header class="flex flex-wrap items-center justify-between gap-3 border-b border-neutre-200 px-4 py-3 sm:px-5">
          <div class="min-w-0">
            <p class="surtitre text-vert-700">Billet électronique</p>
            <p class="truncate font-display text-lg font-bold text-neutre-900">
              {{ billet().categorieNom || 'Billet' }}<span class="font-normal text-neutre-600"> · N° {{ billet().numero }}</span>
            </p>
          </div>
          <div class="flex items-center gap-2">
            <button type="button" class="btn-primary" [disabled]="!blob()" (click)="telecharger()">
              <app-icon name="download" class="h-4 w-4" aria-hidden="true" /> Télécharger
            </button>
            <button type="button" class="btn-secondary hidden sm:inline-flex" [disabled]="!blob()" (click)="ouvrirOnglet()">
              Ouvrir dans un onglet
            </button>
            <button type="button" class="btn-ghost px-2" (click)="fermer.emit()" aria-label="Fermer">
              <app-icon name="x" class="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
        </header>

        <div class="relative flex-1 bg-neutre-100">
          @if (url(); as u) {
            <iframe [src]="u" class="h-full w-full border-0" [title]="'Billet ' + billet().numero"></iframe>
          } @else if (erreur()) {
            <p class="p-8 text-center text-rouge-700">{{ erreur() }}</p>
          } @else {
            <div class="flex h-full items-center justify-center text-neutre-600">Préparation du billet…</div>
          }
        </div>

        <footer class="flex flex-wrap items-center justify-between gap-2 border-t border-neutre-200 bg-neutre-25 px-4 py-3 text-xs text-neutre-600 sm:px-5">
          <span>Pas besoin d'imprimer : présentez le QR code sur votre téléphone.</span>
          <button type="button" class="lien sm:hidden" [disabled]="!blob()" (click)="ouvrirOnglet()">
            L'aperçu ne s'affiche pas ? Ouvrir le PDF
          </button>
        </footer>
      </div>
    </div>
  `,
})
export class TicketViewerComponent implements OnInit, OnDestroy {
  private service = inject(TicketsService);
  private sanitizer = inject(DomSanitizer);

  billet = input.required<BilletVisualisable>();
  fermer = output<void>();

  blob = signal<Blob | null>(null);
  url = signal<SafeResourceUrl | null>(null);
  erreur = signal<string | null>(null);
  private objectUrl: string | null = null;

  ngOnInit(): void {
    this.service.pdfBlob(this.billet().id).subscribe({
      next: (b) => {
        const pdf = b.type === 'application/pdf' ? b : new Blob([b], { type: 'application/pdf' });
        this.blob.set(pdf);
        this.objectUrl = URL.createObjectURL(pdf);
        this.url.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl + '#view=FitH'));
      },
      error: () => this.erreur.set('Le billet n’a pas pu être chargé. Réessayez.'),
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.fermer.emit();
  }

  telecharger(): void {
    const b = this.blob();
    if (b) enregistrerPdf(b, this.billet().numero);
  }

  ouvrirOnglet(): void {
    if (this.objectUrl) window.open(this.objectUrl, '_blank', 'noopener');
  }

  ngOnDestroy(): void {
    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
  }
}
