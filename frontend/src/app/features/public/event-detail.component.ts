import { Component, OnDestroy, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { EventsService } from '../events/events.service';
import { IssuedTicket, TicketsService } from '../tickets/tickets.service';
import { StandsService } from '../stands/stands.service';
import { StructuresService } from '../structures/structures.service';
import { StructureSummary } from '../structures/structure.models';
import { RegistrationsService } from '../registrations/registrations.service';
import { Registration } from '../registrations/registration.models';
import { PaymentsService } from '../payments/payments.service';
import { PhoneInputComponent } from '../../shared/phone-input.component';
import { EventCoverComponent } from '../../shared/event-cover.component';
import { IconComponent } from '../../shared/icon.component';
import { ProgrammeChartComponent } from '../../shared/programme-chart.component';
import { Stand, StandReservation, StandType } from '../stands/stand.models';
import { Activity, EventPublic, EventTicket, IdentiteRequise, MyTicket } from '../events/event.models';
import {
  formatDateRange,
  formatDateTime,
  formatFcfa,
  formatTime,
  priceLabel,
} from '../../shared/format';
import { AuthService } from '../../core/auth.service';
import { ApiError } from '../../core/models';

import { statutLabel } from '../../shared/status-badge.component';
import { buildIcs, downloadIcs, googleCalendarUrl, loadAgenda, saveAgenda } from '../../shared/agenda';
@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, PhoneInputComponent, EventCoverComponent, IconComponent, ProgrammeChartComponent],
  template: `
    @if (event(); as e) {
      <app-event-cover class="h-56 rounded-xl" [nom]="e.nom" [coverUrl]="e.coverUrl" />

      <div class="mt-4">
        @if (e.categoryNom) {
          <span class="badge bg-brand-50 text-brand-700">{{ e.categoryNom }}</span>
        }
        <h1 class="mt-2 text-2xl font-extrabold text-slate-900">{{ e.nom }}</h1>
        <p class="mt-1 text-slate-500">{{ range(e) }} · {{ e.lieu || '' }}{{ e.ville ? ', ' + e.ville : '' }}</p>
        <p class="text-sm text-slate-400">Organisé par {{ e.organizerNom }}</p>
        <a [routerLink]="['/evenements', slug(), 'flux']"
           class="mt-1 inline-block text-sm text-brand-700 hover:underline">
          Présence en direct →
        </a>
        <span class="ml-3 inline-flex gap-3 text-sm">
          <a [href]="whatsappUrl(e)" target="_blank" rel="noopener" class="text-green-700 hover:underline">
            Partager sur WhatsApp
          </a>
          <button type="button" class="text-brand-700 hover:underline" (click)="copyShareLink(e)">
            {{ copied() ? 'Lien copié ✓' : 'Copier le lien' }}
          </button>
        </span>
      </div>

      <!-- Raccourci "en un clic" : uniquement le cas le plus simple (billet
           gratuit unique, sans formulaire, sans compte). Tout le reste passe
           par la section « Participer à l'événement » plus bas. -->
      @if (quickTicket(); as t) {
        <section class="card mt-4 p-4" id="obtenir-billet">
          <p class="text-sm text-slate-600">
            Billet : <span class="font-medium">{{ t.nom }}</span>
            <span class="text-slate-400">— Gratuit</span>
          </p>
          @if (ticketReceipts().length) {
            <p class="mt-2 rounded-lg bg-green-50 px-3 py-2 text-sm text-green-700">
              Billet téléchargé. Pas besoin de l'imprimer : la version numérique,
              affichée sur votre téléphone, suffit pour l'entrée.
            </p>
            <button type="button" class="btn-ghost mt-2 text-brand-700"
                    (click)="downloadTicket(ticketReceipts()[0])">
              Télécharger à nouveau
            </button>
          } @else {
            <div class="mt-2 max-w-sm">
              <label class="form-label">Téléphone *</label>
              <app-phone-input [(ngModel)]="guestPhone" />
            </div>
            @if (error()) { <p class="mt-2 text-sm text-red-700">{{ error() }}</p> }
            <button type="button" class="btn-primary mt-3" [disabled]="submitting()" (click)="submit()">
              {{ submitting() ? 'Un instant…' : 'Obtenir mon billet' }}
            </button>
          }
        </section>
      }

      @if (e.descriptionDetaillee || e.descriptionCourte) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Présentation</h2>
          <p class="mt-2 whitespace-pre-line text-sm text-slate-600">
            {{ e.descriptionDetaillee || e.descriptionCourte }}
          </p>
        </section>
      }

      <!-- PARTICIPER -->
      <section class="card mt-6 p-5" id="participer">
        <h2 class="font-semibold text-slate-800">Participer à l'événement</h2>

        @if (registration(); as r) {
          <div class="mt-3 rounded-lg bg-slate-50 p-4 text-sm">
            <p class="font-medium text-slate-700">Inscription {{ r.reference }}</p>
            <p class="text-slate-500">Statut : <span class="font-semibold">{{ statutLabel(r.statut) }}</span></p>
            @if (r.ticketOrderStatut === 'EN_ATTENTE') {
              <p class="mt-1 text-xs text-amber-700">Finalisez le paiement pour valider vos billets.</p>
              <button class="btn-primary mt-3" (click)="pay(r)">Payer (paiement simulé)</button>
            } @else if (r.statut === 'CONFIRMEE') {
              <p class="mt-2 text-green-700">Inscription confirmée.</p>
            } @else if (r.statut === 'EN_ATTENTE') {
              <p class="mt-2 text-amber-700">En attente de validation par l'organisateur.</p>
            }

            @if (ticketReceipts().length) {
              <div class="mt-4 space-y-3">
                <p class="rounded-lg bg-green-50 px-3 py-2 text-xs text-green-700">
                  Votre billet est prêt. Pas besoin de l'imprimer : la version numérique,
                  affichée sur votre téléphone, suffit pour l'entrée.
                </p>
                @for (t of ticketReceipts(); track t.id) {
                  <div class="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3">
                    @if (ticketQr()[t.id]; as qr) {
                      <img [src]="qr" alt="QR du billet" class="h-16 w-16 rounded border border-slate-100" />
                    }
                    <div class="flex-1">
                      <p class="font-medium text-slate-800">Billet {{ t.numero }}</p>
                      <p class="text-slate-400">{{ t.categorieNom }}</p>
                    </div>
                    <button type="button" class="btn-primary" (click)="downloadTicket(t)">
                      Télécharger
                    </button>
                  </div>
                }
              </div>
            }

            @if (auth.isGuest()) {
              <div class="mt-3 rounded-lg border border-brand-100 bg-brand-50 p-3">
                <p class="font-medium text-brand-800">Créez votre compte</p>
                <p class="mt-1 text-xs text-slate-600">
                  Choisissez un mot de passe pour retrouver vos billets, inscriptions
                  et factures sur tous vos appareils.
                </p>
                <a routerLink="/finaliser-compte" class="btn-primary mt-2 inline-flex">
                  Créer un compte
                </a>
              </div>
            } @else if (auth.isFullyAuthenticated() && r.statut === 'CONFIRMEE') {
              <a routerLink="/tableau-de-bord/inscriptions" class="btn-primary mt-2 inline-flex">
                Mes inscriptions
              </a>
            }
          </div>
        } @else if (quickTicket()) {
          <p class="mt-3 text-sm text-slate-500">
            Utilisez le bouton en haut de la page pour obtenir votre billet.
          </p>
        } @else {
          <div class="mt-3 space-y-3">
            @if (!auth.isAuthenticated()) {
              <div class="rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
                Pas besoin de compte pour vous inscrire. Renseignez vos coordonnées ;
                vous pourrez créer un compte après le paiement pour retrouver vos billets.
                <a routerLink="/connexion" [queryParams]="{ redirect: '/evenements/' + slug() }"
                   class="font-semibold text-brand-700">J'ai déjà un compte</a>
              </div>
              <div class="grid max-w-lg gap-3 sm:grid-cols-2">
                @if (identiteRequise() !== 'NOM_SEUL') {
                  <div>
                    <label class="form-label">Prénom{{ identiteRequise() === 'PRENOM_SEUL' ? ' *' : '' }}</label>
                    <input class="form-input" [(ngModel)]="guestFirstName" />
                  </div>
                }
                @if (identiteRequise() !== 'PRENOM_SEUL') {
                  <div>
                    <label class="form-label">Nom{{ identiteRequise() === 'NOM_SEUL' ? ' *' : '' }}</label>
                    <input class="form-input" [(ngModel)]="guestLastName" />
                  </div>
                }
              </div>
              <div class="grid max-w-lg gap-3 sm:grid-cols-2">
                <div>
                  <label class="form-label">Téléphone *</label>
                  <app-phone-input [(ngModel)]="guestPhone" />
                </div>
                <div>
                  <label class="form-label">Adresse e-mail (facultatif)</label>
                  <input class="form-input" type="email" [(ngModel)]="guestEmail" />
                  <p class="mt-1 text-xs text-slate-400">Pour recevoir vos billets et créer un compte.</p>
                </div>
              </div>
            } @else {
              <div>
                <label class="form-label">Nom du participant</label>
                <input class="form-input max-w-sm" [(ngModel)]="participantNom" />
              </div>
            }

            @if (singleFreeTicket(); as t) {
              <p class="text-sm text-slate-600">
                Billet : <span class="font-medium">{{ t.nom }}</span>
                <span class="text-slate-400">— Gratuit</span>
              </p>
            } @else if (tickets().length) {
              <div>
                @if (sessionTickets().length) {
                  <p class="text-xs text-slate-500">
                    Astuce : cochez les sessions qui vous intéressent, ici ou depuis le programme
                    (bouton « S'inscrire »). Un seul formulaire suffit pour toutes.
                  </p>
                }
                @for (g of ticketGroups(); track g.label) {
                <p class="form-label mt-3">{{ g.label }}</p>
                <table class="w-full max-w-2xl text-sm">
                  <tbody class="divide-y divide-slate-100">
                    @for (t of g.tickets; track t.id) {
                      <tr [class.bg-brand-50]="(qty()[t.id] || 0) > 0">
                        <td class="py-2">
                          @if (t.activites.length && t.activites[0].dateDebut) {
                            <span class="mr-1 rounded bg-slate-100 px-1.5 py-0.5 text-xs font-semibold text-slate-600">
                              {{ sessionWhen(t) }}
                            </span>
                          }
                          {{ t.nom }}
                          @if (t.quantiteRestante <= 0) {
                            <span class="badge bg-red-100 text-red-800">Complet</span>
                          } @else {
                            <span class="text-xs text-slate-400">· {{ t.quantiteRestante }} place{{ t.quantiteRestante > 1 ? 's' : '' }} restante{{ t.quantiteRestante > 1 ? 's' : '' }}</span>
                          }
                        </td>
                        <td class="py-2 text-right font-semibold"
                            [class.text-slate-400]="!t.prixMontant">{{ prix(t.prixMontant) }}</td>
                        <td class="py-2 pl-3 text-right">
                          @if (maxFor(t) <= 1) {
                            <input type="checkbox" class="h-5 w-5" [disabled]="t.quantiteRestante <= 0"
                                   [attr.aria-label]="'Choisir ' + t.nom"
                                   [ngModel]="(qty()[t.id] || 0) > 0" (ngModelChange)="setQty(t.id, $event ? 1 : 0)" />
                          } @else {
                            <input type="number" min="0" [max]="maxFor(t)" class="form-input w-16"
                                   [attr.aria-label]="'Quantité ' + t.nom"
                                   [ngModel]="qty()[t.id] || 0" (ngModelChange)="setQty(t.id, $event)" />
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
                }
              </div>
            }
            <p class="text-xs text-slate-500">
              Vos nom et téléphone servent uniquement à émettre vos billets et à contrôler l'accès.
              <a routerLink="/confidentialite" target="_blank" class="font-semibold text-brand-700 underline">
                Politique de confidentialité et mentions légales</a>.
            </p>
            @if (error()) { <p class="text-sm text-red-700">{{ error() }}</p> }
            <button class="btn-primary" [disabled]="submitting()" (click)="submit()">
              {{ singleFreeTicket() ? 'Obtenir mon billet'
                 : totalQty() > 0 ? 'S\\'inscrire et commander (' + totalQty() + ' billet(s))' : 'S\\'inscrire' }}
            </button>
          </div>
        }
      </section>

      <!-- STANDS -->
      @if (e.standsActifs) {
        <section class="card mt-6 p-5" id="stands">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="font-semibold text-slate-800">Stands</h2>
            <div class="flex flex-wrap items-center gap-3">
              <a [routerLink]="['/evenements', e.slug, 'pointage', 'signaler']"
                 class="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
                <app-icon name="check" class="h-4 w-4" /> Signaler mon passage
              </a>
              <a [routerLink]="['/evenements', e.slug, 'pointage']"
                 class="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
                <app-icon name="users" class="h-4 w-4" /> Pointage en direct
              </a>
            </div>
          </div>
          @if (standReservation(); as sr) {
            <div class="mt-3 rounded-lg bg-slate-50 p-4 text-sm">
              <p class="font-medium text-slate-700">
                Réservation {{ sr.numeroReservation }} — Stand {{ sr.standNumero }} —
                <span class="font-semibold">{{ statutLabel(sr.statut) }}</span>
              </p>
              @if (sr.statut === 'RESERVE_TEMP') {
                <p class="mt-1 text-xs text-amber-700">Stand bloqué jusqu'à {{ dt(sr.dateLimitePaiement) }}.</p>
                <button class="btn-primary mt-3" (click)="payStand()">Payer (sandbox)</button>
              } @else if (sr.statut === 'CONFIRME') {
                <p class="mt-2 text-green-700">Stand confirmé.</p>
              }
            </div>
          } @else if (!auth.isAuthenticated()) {
            <p class="mt-2 text-sm text-slate-500">
              @if (e.standsParticuliers) { Connectez-vous pour réserver un stand. }
              @else {
                La réservation de stands est réservée aux comptes <b>structure</b>
                (entreprise / institution).
              }
              <a routerLink="/connexion" [queryParams]="{ redirect: '/evenements/' + slug() }"
                 class="font-semibold text-brand-700">Se connecter</a>
            </p>
          } @else if (!e.standsParticuliers && !auth.hasPermission('STAND_RESERVE')) {
            <p class="mt-2 text-sm text-slate-500">
              La réservation de stands est réservée aux comptes <b>structure</b>.
            </p>
          } @else {
            @if (e.standsParticuliers) {
              <div class="mt-3 inline-flex rounded-lg border border-slate-200 p-1 text-sm">
                <button type="button" class="rounded-md px-3 py-1"
                        [class.bg-brand-600]="standMode() === 'particulier'"
                        [class.text-white]="standMode() === 'particulier'"
                        (click)="standMode.set('particulier')">En mon nom</button>
                <button type="button" class="rounded-md px-3 py-1"
                        [class.bg-brand-600]="standMode() === 'structure'"
                        [class.text-white]="standMode() === 'structure'"
                        (click)="standMode.set('structure')">Au nom d'une structure</button>
              </div>
            }

            @if (standMode() === 'structure') {
              @if (verifiedStructures().length === 0) {
                <div class="mt-3 rounded-lg bg-amber-50 p-4 text-sm">
                  <p class="font-medium text-amber-800">
                    {{ structures().length ? 'Structure en attente de vérification'
                                            : 'Aucune structure vérifiée' }}
                  </p>
                  <a routerLink="/tableau-de-bord/structures" class="btn-primary mt-3 inline-flex">
                    Mes structures
                  </a>
                </div>
              } @else {
                <div class="mt-3 max-w-sm">
                  <label class="form-label">Réserver au nom de</label>
                  <select class="form-input" [ngModel]="standStructureId()"
                          (ngModelChange)="standStructureId.set($event)">
                    <option [ngValue]="null" disabled>Choisir une structure…</option>
                    @for (st of verifiedStructures(); track st.id) {
                      <option [ngValue]="st.id">{{ st.raisonSociale }}</option>
                    }
                  </select>
                </div>
              }
            }

            @for (t of standTypes(); track t.id) {
              <div class="mt-3 rounded-lg border border-slate-100 p-3 text-sm">
                <div class="flex items-center justify-between">
                  <p class="font-medium text-slate-800">
                    {{ t.nom }}
                    @if (t.prixMontant > 0) { — {{ fcfa(t.prixMontant) }} }
                    @else { <span class="text-slate-400">— Gratuit</span> }
                  </p>
                  <span class="text-slate-400">{{ t.quantiteRestante }} disponibles</span>
                </div>
                <div class="mt-2 flex flex-wrap gap-1">
                  @for (s of standsOfType(t.id); track s.id) {
                    <button type="button" class="rounded border px-2 py-1 text-xs"
                            [class.border-brand-500]="s.disponible" [class.text-brand-700]="s.disponible"
                            [class.border-slate-200]="!s.disponible" [class.text-slate-300]="!s.disponible"
                            [disabled]="!s.disponible || (standMode() === 'structure' && !standStructureId())"
                            (click)="reserveStand(s)">{{ s.numero }}</button>
                  }
                </div>
              </div>
            }
            @if (standError()) { <p class="mt-2 text-sm text-red-700">{{ standError() }}</p> }
          }
        </section>
      }

      @if (e.hasActivities && e.programme.length) {
        <section class="card mt-6 p-5">
          <h2 class="font-semibold text-slate-800">Programme</h2>
          <app-programme-chart class="mt-4" [activities]="e.programme" [title]="e.nom"
                               [subtitle]="range(e)" [actions]="activityActions" [filters]="true" />
          <ng-template #activityActions let-a>
                  @if (!registration() && ticketsFor(a.id).length) {
                    @if (isSelected(a.id)) {
                      <div class="mt-2 flex flex-wrap items-center gap-2 text-xs">
                        <span class="badge bg-green-100 text-green-800">✓ Ajoutée à votre inscription</span>
                        <button type="button" class="text-slate-500 underline" (click)="unselectActivity(a.id)">Retirer</button>
                      </div>
                    } @else if (soldOut(a.id)) {
                      <span class="badge mt-2 bg-red-100 text-red-800">Complet</span>
                    } @else {
                      <button type="button" class="btn-primary mt-2 px-3 py-1 text-xs" (click)="selectActivity(a.id)">
                        S'inscrire{{ priceFor(a.id) }}
                      </button>
                    }
                  } @else if (a.acces === 'GRATUIT') {
                    @if (attendedTickets()[a.id]; as t) {
                      <div class="mt-2 flex max-w-sm items-center gap-3 rounded-lg border border-slate-200 p-2">
                        @if (ticketQr()[t.id]; as qr) {
                          <img [src]="qr" alt="QR du billet" class="h-14 w-14 rounded border border-slate-100" />
                        }
                        <div class="flex-1 text-xs">
                          <p class="font-medium text-green-700">Vous participez — billet {{ t.numero }}</p>
                          <p class="text-slate-400">Pas besoin d'imprimer, la version numérique suffit.</p>
                        </div>
                        <button type="button" class="btn-ghost text-brand-700" (click)="downloadTicket(t)">
                          Télécharger
                        </button>
                      </div>
                    } @else if (auth.isAuthenticated()) {
                      <button class="btn-ghost mt-1 text-brand-700" [disabled]="attending()"
                              (click)="attend(a.id)">S'inscrire à cette session (billet gratuit)</button>
                    }
                  }
                  @if (a.format && a.format !== 'SUR_PLACE') {
                    @if (diffusion()[a.id]; as lien) {
                      <a [href]="lien" target="_blank" rel="noopener" class="mt-1 inline-block text-xs font-semibold text-fuchsia-700 underline">
                        Rejoindre la diffusion en ligne →
                      </a>
                    } @else if (auth.isAuthenticated()) {
                      <button type="button" class="mt-1 block text-xs text-fuchsia-700 underline" (click)="loadDiffusion(a.id)">
                        Obtenir le lien de diffusion
                      </button>
                    } @else {
                      <p class="mt-1 text-xs text-slate-500">Lien de diffusion envoyé après inscription.</p>
                    }
                  }
                  <button type="button" class="mt-1 block text-xs font-medium"
                          [class.text-amber-600]="inAgenda(a.id)" [class.text-slate-500]="!inAgenda(a.id)"
                          [attr.aria-pressed]="inAgenda(a.id)" (click)="toggleAgenda(a.id)">
                    {{ inAgenda(a.id) ? '★ Dans mon programme' : '☆ Ajouter à mon programme' }}
                  </button>
                  @if (attendError() && attendErrorFor() === a.id) {
                    <p class="mt-1 text-sm text-red-700">{{ attendError() }}</p>
                  }
          </ng-template>
        </section>
      }

      @if (agendaSessions().length) {
        <section class="card mt-6 p-5" id="mon-programme">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 class="font-semibold text-slate-800">Mon programme ({{ agendaSessions().length }})</h2>
            <button type="button" class="btn-primary" (click)="exportAgenda(e)">Exporter (.ics)</button>
          </div>
          <p class="mt-1 text-xs text-slate-500">
            Le fichier .ics s'ouvre dans Google Agenda, Outlook ou le calendrier de votre téléphone,
            avec un rappel 30 minutes avant chaque session.
          </p>
          <ul class="mt-3 divide-y divide-slate-100 text-sm">
            @for (a of agendaSessions(); track a.id) {
              <li class="flex flex-wrap items-center justify-between gap-2 py-2">
                <span>
                  <b>{{ dt(a.dateDebut) }}</b> · {{ a.titre }}
                  <span class="text-slate-500">{{ a.salle ? '· ' + a.salle : '' }}</span>
                </span>
                <span class="flex gap-3 text-xs">
                  <a [href]="googleUrl(a, e)" target="_blank" rel="noopener" class="text-brand-700">Google Agenda</a>
                  <button type="button" class="text-slate-500" (click)="toggleAgenda(a.id)">Retirer</button>
                </span>
              </li>
            }
          </ul>
        </section>
      }

      @if (e.intervenants.length) {
        <section class="card mt-6 p-5" id="intervenants">
          <h2 class="font-semibold text-slate-800">Intervenants</h2>
          <div class="mt-3 grid gap-3 sm:grid-cols-2">
            @for (s of e.intervenants; track s.id) {
              <button type="button" class="flex items-start gap-3 rounded-lg border border-slate-100 p-3 text-left text-sm hover:border-brand-300"
                      [attr.aria-expanded]="openSpeaker() === s.id"
                      (click)="openSpeaker.set(openSpeaker() === s.id ? null : s.id)">
                @if (s.photoUrl) {
                  <img [src]="s.photoUrl" [alt]="'Photo de ' + s.nom" class="h-14 w-14 shrink-0 rounded-full object-cover" />
                }
                <div class="min-w-0">
                  <p class="font-medium text-slate-800">{{ s.nom }}</p>
                  <p class="text-slate-500">{{ s.titre }}{{ s.organisation ? ' · ' + s.organisation : '' }}</p>
                  @if (openSpeaker() === s.id) {
                    @if (s.bio) { <p class="mt-2 whitespace-pre-line text-slate-600">{{ s.bio }}</p> }
                    <p class="mt-2 text-xs font-semibold uppercase text-slate-500">Ses sessions</p>
                    @for (a of sessionsOf(s, e); track a.id) {
                      <p class="text-xs text-slate-700">{{ dt(a.dateDebut) }} · {{ a.titre }}{{ a.salle ? ' · ' + a.salle : '' }}</p>
                    } @empty {
                      <p class="text-xs text-slate-400">Aucune session liée.</p>
                    }
                  } @else {
                    <p class="mt-1 text-xs text-brand-700">{{ sessionsOf(s, e).length }} session(s) — voir la fiche</p>
                  }
                </div>
              </button>
            }
          </div>
        </section>
      }

      @if (e.partenaires.length) {
        <section class="card mt-6 p-5" id="partenaires">
          <h2 class="font-semibold text-slate-800">Partenaires</h2>
          @for (g of partnerGroups(e); track g.label) {
            <p class="mt-4 text-xs font-semibold uppercase tracking-wide text-slate-500">{{ g.label }}</p>
            <ul class="mt-2 flex flex-wrap items-center gap-3 text-sm">
              @for (p of g.items; track p.id) {
                <li class="flex items-center gap-2 rounded-lg border border-slate-100 px-3 py-2">
                  @if (p.logoUrl) {
                    <img [src]="p.logoUrl" [alt]="'Logo ' + p.nom" class="object-contain"
                         [class.h-14]="g.big" [class.w-24]="g.big" [class.h-8]="!g.big" [class.w-12]="!g.big" />
                  }
                  @if (p.siteWeb) {
                    <a [href]="p.siteWeb" target="_blank" rel="noopener" class="text-slate-700 hover:underline">{{ p.nom }}</a>
                  } @else {
                    <span class="text-slate-700">{{ p.nom }}</span>
                  }
                </li>
              }
            </ul>
          }
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

      @if (!registration() && selectedSessions() > 0) {
        <div class="fixed inset-x-0 bottom-0 z-30 border-t border-slate-200 bg-white/95 p-3 shadow-lg backdrop-blur">
          <div class="mx-auto flex max-w-4xl flex-wrap items-center justify-between gap-3">
            <p class="text-sm text-slate-700">
              <b>{{ selectedSessions() }}</b> session{{ selectedSessions() > 1 ? 's' : '' }} choisie{{ selectedSessions() > 1 ? 's' : '' }}
              — un seul formulaire pour toutes.
            </p>
            <button type="button" class="btn-primary" (click)="goToForm()">Finaliser mon inscription</button>
          </div>
        </div>
      }
    } @else if (loadError()) {
      <p class="py-16 text-center text-slate-500">{{ loadError() }}</p>
    } @else {
      <p class="py-16 text-center text-slate-400">Chargement…</p>
    }
  `,
})
export class EventDetailComponent implements OnDestroy {
  private events = inject(EventsService);
  private ticketsService = inject(TicketsService);
  private standsService = inject(StandsService);
  private structuresService = inject(StructuresService);
  private registrationsService = inject(RegistrationsService);
  private paymentsService = inject(PaymentsService);
  private sanitizer = inject(DomSanitizer);
  auth = inject(AuthService);
  statutLabel = statutLabel;

