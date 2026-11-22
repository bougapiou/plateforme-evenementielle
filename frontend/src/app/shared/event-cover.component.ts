import { Component, computed, input } from '@angular/core';

/** Palette alignée sur la charte graphique (vert, bleu, ocre, neutres, or ; pas de rouge, réservé au critique). Same palette and same pick rule as the PDFs (backend `EventTheme`), so an event has one color everywhere. */
const PALETTE: { accent: string; dark: string }[] = [
  { accent: '#00843B', dark: '#045527' }, // vert
  { accent: '#2A6D9B', dark: '#173F5F' }, // bleu
  { accent: '#B07800', dark: '#7D5500' }, // ocre
  { accent: '#026C31', dark: '#04411F' }, // vert foncé
  { accent: '#635C55', dark: '#332E2A' }, // neutre
  { accent: '#173F5F', dark: '#0D2639' }, // bleu nuit
  { accent: '#A39300', dark: '#5C5300' }, // or
];

/** Java's String.hashCode(), so the pick matches the backend exactly. */
function javaHash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

/**
 * The event's cover photo — or, when it has none, a generated cover (colored backdrop, soft circles,
 * fine stripes) whose color depends on the event. Fills its host: size it with a class on `<app-event-cover>`.
 */
@Component({
  selector: 'app-event-cover',
  standalone: true,
  host: { class: 'block overflow-hidden' },
  template: `
    @if (coverUrl()) {
      <img [src]="coverUrl()" alt="" class="h-full w-full" [class.object-cover]="!contain()" [class.object-contain]="contain()" loading="lazy" />
    } @else {
      <div class="relative h-full w-full overflow-hidden" [style.background]="palette().dark" aria-hidden="true">
        <div class="absolute -right-10 -top-16 h-56 w-56 rounded-full opacity-25"
             [style.background]="palette().accent"></div>
        <div class="absolute -right-2 -top-8 h-32 w-32 rounded-full bg-white opacity-10"></div>
        <div class="absolute inset-0"
             style="background:repeating-linear-gradient(45deg,rgba(255,255,255,.07) 0 1px,transparent 1px 16px)"></div>
        <div class="absolute inset-0" style="background:linear-gradient(to top,rgba(0,0,0,.4),transparent 65%)"></div>
      </div>
    }
  `,
})
export class EventCoverComponent {
  nom = input.required<string>();
  coverUrl = input<string | null | undefined>(null);
  /** Affiche l'image entière (affiches avec texte) au lieu de la recadrer. */
  contain = input(false);

  palette = computed(() => PALETTE[((javaHash(this.nom()) % PALETTE.length) + PALETTE.length) % PALETTE.length]);
}
