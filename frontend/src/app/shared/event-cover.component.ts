import { Component, computed, input } from '@angular/core';

/** Same palette and same pick rule as the PDFs (backend `EventTheme`), so an event has one color everywhere. */
const PALETTE: { accent: string; dark: string }[] = [
  { accent: '#0E7A3C', dark: '#065829' }, // vert
  { accent: '#1D4E9E', dark: '#0F3572' }, // bleu
  { accent: '#6B3FA0', dark: '#4A2873' }, // violet
  { accent: '#0F766E', dark: '#07554F' }, // turquoise
  { accent: '#9F1239', dark: '#720725' }, // bordeaux
  { accent: '#B45309', dark: '#823800' }, // ocre
  { accent: '#4338CA', dark: '#2A2191' }, // indigo
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
      <img [src]="coverUrl()" alt="" class="h-full w-full object-cover" loading="lazy" />
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

  palette = computed(() => PALETTE[((javaHash(this.nom()) % PALETTE.length) + PALETTE.length) % PALETTE.length]);
}