  slug = input.required<string>();
  event = signal<EventPublic | null>(null);
  tickets = signal<EventTicket[]>([]);
  loadError = signal<string | null>(null);
  error = signal<string | null>(null);
  submitting = signal(false);
  qty = signal<Record<string, number>>({});
  /** Only category, and it's free: no picker to show, quantity is implicitly 1. */
  singleFreeTicket = computed(() =>
    this.tickets().length === 1 && !this.tickets()[0].prixMontant ? this.tickets()[0] : null,
  );
  /** That free category also skips the identity form — phone only (or nothing, if signed in). */
  noFormTicket = computed(() => {
    const t = this.singleFreeTicket();
    return t && !t.formulaireRequis ? t : null;
  });
  /** The "click, phone, done" shortcut shown right under the title — only
   * for the simplest case (free, no form, no account). Everything else
   * (paid, multi-billet, un formulaire complet) reste dans la section
   * « Participer à l'événement » plus bas. */
  quickTicket = computed(() =>
    !this.auth.isAuthenticated() && !this.registration() ? this.noFormTicket() : null,
  );
  /** Which identity field(s) the guest form asks for — driven by the single
   * free category's setting when there is one and it has a form ; both
   * (nom + prénom) otherwise (multi-billet, payant : pas configurable). */
  identiteRequise = computed<IdentiteRequise>(() => {
    const t = this.singleFreeTicket();
    return t && t.formulaireRequis ? t.identiteRequise : 'NOM_ET_PRENOM';
  });
  registration = signal<Registration | null>(null);
  participantNom = '';

