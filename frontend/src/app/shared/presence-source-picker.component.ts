import { Component, input, output } from '@angular/core';
import { PRESENCE_SOURCES, PresenceSource } from './presence';

/** Segmented control to pick where the presence numbers come from: tickets (QR), sensors, or both added up. */
@Component({
  selector: 'app-presence-source-picker',
  standalone: true,
  template: `
    <div role="radiogroup" aria-label="Source du comptage"
         class="inline-flex max-w-full rounded-xl p-1"
         [class]="dark() ? 'bg-slate-800/80' : 'bg-slate-100'">
      @for (s of sources; track s.value) {
        <button type="button" role="radio" [attr.aria-checked]="value() === s.value" [title]="s.description"
                class="whitespace-nowrap rounded-lg px-2.5 py-1.5 text-xs font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 sm:px-3 sm:text-sm"
                [class]="buttonClass(s.value)" (click)="changed.emit(s.value)">
          {{ s.label }}
        </button>
      }
    </div>
  `,
})
export class PresenceSourcePickerComponent {
  value = input.required<PresenceSource>();
  dark = input(false);
  changed = output<PresenceSource>();

  sources = PRESENCE_SOURCES;

  buttonClass(source: PresenceSource): string {
    const on = this.value() === source;
    if (this.dark()) {
      return on ? 'bg-white text-slate-900 shadow' : 'text-slate-300 hover:text-white';
    }
    return on ? 'bg-white text-brand-700 shadow-sm' : 'text-slate-500 hover:text-slate-800';
  }
}
