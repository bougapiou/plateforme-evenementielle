import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/icon.component';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { TicketsService } from './tickets.service';
import { PaymentsService } from '../payments/payments.service';
import { MyTicket, TicketOrder } from '../events/event.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { EventCoverComponent } from '../../shared/event-cover.component';
import { formatDate, formatDateTime } from '../../shared/format';
import { TicketViewerComponent, enregistrerPdf } from './ticket-viewer.component';

const ZONE = 'Africa/Ouagadougou';

/** Mes billets : regroupés par événement, avec ce que chaque billet donne, son QR, « Voir » et « Télécharger ». */
@Component({
  selector: 'app-my-tickets',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent, IconComponent, EventCoverComponent, TicketViewerComponent],
  template: `
    <h1 class="text-[1.625rem] text-neutre-900">Mes billets</h1>
    <p class="mt-1 text-sm text-neutre-600">
      Présentez le QR code à l'entrée — pas besoin d'imprimer. Vous pouvez aussi voir ou télécharger chaque billet en PDF.
    </p>

    @for (g of parEvenement(); track g.eventId) {
      <section class="mt-8">
        <div class="flex items-center gap-3">
          <app-event-cover class="h-12 w-20 shrink-0 rounded-md" [nom]="g.eventNom" [coverUrl]="g.cover" />
          <div class="min-w-0">
            <h2 class="truncate text-lg text-neutre-900">{{ g.eventNom }}</h2>
            <p class="text-sm text-neutre-600">{{ g.billets.length }} billet{{ g.billets.length > 1 ? 's' : '' }}</p>
          </div>
        </div>

        <div class="mt-4 grid gap-4 lg:grid-cols-2">
          @for (t of g.billets; track t.id) {
            <article class="flex flex-col overflow-hidden rounded-xl border border-neutre-200 bg-white shadow-xs">
              <div class="flex gap-4 p-4">
                <button type="button" class="shrink-0 rounded-lg border border-neutre-200 bg-white p-1.5 transition hover:border-vert-600"
                        (click)="apercu.set(t)" [attr.aria-label]="'Voir le billet ' + t.numero">
                  @if (qr()[t.id]; as src) {
                    <img [src]="src" alt="" class="h-28 w-28" />
                  } @else {
                    <span class="grid h-28 w-28 place-items-center text-xs text-neutre-400">QR…</span>
                  }
                </button>

                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="badge" [class]="type(t).cls">{{ type(t).label }}</span>
                    <app-status-badge [value]="t.statut" />
                  </div>
                  <p class="mt-2 font-display font-bold leading-snug text-neutre-900">{{ titre(t) }}</p>
                  @if (t.categorieDescription && (t.sessions?.length ?? 0) !== 1) {
                    <p class="mt-1 text-sm text-neutre-600">{{ t.categorieDescription }}</p>
                  }
                  @if (t.sessions?.length === 1) {
                    <p class="mt-1 text-sm text-neutre-700">
                      {{ jour(t.sessions![0].dateDebut) }} · {{ heures(t.sessions![0]) }}
                      @if (t.sessions![0].salle) { · {{ t.sessions![0].salle }} }
                    </p>
                  } @else if (!t.sessions?.length) {
                    <p class="mt-1 text-sm text-neutre-700">À partir du {{ date(t.eventDateDebut) }}</p>
                  }
                  <p class="mt-2 font-mono text-xs text-neutre-500">N° {{ t.numero }}</p>
                </div>
              </div>

              @if ((t.sessions?.length ?? 0) > 1) {
                <div class="border-t border-neutre-100 px-4 py-3">
                  <p class="surtitre text-neutre-600">Sessions incluses</p>
                  <ul class="mt-2 space-y-1.5 text-sm">
                    @for (s of t.sessions; track s.id) {
                      <li class="flex gap-3">
                        <span class="w-24 shrink-0 font-display text-xs font-bold uppercase leading-5 text-vert-700">{{ jour(s.dateDebut) }}</span>
                        <span class="w-24 shrink-0 tabular-nums text-neutre-700">{{ heures(s) }}</span>
                        <span class="min-w-0 text-neutre-900">{{ s.titre }}</span>
                      </li>
                    }
                  </ul>
                </div>
              }

              <div class="mt-auto flex gap-2 border-t border-neutre-100 bg-neutre-25 p-3">
                <button type="button" class="btn-secondary flex-1" (click)="apercu.set(t)">
                  <app-icon name="eye" class="h-4 w-4" aria-hidden="true" /> Voir le billet
                </button>
                <button type="button" class="btn-ghost flex-1 border border-neutre-300" (click)="telecharger(t)">
                  <app-icon name="download" class="h-4 w-4" aria-hidden="true" /> Télécharger
                </button>
              </div>
            </article>
          }
        </div>
      </section>
    } @empty {
      <div class="mt-6 rounded-xl border border-dashed border-neutre-300 bg-white p-10 text-center">
        <p class="font-display font-semibold text-neutre-900">Vous n'avez encore aucun billet.</p>
        <a routerLink="/" class="btn-primary mt-4">
          <app-icon name="calendar" class="h-4 w-4" aria-hidden="true" /> Découvrir les événements
        </a>
      </div>
    }

    <h2 class="mt-12 text-lg text-neutre-900">Mes commandes</h2>
    <div class="mt-3 space-y-2">
      @for (o of orders(); track o.id) {
        <div class="card flex items-center justify-between p-4 text-sm">
          <div>
            <p class="font-medium text-neutre-800">
              {{ o.eventNom }} — {{ o.montantTotal > 0 ? o.montantFormatte : 'Gratuit' }}
            </p>
            <p class="text-neutre-500">
              {{ o.reference }} · {{ dt(o.createdAt) }}
              @if (o.statut === 'EN_ATTENTE') { · expire {{ dt(o.expireLe) }} }
            </p>
          </div>
          <div class="flex items-center gap-2">
            <app-status-badge [value]="o.statut" />
            @if (o.statut === 'EN_ATTENTE') {
              <button class="btn-ghost text-green-700" (click)="pay(o)">Payer</button>
              <button class="btn-ghost text-red-700" (click)="cancel(o)">Annuler</button>
            }
          </div>
        </div>
      } @empty {
        <p class="text-sm text-neutre-500">Aucune commande.</p>
      }
    </div>

    @if (apercu(); as t) {
      <app-ticket-viewer [billet]="t" (fermer)="apercu.set(null)" />
    }
  `,
})
export class MyTicketsComponent implements OnDestroy {
  private service = inject(TicketsService);
  private paymentsService = inject(PaymentsService);
  private sanitizer = inject(DomSanitizer);