  // Billets obtenus (commande payée, ou activité gratuite) — affichés directement,
  // pas besoin d'aller sur « Mes billets ».
  ticketReceipts = signal<MyTicket[]>([]);
  ticketQr = signal<Record<string, SafeUrl>>({});
  attendedTickets = signal<Record<string, IssuedTicket>>({});
  private ticketReceiptIds = new Set<string>();
  private qrObjectUrls: string[] = [];

  // Guest checkout (visitor without an account)
  guestFirstName = '';
  guestLastName = '';
  guestEmail = '';
  guestPhone = '';

  standTypes = signal<StandType[]>([]);
  stands = signal<Stand[]>([]);
  standReservation = signal<StandReservation | null>(null);
  standError = signal<string | null>(null);

  attended = signal<string[]>([]);
  attending = signal(false);
  attendError = signal<string | null>(null);
  attendErrorFor = signal<string | null>(null);
  structures = signal<StructureSummary[]>([]);
  standStructureId = signal<string | null>(null);
  standMode = signal<'particulier' | 'structure'>('particulier');
  verifiedStructures = computed(() => this.structures().filter((s) => s.statut === 'VERIFIEE'));

  private route = inject(ActivatedRoute);
  private wantsToParticipate = this.route.snapshot.queryParamMap.has('participer');

