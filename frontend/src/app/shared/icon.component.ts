import { Component, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Tiny inline-SVG icon set (heroicons-style, MIT). CSP-safe, no dependency.
 * Sizing / colour come from the host element (`class="h-5 w-5 text-slate-500"`).
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  template: `<span class="block h-full w-full" [innerHTML]="svg()"></span>`,
  styles: [':host{display:inline-block;width:1.25rem;height:1.25rem;line-height:0}'],
})
export class IconComponent {
  private sanitizer = inject(DomSanitizer);
  name = input.required<IconName>();

  svg(): SafeHtml {
    const body = ICONS[this.name()] ?? ICONS['dot'];
    return this.sanitizer.bypassSecurityTrustHtml(
      `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
        stroke-linecap="round" stroke-linejoin="round"
        style="width:100%;height:100%;display:block" aria-hidden="true">${body}</svg>`,
    );
  }
}

export type IconName =
  | 'home' | 'calendar' | 'ticket' | 'qr' | 'users' | 'building' | 'chart'
  | 'login' | 'logout' | 'present' | 'check' | 'x' | 'badge' | 'card' | 'bell'
  | 'key' | 'scan' | 'repeat' | 'dot' | 'expand';

const ICONS: Record<string, string> = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
  calendar: '<rect x="3" y="4.5" width="18" height="16" rx="2"/><path d="M3 9h18M8 3v3M16 3v3"/>',
  ticket: '<path d="M4 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2 2 2 0 0 0 0 4 2 2 0 0 1-2 2H6a2 2 0 0 1-2-2 2 2 0 0 0 0-4Z"/><path d="M14 6v12"/>',
  qr: '<rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><path d="M14 14h3v3M20 14v6M17 20h3"/>',
  users: '<circle cx="9" cy="8" r="3.2"/><path d="M3.5 20a5.5 5.5 0 0 1 11 0"/><path d="M16 6.2a3 3 0 0 1 0 5.6M17 20a5.5 5.5 0 0 0-3-4.9"/>',
  building: '<rect x="5" y="3" width="14" height="18" rx="1.5"/><path d="M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2M10 21v-3h4v3"/>',
  chart: '<path d="M4 20V4M4 20h16"/><rect x="7" y="12" width="3" height="6"/><rect x="12" y="8" width="3" height="10"/><rect x="17" y="5" width="3" height="13"/>',
  login: '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><path d="M10 17l5-5-5-5M15 12H3"/>',
  logout: '<path d="M9 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4"/><path d="M16 17l5-5-5-5M21 12H9"/>',
  present: '<circle cx="12" cy="8" r="3.2"/><path d="M5 21a7 7 0 0 1 14 0"/>',
  check: '<path d="M5 13l4 4 10-11"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  badge: '<rect x="4" y="3" width="16" height="18" rx="2"/><circle cx="12" cy="10" r="2.6"/><path d="M8 18a4 4 0 0 1 8 0"/>',
  card: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18"/>',
  bell: '<path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6"/><path d="M10 20a2 2 0 0 0 4 0"/>',
  key: '<circle cx="8" cy="15" r="4"/><path d="M11 12 20 3M17 6l2 2M15 8l2 2"/>',
  scan: '<path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2M4 12h16"/>',
  repeat: '<path d="M4 9a5 5 0 0 1 5-5h9l-3-3M20 15a5 5 0 0 1-5 5H6l3 3"/>',
  dot: '<circle cx="12" cy="12" r="3"/>',
  expand: '<path d="M4 9V5a1 1 0 0 1 1-1h4M20 9V5a1 1 0 0 0-1-1h-4M4 15v4a1 1 0 0 0 1 1h4M20 15v4a1 1 0 0 1-1 1h-4"/>',
};
