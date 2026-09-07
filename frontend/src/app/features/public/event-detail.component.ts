import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { EventsService } from '../events/events.service';
import { TicketsService } from '../tickets/tickets.service';
import { StandsService } from '../stands/stands.service';
import { Stand, StandReservation, StandType } from '../stands/stand.models';
import { EventPublic, EventTicket, TicketOrder } from '../events/event.models';
import { formatDateRange, formatDateTime, formatFcfa, formatTime } from '../../shared/format';
import { AuthService } from '../../core/auth.service';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink, FormsModule],
  template: `
    @if (event(); as e) {
      <div class="rounded-xl bg-slate-100">
        @if (e.coverUrl) { <img [src]="e.coverUrl" alt="" class="h-56 w-full rounded-xl object-cover" /> }
      </div>

      <div class="mt-4">
        @if (e.categoryNom) {
          <span class="badge bg-brand-50 text-brand-700">{{ e.categoryNom }}</span>
        }
        <h1 class="mt-2 text-2xl font-extrabold text-slate-900">{{ e.nom }}</h1>
        <p class="mt-1 text-slate-500">
          {{ range(e) }} · {{ e.lieu || '' }}{{ e.ville ? ', ' + e.ville : '' }}
        </p>
        <p class="text-sm text-slate-400">Organisé par {{ e.organizerNom }}</p>
      </div>

      @if (e.descriptionDetaillee || e.descriptionCourte) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Présentation</h2>
          <p class="mt-2 whitespace-pre-line text-sm text-slate-600">
            {{ e.descriptionDetaillee || e.descriptionCourte }}
          </p>
        </section>
      }

      <!-- BILLETTERIE -->
      <section class="card mt-6 p-5" id="billets">
        <h2 class="font-semibold text-slate-800">Billets</h2>
        @if (!tickets().length) {
          <p class="mt-2 text-sm text-slate-500">Aucun billet en vente pour le moment.</p>
        } @else if (order()) {
          <div class="mt-3 rounded-lg bg-slate-50 p-4 text-sm">
            <p class="font-medium text-slate-700">Commande {{ order()!.reference }}</p>
            <p class="text-slate-500">
              {{ order()!.montantFormatte }} —
              <span class="font-semibold">{{ order()!.statut }}</span>
            </p>
            @if (order()!.statut === 'EN_ATTENTE') {
              <p class="mt-1 text-xs text-amber-700">
                Quota réservé jusqu'à {{ dt(order()!.expireLe) }}. Finalisez le paiement.
              </p>
              <button class="btn-primary mt-3" (click)="pay()">
                Payer (paiement simulé — sandbox)
              </button>
            } @else if (order()!.statut === 'PAYEE') {
              <p class="mt-2 text-green-700">Paiement confirmé — vos billets sont disponibles.</p>
              <a routerLink="/tableau-de-bord/billets" class="btn-primary mt-2 inline-flex">Voir mes billets</a>
            }
          </div>
        } @else {
          <table class="mt-3 w-full text-sm">
            <tbody class="divide-y divide-slate-100">
              @for (t of tickets(); track t.id) {
                <tr>
                  <td class="py-2">
                    <p class="font-medium text-slate-700">{{ t.nom }}</p>
                    <p class="text-xs text-slate-400">
                      {{ t.portee === 'ACTIVITE' ? 'Accès : ' + activityTitles(t) : 'Accès à tout l\\'événement' }}
                      · {{ t.quantiteRestante }} disponibles
                    </p>
                  </td>
                  <td class="py-2 text-right font-semibold">{{ fcfa(t.prixMontant) }}</td>
                  <td class="py-2 pl-3 text-right">
                    <input type="number" min="0" [max]="maxFor(t)" class="form-input w-16"
                           [ngModel]="qty()[t.id] || 0" (ngModelChange)="setQty(t.id, $event)" />
                  </td>
                </tr>
              }
            </tbody>
          </table>
          @if (buyError()) { <p class="mt-2 text-sm text-red-700">{{ buyError() }}</p> }
          <button class="btn-primary mt-4" [disabled]="totalQty() === 0" (click)="createOrder()">
            @if (auth.isAuthenticated()) { Commander ({{ totalQty() }} billet(s)) }
            @else { Se connecter pour commander }
          </button>
        }
      </section>

      <!-- STANDS -->
      @if (e.standsActifs) {
        <section class="card mt-6 p-5" id="stands">
          <h2 class="font-semibold text-slate-800">Stands</h2>
          @if (standReservation()) {
            <div class="mt-3 rounded-lg bg-slate-50 p-4 text-sm">
              <p class="font-medium text-slate-700">
                Réservation {{ standReservation()!.numeroReservation }} — Stand
                {{ standReservation()!.standNumero }}
              </p>
              <p class="text-slate-500">
                {{ standReservation()!.montantFormatte }} —
                <span class="font-semibold">{{ standReservation()!.statut }}</span>
              </p>
              @if (standReservation()!.statut === 'RESERVE_TEMP') {
                <p class="mt-1 text-xs text-amber-700">
                  Stand bloqué jusqu'à {{ dt(standReservation()!.dateLimitePaiement) }}.
                </p>
                <button class="btn-primary mt-3" (click)="payStand()">Payer (sandbox)</button>
              } @else if (standReservation()!.statut === 'CONFIRME') {
                <p class="mt-2 text-green-700">Stand confirmé.</p>
                <a routerLink="/tableau-de-bord/stands" class="btn-primary mt-2 inline-flex">Mes stands</a>
              }
            </div>
          } @else {
            @for (t of standTypes(); track t.id) {
              <div class="mt-3 rounded-lg border border-slate-100 p-3 text-sm">
                <div class="flex items-center justify-between">
                  <p class="font-medium text-slate-800">{{ t.nom }} — {{ fcfa(t.prixMontant) }}</p>
                  <span class="text-slate-400">{{ t.quantiteRestante }} disponibles</span>
                </div>
                @if (t.dimensions || t.equipements) {
                  <p class="text-slate-400">{{ t.dimensions }}{{ t.equipements ? ' · ' + t.equipements : '' }}</p>
                }
                <div class="mt-2 flex flex-wrap gap-1">
                  @for (s of standsOfType(t.id); track s.id) {
                    <button type="button"
                            class="rounded border px-2 py-1 text-xs"
                            [class.border-brand-500]="s.disponible"
                            [class.text-brand-700]="s.disponible"
                            [class.border-slate-200]="!s.disponible"
                            [class.text-slate-300]="!s.disponible"
                            [disabled]="!s.disponible"
                            (click)="reserveStand(s)">
                      {{ s.numero }}
                    </button>
                  }
                </div>
              </div>
            }
            @if (standError()) { <p class="mt-2 text-sm text-red-700">{{ standError() }}</p> }
            @if (!auth.hasPermission('STAND_RESERVE')) {
              <p class="mt-2 text-xs text-slate-400">
                La réservation de stands est réservée aux comptes structure.
              </p>
            }
          }
        </section>
      }

      @if (e.hasActivities && e.programme.length) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Programme</h2>
          <ol class="mt-3 space-y-3">
            @for (a of e.programme; track a.id) {
              <li class="flex gap-3 text-sm">
                <span class="w-14 shrink-0 font-semibold text-brand-700">{{ time(a.dateDebut) }}</span>
                <div>
                  <p class="font-medium text-slate-800">{{ a.titre }}</p>
                  <p class="text-slate-400">
                    {{ a.salle }}{{ a.intervenant ? ' · ' + a.intervenant : '' }}
                  </p>
                </div>
              </li>
            }
          </ol>
        </section>
      }

      @if (e.intervenants.length) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Intervenants</h2>
          <div class="mt-3 grid gap-3 sm:grid-cols-2">
            @for (s of e.intervenants; track s.id) {
              <div class="rounded-lg border border-slate-100 p-3 text-sm">
                <p class="font-medium text-slate-800">{{ s.nom }}</p>
                <p class="text-slate-400">{{ s.titre }}{{ s.organisation ? ' · ' + s.organisation : '' }}</p>
              </div>
            }
          </div>
        </section>
      }

      @if (e.partenaires.length) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Partenaires</h2>
          <ul class="mt-3 flex flex-wrap gap-2 text-sm">
            @for (p of e.partenaires; track p.id) {
              <li class="badge bg-slate-100 text-slate-600">{{ p.nom }}</li>
            }
          </ul>
        </section>
      }

      <section class="card mt-6 p-5">
        <h2 class="font-semibold text-slate-800">Informations pratiques</h2>
        <dl class="mt-3 grid gap-2 text-sm sm:grid-cols-2">
          <div><dt class="text-slate-400">Dates</dt><dd>{{ range(e) }}</dd></div>
          <div><dt class="text-slate-400">Lieu</dt><dd>{{ e.lieu || '—' }}{{ e.adresse ? ', ' + e.adresse : '' }}</dd></div>
          <div><dt class="text-slate-400">Ville</dt><dd>{{ e.ville || '—' }}, {{ e.pays }}</dd></div>
          <div><dt class="text-slate-400">Contact</dt><dd>{{ e.contactEmail || e.contactTelephone || '—' }}</dd></div>
        </dl>
        @if (e.conditionsParticipation) {
          <p class="mt-3 text-sm text-slate-500"><b>Conditions :</b> {{ e.conditionsParticipation }}</p>
        }
      </section>

      <p class="mt-6"><a routerLink="/" class="text-sm text-brand-700">← Tous les événements</a></p>
    } @else if (error()) {
      <p class="py-16 text-center text-slate-500">{{ error() }}</p>
    } @else {
      <p class="py-16 text-center text-slate-400">Chargement…</p>
    }
  `,
})
export class EventDetailComponent {
  private events = inject(EventsService);
  private ticketsService = inject(TicketsService);
  private standsService = inject(StandsService);
  private router = inject(Router);
  auth = inject(AuthService);