  /** Arrivée depuis « S'inscrire et prendre un billet » : on amène le visiteur au formulaire. */
  private scrollToParticipation(): void {
    if (!this.wantsToParticipate) return;
    setTimeout(() => {
      const el = document.getElementById('obtenir-billet') ?? document.getElementById('participer');
      el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 250);
  }

  constructor() {
    effect(() => {
      const slug = this.slug();
      if (!slug) return;
      this.events.publicBySlug(slug).subscribe({
        next: (e) => {
          this.event.set(e);
          this.agenda.set(loadAgenda(e.id));
          this.standMode.set(e.standsParticuliers ? 'particulier' : 'structure');
        },
        error: () => this.loadError.set("Cet événement n'est pas disponible."),
      });
      this.ticketsService.publicTickets(slug).subscribe({
        next: (t) => {
          const filtered = t.filter((x) => x.enVente || x.quantiteRestante > 0);
          this.tickets.set(filtered);
          // A single category — free or paid — defaults to quantity 1 so
          // there's nothing to fill in beyond the contact form.
          if (filtered.length === 1) this.qty.set({ [filtered[0].id]: 1 });
          this.scrollToParticipation();
        },
        error: () => this.tickets.set([]),
      });
      this.standsService.publicTypes(slug).subscribe({ next: (t) => this.standTypes.set(t), error: () => {} });
      this.standsService.publicStands(slug).subscribe({ next: (s) => this.stands.set(s), error: () => {} });
    });
    if (this.auth.user()) this.participantNom = this.auth.user()!.fullName;
    if (this.auth.hasPermission('STAND_RESERVE')) {
      this.structuresService.mine().subscribe({
        next: (list) => {
          this.structures.set(list);
          const verified = list.filter((s) => s.statut === 'VERIFIEE');
          if (verified.length === 1) this.standStructureId.set(verified[0].id);
        },
        error: () => {},
      });
    }
  }

  range = (e: EventPublic) => formatDateRange(e.dateDebut, e.dateFin);
  dt = (iso?: string) => formatDateTime(iso);
  time = (iso?: string) => formatTime(iso);
  fcfa = (n?: number) => formatFcfa(n);
  prix = (n?: number) => priceLabel(n);
  maxFor = (t: EventTicket) => Math.min(t.quantiteRestante, t.limiteParUtilisateur);
  standsOfType = (typeId: string) => this.stands().filter((s) => s.standTypeId === typeId);

  // --- Partage (PART-08): the /partage link carries the event's Open Graph preview.
  copied = signal(false);
  shareUrl = (e: EventPublic) => `${location.origin}/partage/evenements/${e.slug}`;
  whatsappUrl = (e: EventPublic) =>
    `https://wa.me/?text=${encodeURIComponent(e.nom + ' — ' + this.range(e) + ' ' + this.shareUrl(e))}`;
  copyShareLink(e: EventPublic): void {
    navigator.clipboard?.writeText(this.shareUrl(e)).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2500);
    });
  }

  // --- Mon programme (PART-02) ---
  agenda = signal<string[]>([]);
  agendaSessions = computed(() => {
    const ids = new Set(this.agenda());
    return (this.event()?.programme ?? []).filter((a) => ids.has(a.id))
      .sort((a, b) => a.dateDebut.localeCompare(b.dateDebut));
  });
  inAgenda = (id: string) => this.agenda().includes(id);
  toggleAgenda(id: string): void {
    const e = this.event();
    if (!e) return;
    const next = this.inAgenda(id) ? this.agenda().filter((x) => x !== id) : [...this.agenda(), id];
    this.agenda.set(next);
    saveAgenda(e.id, next);
  }
  exportAgenda(e: EventPublic): void {
    downloadIcs(`mon-programme-${e.slug}.ics`, buildIcs(e.nom, e.lieu, this.agendaSessions()));
  }
  googleUrl = (a: Activity, e: EventPublic) => googleCalendarUrl(a, e.lieu);

  // --- Intervenants & partenaires (PART-06) ---
  openSpeaker = signal<string | null>(null);
  sessionsOf(s: { id: string; nom: string }, e: EventPublic): Activity[] {
    const name = s.nom.toLowerCase();
    return e.programme.filter((a) => a.speakerId === s.id
      || (a.intervenant ?? '').toLowerCase().includes(name) || (a.moderateur ?? '').toLowerCase().includes(name));
  }
  private static readonly LEVELS: [string, string, boolean][] = [
    ['PLATINE', 'Partenaires platine', true], ['OR', 'Partenaires or', true], ['ARGENT', 'Partenaires argent', false],
    ['BRONZE', 'Partenaires bronze', false], ['PARTENAIRE_INSTITUTIONNEL', 'Partenaires institutionnels', false],
    ['PARTENAIRE_MEDIA', 'Partenaires médias', false], ['PARTENAIRE', 'Partenaires', false],
  ];
  partnerGroups(e: EventPublic) {
    const groups = EventDetailComponent.LEVELS
      .map(([code, label, big]) => ({ label, big, items: e.partenaires.filter((p) => p.niveau === code) }))
      .filter((g) => g.items.length);
    const other = e.partenaires.filter((p) => !p.niveau || !EventDetailComponent.LEVELS.some(([c]) => c === p.niveau));
    return other.length ? [...groups, { label: 'Partenaires', big: false, items: other }] : groups;
  }

  /** Activity-scoped categories, grouped apart from event-wide ones. */
  sessionTickets = computed(() => this.tickets().filter((t) => t.portee === 'ACTIVITE' && t.activites.length));
  ticketGroups = computed(() => {
    const general = this.tickets().filter((t) => !(t.portee === 'ACTIVITE' && t.activites.length));
    const sessions = [...this.sessionTickets()].sort((a, b) =>
      (a.activites[0].dateDebut ?? '').localeCompare(b.activites[0].dateDebut ?? ''),
    );
    return [
      ...(general.length ? [{ label: sessions.length ? "Accès à l'événement" : 'Billets', tickets: general }] : []),
      ...(sessions.length ? [{ label: 'Billets par session', tickets: sessions }] : []),
    ];
  });
  /** Number of sessions picked so far (activity-scoped categories with a quantity). */
  selectedSessions = computed(() => this.sessionTickets().filter((t) => (this.qty()[t.id] || 0) > 0).length);
  diffusion = signal<Record<string, string>>({});

  private static readonly WHEN = new Intl.DateTimeFormat('fr-FR', {
    weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  });

  /** « jeu. 26 nov., 09:00 · Salle Kilimandjaro » for a session ticket. */
  sessionWhen(t: EventTicket): string {
    const a = t.activites[0];
    if (!a?.dateDebut) return '';
    return EventDetailComponent.WHEN.format(new Date(a.dateDebut)) + (a.salle ? ' · ' + a.salle : '');
  }

  ticketsFor(activityId: string): EventTicket[] {
    return this.tickets()
      .filter((t) => t.portee === 'ACTIVITE' && t.activites.some((a) => a.id === activityId))
      .sort((a, b) => (a.prixMontant || 0) - (b.prixMontant || 0));
  }
  isSelected(activityId: string): boolean {
    return this.ticketsFor(activityId).some((t) => (this.qty()[t.id] || 0) > 0);
  }
  soldOut(activityId: string): boolean {
    return this.ticketsFor(activityId).every((t) => t.quantiteRestante <= 0);
  }
  priceFor(activityId: string): string {
    const t = this.ticketsFor(activityId).find((x) => x.quantiteRestante > 0);
    return t && t.prixMontant ? ' — ' + formatFcfa(t.prixMontant) : '';
  }
  /** « S'inscrire » on a session: pre-select its (cheapest available) ticket in the form. */
  selectActivity(activityId: string): void {
    const t = this.ticketsFor(activityId).find((x) => x.quantiteRestante > 0);
    if (!t) return;
    this.setQty(t.id, 1);
    if (this.selectedSessions() === 1) this.goToForm();
  }
  unselectActivity(activityId: string): void {
    const next = { ...this.qty() };
    this.ticketsFor(activityId).forEach((t) => delete next[t.id]);
    this.qty.set(next);
  }
  goToForm(): void {
    document.getElementById('participer')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  loadDiffusion(activityId: string): void {
    this.attendError.set(null);
    this.ticketsService.activityDiffusion(activityId).subscribe({
      next: (d) => this.diffusion.set({ ...this.diffusion(), [activityId]: d.lien }),
      error: (err: HttpErrorResponse) => {
        this.attendErrorFor.set(activityId);
        this.attendError.set((err.error as ApiError)?.message ?? 'Lien indisponible.');
      },
    });
  }

  setQty(id: string, value: number): void {
    this.qty.set({ ...this.qty(), [id]: Math.max(0, Math.floor(value || 0)) });
  }
  totalQty = computed(() => Object.values(this.qty()).reduce((a, b) => a + b, 0));

  submit(): void {
    this.error.set(null);

    if (!this.auth.isAuthenticated()) {
      if (this.noFormTicket()) {
        const phone = this.guestPhone.trim();
        if (!/^\+?[0-9 ]{6,20}$/.test(phone)) {
          this.error.set('Renseignez un numéro de téléphone valide.');
          return;
        }
        this.submitting.set(true);
        this.auth.guestSessionQuick(phone).subscribe({
          next: () => this.doRegister({ nom: 'Visiteur' }),
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            const body = err.error as ApiError | undefined;
            this.error.set(
              body?.code === 'ACCOUNT_EXISTS'
                ? `${body.message} Utilisez « J'ai déjà un compte » pour vous connecter.`
                : (body?.message ?? 'Impossible de créer la session.'),
            );
          },
        });
        return;
      }

      const first = this.guestFirstName.trim();
      const last = this.guestLastName.trim();
      const email = this.guestEmail.trim();
      const phone = this.guestPhone.trim();
      const needFirst = this.identiteRequise() !== 'NOM_SEUL';
      const needLast = this.identiteRequise() !== 'PRENOM_SEUL';
      if ((needFirst && !first) || (needLast && !last) || !/^\+?[0-9 ]{6,20}$/.test(phone)) {
        this.error.set(
          this.identiteRequise() === 'NOM_SEUL' ? 'Renseignez votre nom et un numéro de téléphone.'
            : this.identiteRequise() === 'PRENOM_SEUL' ? 'Renseignez votre prénom et un numéro de téléphone.'
            : 'Renseignez votre prénom, votre nom et un numéro de téléphone.',
        );
        return;
      }
      if (email && !email.includes('@')) {
        this.error.set('L\'adresse e-mail n\'est pas valide.');
        return;
      }
      this.submitting.set(true);
      this.auth
        .guestSession({
          email: email || undefined,
          firstName: first,
          lastName: last,
          phone,
        })
        .subscribe({
          next: () => this.doRegister({ prenom: first || undefined, nom: last || undefined }),
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            const body = err.error as ApiError | undefined;
            this.error.set(
              body?.code === 'ACCOUNT_EXISTS'
                ? `${body.message} Utilisez « J'ai déjà un compte » pour vous connecter.`
                : (body?.message ?? 'Impossible de créer la session.'),
            );
          },
        });
      return;
    }

    this.submitting.set(true);
    this.doRegister({ nom: this.participantNom || this.auth.user()?.fullName || 'Participant' });
  }

  private doRegister(participant: { nom?: string; prenom?: string }): void {
    const tickets = Object.entries(this.qty())
      .filter(([, q]) => q > 0)
      .map(([eventTicketId, quantite]) => ({ eventTicketId, quantite }));
    this.registrationsService
      .register(this.event()!.id, {
        type: 'PARTICULIER',
        participants: [{
          nom: participant.nom || undefined,
          prenom: participant.prenom || undefined,
        }],
        tickets: tickets.length ? tickets : undefined,
      })
      .subscribe({
        next: (r) => {
          this.submitting.set(false);
          this.registration.set(r);
          this.loadOrderTicketsIfPaid(r);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.error.set((err.error as ApiError)?.message ?? 'Inscription impossible.');
        },
      });
  }

  pay(r: Registration): void {
    if (!r.ticketOrderId) return;
    // For a real provider this navigates the browser away — nothing more to
    // do here; the payer lands back on « Mes paiements » once done.
    this.paymentsService.payOrRedirect('TICKET_ORDER', r.ticketOrderId).subscribe(() =>
      this.registrationsService.byId(r.id).subscribe((x) => {
        this.registration.set(x);
        this.loadOrderTicketsIfPaid(x);
      }),
    );
  }

  attend(activityId: string): void {
    this.attendError.set(null);
    this.attendErrorFor.set(null);
    this.attending.set(true);
    this.ticketsService.attendActivity(activityId).subscribe({
      next: (t) => {
        this.attending.set(false);
        this.attended.set([...this.attended(), activityId]);
        this.attendedTickets.set({ ...this.attendedTickets(), [activityId]: t });
        this.loadTicketQr(t.id);
      },
      error: (err: HttpErrorResponse) => {
        this.attending.set(false);
        this.attendErrorFor.set(activityId);
        this.attendError.set((err.error as ApiError)?.message ?? 'Participation impossible.');
      },
    });
  }

  /** Once a ticket order is paid (free orders confirm instantly), show its tickets right here. */
  private loadOrderTicketsIfPaid(r: Registration): void {
    if (r.ticketOrderStatut !== 'PAYEE' || !r.ticketOrderReference) return;
    this.ticketsService.myTickets().subscribe((all) => {
      all
        .filter((t) => t.orderReference === r.ticketOrderReference)
        .forEach((t) => {
          if (this.ticketReceiptIds.has(t.id)) return;
          this.ticketReceiptIds.add(t.id);
          this.ticketReceipts.set([...this.ticketReceipts(), t]);
          this.loadTicketQr(t.id);
          this.downloadTicket(t);
        });
    });
  }

  private loadTicketQr(ticketId: string): void {
    if (this.ticketQr()[ticketId]) return;
    this.ticketsService.qrBlob(ticketId).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      this.qrObjectUrls.push(url);
      this.ticketQr.set({ ...this.ticketQr(), [ticketId]: this.sanitizer.bypassSecurityTrustUrl(url) });
    });
  }

  downloadTicket(t: { id: string; numero: string }): void {
    this.ticketsService.pdfBlob(t.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `billet-${t.numero}.pdf`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    });
  }

  ngOnDestroy(): void {
    this.qrObjectUrls.forEach((u) => URL.revokeObjectURL(u));
  }

  reserveStand(s: Stand): void {
    const asStructure = this.standMode() === 'structure';
    const structureId = asStructure ? this.standStructureId() : undefined;
    if (asStructure && !structureId) {
      this.standError.set('Choisissez d\'abord une structure.');
      return;
    }
    this.standError.set(null);
    this.standsService
      .reserve({ eventId: this.event()!.id, standId: s.id, structureId: structureId ?? undefined })
      .subscribe({
        next: (r) => this.standReservation.set(r),
        error: (err: HttpErrorResponse) =>
          this.standError.set((err.error as ApiError)?.message ?? 'Réservation impossible.'),
      });
  }
  payStand(): void {
    const id = this.standReservation()!.id;
    this.paymentsService.payOrRedirect('STAND_RESERVATION', id).subscribe({
      next: () => this.standsService.byId(id).subscribe((r) => this.standReservation.set(r)),
      error: (err: HttpErrorResponse) =>
        this.standError.set((err.error as ApiError)?.message ?? 'Paiement impossible.'),
    });
  }
}