  tickets = signal<MyTicket[]>([]);
  orders = signal<TicketOrder[]>([]);
  qr = signal<Record<string, SafeUrl>>({});
  apercu = signal<MyTicket | null>(null);
  private objectUrls: string[] = [];

  parEvenement = computed(() => {
    const groupes = new Map<string, { eventId: string; eventNom: string; cover?: string; billets: MyTicket[] }>();
    for (const t of this.tickets()) {
      const g = groupes.get(t.eventId) ?? { eventId: t.eventId, eventNom: t.eventNom, cover: t.eventCoverUrl, billets: [] };
      g.billets.push(t);
      groupes.set(t.eventId, g);
    }
    // Dans un événement : entrée d'abord, puis pass, puis sessions par date.
    const rang = (t: MyTicket) => (!t.sessions?.length ? 0 : t.sessions.length > 1 ? 1 : 2);
    for (const g of groupes.values()) {
      g.billets.sort((a, b) => rang(a) - rang(b)
        || (a.sessions?.[0]?.dateDebut ?? '').localeCompare(b.sessions?.[0]?.dateDebut ?? ''));
    }
    return [...groupes.values()];
  });

  date = (iso?: string) => formatDate(iso);
  dt = (iso?: string) => formatDateTime(iso);

  private fmt = (iso: string, o: Intl.DateTimeFormatOptions) =>
    new Intl.DateTimeFormat('fr-FR', { ...o, timeZone: ZONE }).format(new Date(iso));
  jour = (iso: string) => this.fmt(iso, { weekday: 'short', day: 'numeric', month: 'short' }).replace(/\./g, '');
  heures = (s: { dateDebut: string; dateFin?: string }) => {
    const h = (iso: string) => this.fmt(iso, { hour: '2-digit', minute: '2-digit' });
    return s.dateFin ? `${h(s.dateDebut)} – ${h(s.dateFin)}` : h(s.dateDebut);
  };

  type(t: MyTicket): { label: string; cls: string } {
    const n = t.sessions?.length ?? 0;
    if (n > 1) return { label: `Pass · ${n} sessions`, cls: 'bg-or-50 text-or-700 ring-1 ring-or-200' };
    if (n === 1) return { label: 'Session', cls: 'bg-bleu-50 text-bleu-800 ring-1 ring-bleu-100' };
    return { label: 'Entrée', cls: 'bg-vert-50 text-vert-800 ring-1 ring-vert-200' };
  }

  /** Billet d'une session : le titre de la session plutôt que « Accès — … ». */
  titre = (t: MyTicket) => (t.sessions?.length === 1 ? t.sessions[0].titre : t.categorieNom);

  constructor() {
    this.reload();
  }

  ngOnDestroy(): void {
    this.objectUrls.forEach((u) => URL.revokeObjectURL(u));
  }

  reload(): void {
    this.service.myTickets().subscribe((t) => {
      this.tickets.set(t);
      t.forEach((ticket) =>
        this.service.qrBlob(ticket.id).subscribe((blob) => {
          const url = URL.createObjectURL(blob);
          this.objectUrls.push(url);
          this.qr.set({ ...this.qr(), [ticket.id]: this.sanitizer.bypassSecurityTrustUrl(url) });
        }),
      );
    });
    this.service.myOrders().subscribe((p) => this.orders.set(p.content));
  }

  telecharger(t: MyTicket): void {
    this.service.pdfBlob(t.id).subscribe((blob) => enregistrerPdf(blob, t.numero));
  }

  pay(o: TicketOrder): void {
    this.paymentsService.payOrRedirect('TICKET_ORDER', o.id).subscribe(() => this.reload());
  }
  cancel(o: TicketOrder): void {
    this.service.cancelOrder(o.id).subscribe(() => this.reload());
  }
}