  slug = input.required<string>();
  event = signal<EventPublic | null>(null);
  tickets = signal<EventTicket[]>([]);
  order = signal<TicketOrder | null>(null);
  error = signal<string | null>(null);
  buyError = signal<string | null>(null);
  qty = signal<Record<string, number>>({});
  standTypes = signal<StandType[]>([]);
  stands = signal<Stand[]>([]);
  standReservation = signal<StandReservation | null>(null);
  standError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const slug = this.slug();
      if (!slug) return;
      this.events.publicBySlug(slug).subscribe({
        next: (e) => this.event.set(e),
        error: () => this.error.set("Cet événement n'est pas disponible."),
      });
      this.ticketsService.publicTickets(slug).subscribe({
        next: (t) => this.tickets.set(t.filter((x) => x.enVente || x.quantiteRestante > 0)),
        error: () => this.tickets.set([]),
      });
      this.standsService.publicTypes(slug).subscribe({
        next: (t) => this.standTypes.set(t),
        error: () => this.standTypes.set([]),
      });
      this.standsService.publicStands(slug).subscribe({
        next: (s) => this.stands.set(s),
        error: () => this.stands.set([]),
      });
    });
  }

  standsOfType = (typeId: string) => this.stands().filter((s) => s.standTypeId === typeId);

  reserveStand(s: Stand): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/connexion'], { queryParams: { redirect: this.router.url } });
      return;
    }
    this.standError.set(null);
    this.standsService.reserve({ eventId: this.event()!.id, standId: s.id }).subscribe({
      next: (r) => this.standReservation.set(r),
      error: (err: HttpErrorResponse) =>
        this.standError.set((err.error as ApiError)?.message ?? 'Réservation impossible.'),
    });
  }

  payStand(): void {
    this.standsService.paySandbox(this.standReservation()!.id).subscribe({
      next: (r) => this.standReservation.set(r),
      error: (err: HttpErrorResponse) =>
        this.standError.set((err.error as ApiError)?.message ?? 'Paiement impossible.'),
    });
  }

  range = (e: EventPublic) => formatDateRange(e.dateDebut, e.dateFin);
  dt = (iso?: string) => formatDateTime(iso);
  time = (iso?: string) => formatTime(iso);
  fcfa = (n?: number) => formatFcfa(n);
  activityTitles = (t: EventTicket) => t.activites.map((a) => a.titre).join(', ');
  maxFor = (t: EventTicket) => Math.min(t.quantiteRestante, t.limiteParUtilisateur);

  setQty(id: string, value: number): void {
    this.qty.set({ ...this.qty(), [id]: Math.max(0, Math.floor(value || 0)) });
  }
  totalQty(): number {
    return Object.values(this.qty()).reduce((a, b) => a + b, 0);
  }

  createOrder(): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/connexion'], { queryParams: { redirect: this.router.url } });
      return;
    }
    this.buyError.set(null);
    const lignes = Object.entries(this.qty())
      .filter(([, q]) => q > 0)
      .map(([eventTicketId, quantite]) => ({ eventTicketId, quantite }));
    this.ticketsService
      .createOrder({ eventId: this.event()!.id, lignes })
      .subscribe({
        next: (o) => this.order.set(o),
        error: (err: HttpErrorResponse) =>
          this.buyError.set((err.error as ApiError)?.message ?? 'Commande impossible.'),
      });
  }

  pay(): void {
    this.ticketsService.paySandbox(this.order()!.id).subscribe({
      next: (o) => this.order.set(o),
      error: (err: HttpErrorResponse) =>
        this.buyError.set((err.error as ApiError)?.message ?? 'Paiement impossible.'),
    });
  }
}
