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
import { BilletVisualisable, TicketViewerComponent } from '../tickets/ticket-viewer.component';
import { Stand, StandReservation, StandType } from '../stands/stand.models';
import { EventPublic, EventStatus, EventTicket, IdentiteRequise, MyTicket } from '../events/event.models';
import {
  formatDateRange,
  formatDateTime,
  formatFcfa,
  formatTime,
  priceLabel,
} from '../../shared/format';
import { AuthService } from '../../core/auth.service';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, PhoneInputComponent, EventCoverComponent, IconComponent, ProgrammeChartComponent, TicketViewerComponent],
  template: `
    @if (event(); as e) {
      <!-- ============ BANDEAU ============ -->
      <section class="pleine-largeur -mt-8 bg-neutre-900 text-white sm:-mt-10">
        <div class="mx-auto grid max-w-conteneur items-center gap-10 px-4 py-10 sm:px-6 lg:grid-cols-[1.15fr_1fr] lg:py-14">
          <div class="min-w-0">
            <nav aria-label="Fil d'Ariane" class="text-sm text-neutre-400">
              <ol class="flex flex-wrap items-center gap-1.5">
                <li><a routerLink="/" class="hover:text-white hover:underline">Événements</a></li>
                @if (e.categoryNom) {
                  <li aria-hidden="true">/</li>
                  <li class="text-neutre-300">{{ e.categoryNom }}</li>
                }
              </ol>
            </nav>
            <div class="mt-5 flex flex-wrap items-center gap-2">
              @if (statut(e.statut); as s) {
                <span class="badge gap-1.5" [class]="s.cls">
                  @if (e.statut === 'EN_COURS') { <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-current"></span> }
                  {{ s.label }}
                </span>
              }
              @if (e.sigle && e.sigle !== e.nom) { <span class="badge bg-white/10 text-neutre-200">{{ e.sigle }}</span> }
            </div>
            <h1 class="mt-4 text-[clamp(2rem,1.54rem+2.04vw,3.3125rem)] text-white">{{ e.nom }}</h1>
            @if (e.descriptionCourte) {
              <p class="mt-4 max-w-xl text-[1.0625rem] leading-[1.75] text-neutre-300">{{ e.descriptionCourte }}</p>
            }
            <ul class="mt-6 space-y-3 text-[0.9375rem] text-neutre-100">
              <li class="flex items-start gap-3">
                <app-icon name="calendar" class="mt-0.5 h-5 w-5 shrink-0 text-or-300" aria-hidden="true" />
                <span>{{ dateLongue(e) }}</span>
              </li>
              <li class="flex items-start gap-3">
                <app-icon name="pin" class="mt-0.5 h-5 w-5 shrink-0 text-or-300" aria-hidden="true" />
                <span>{{ e.lieu || '—' }}{{ e.ville ? ', ' + e.ville : '' }}</span>
              </li>
              <li class="flex items-start gap-3">
                <app-icon name="building" class="mt-0.5 h-5 w-5 shrink-0 text-or-300" aria-hidden="true" />
                <span>Organisé par <span class="font-semibold text-white">{{ e.organizerNom }}</span></span>
              </li>
            </ul>
            <div class="mt-8 flex flex-wrap gap-3">
              @if (!estTermine(e)) {
                <a href="#participer" (click)="allerA($event, 'participer')" class="btn-primary px-6">
                  <app-icon name="ticket" class="h-4 w-4" aria-hidden="true" /> {{ libelleCta() }}
                </a>
              }
              <button type="button" class="btn-contour-clair" (click)="ajouterAuCalendrier(e)">
                <app-icon name="calendar-plus" class="h-4 w-4" aria-hidden="true" /> Ajouter au calendrier
              </button>
              <button type="button" class="btn-contour-clair" (click)="partager(e)">
                <app-icon name="share" class="h-4 w-4" aria-hidden="true" /> {{ partageOk() ? 'Lien copié' : 'Partager' }}
              </button>
            </div>
          </div>
          <div class="overflow-hidden rounded-xl bg-black/30 shadow-xl ring-1 ring-white/10">
            <app-event-cover class="aspect-[16/9] w-full" [nom]="e.nom" [coverUrl]="e.coverUrl" [contain]="true" />
          </div>
        </div>
        <div class="fs-tissage" aria-hidden="true"></div>
      </section>

      <!-- ============ SOMMAIRE ============ -->
      <nav class="pleine-largeur sticky top-0 z-[100] border-b border-neutre-200 bg-white/95 backdrop-blur" aria-label="Sommaire de la page">
        <ul class="mx-auto flex max-w-conteneur gap-1 overflow-x-auto px-4 sm:px-6">
          @for (s of sommaire(); track s.id) {
            <li>
              <a [href]="'#' + s.id" (click)="allerA($event, s.id)"
                 class="flex min-h-[52px] items-center whitespace-nowrap border-b-2 border-transparent px-3 font-display text-sm font-semibold text-neutre-600 transition-colors hover:border-vert-600 hover:text-vert-700">
                {{ s.label }}
              </a>
            </li>
          }
        </ul>
      </nav>

      <div class="mt-10 grid items-start gap-10 pb-24 lg:grid-cols-[minmax(0,1fr)_380px] lg:pb-0">
        <!-- ============ CONTENU ============ -->
        <div class="min-w-0 space-y-14">
          <section id="presentation" class="scroll-mt-20">
            <h2 class="text-[1.625rem] text-neutre-900">À propos de l'événement</h2>
            <!-- Texte long : premières lignes, puis « Lire la suite » pour tout afficher -->
            <div class="relative mt-4 max-w-mesure">
              <p id="apropos-texte" class="whitespace-pre-line leading-[1.75] text-neutre-800"
                 [class.line-clamp-6]="aproposLong(e) && !aproposOuvert()">{{ texteApropos(e) }}</p>
              @if (aproposLong(e) && !aproposOuvert()) {
                <div class="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-t from-neutre-25 to-transparent" aria-hidden="true"></div>
              }
            </div>
            @if (aproposLong(e)) {
              <button type="button" class="mt-3 inline-flex items-center gap-1.5 font-display text-sm font-semibold text-vert-700 hover:underline"
                      aria-controls="apropos-texte" [attr.aria-expanded]="aproposOuvert()" (click)="aproposOuvert.set(!aproposOuvert())">
                {{ aproposOuvert() ? 'Réduire' : 'Lire la suite' }}
                <app-icon name="chevron-down" class="h-4 w-4 transition-transform" [class.rotate-180]="aproposOuvert()" aria-hidden="true" />
              </button>
            }
            @if (e.conditionsParticipation) {
              <div class="mt-6 flex gap-3 rounded-xl border border-bleu-100 bg-bleu-50 p-4 text-sm text-bleu-800">
                <app-icon name="info" class="h-5 w-5 shrink-0" aria-hidden="true" />
                <p><span class="font-semibold">Conditions de participation.</span> {{ e.conditionsParticipation }}</p>
              </div>
            }
          </section>

          @if (e.hasActivities && e.programme.length) {
            <section id="programme" class="scroll-mt-20">
              <h2 class="text-[1.625rem] text-neutre-900">Programme</h2>
              <p class="mt-1 text-sm text-neutre-600">{{ e.programme.length }} activité{{ e.programme.length > 1 ? 's' : '' }} au programme</p>
              <app-programme-chart class="mt-6" [activities]="e.programme" [title]="e.nom"
                                   [subtitle]="range(e)" [actions]="activityActions" />
              <ng-template #activityActions let-a>
                @if (a.acces === 'GRATUIT') {
                  @if (attendedTickets()[a.id]; as t) {
                    <div class="mt-2 flex max-w-sm items-center gap-3 rounded-lg border border-vert-200 bg-vert-50 p-2">
                      @if (ticketQr()[t.id]; as qr) {
                        <img [src]="qr" alt="QR du billet" class="h-14 w-14 rounded border border-white bg-white" />
                      }
                      <div class="flex-1 text-xs">
                        <p class="font-semibold text-vert-800">Vous participez — billet {{ t.numero }}</p>
                        <p class="text-neutre-600">Pas besoin d'imprimer, la version numérique suffit.</p>
                      </div>
                      <div class="flex flex-col gap-1">
                        <button type="button" class="btn-ghost min-h-[36px] py-1 text-vert-700" (click)="apercu.set(t)">Voir</button>
                        <button type="button" class="btn-ghost min-h-[36px] py-1 text-vert-700" (click)="downloadTicket(t)">Télécharger</button>
                      </div>
                    </div>
                  } @else if (attendPhoneFor() === a.id) {
                    <div class="mt-2 max-w-sm rounded-lg border border-neutre-200 bg-neutre-50 p-3">
                      <label class="form-label">Votre téléphone *</label>
                      <app-phone-input [(ngModel)]="attendPhone" />
                      <div class="mt-2 flex flex-wrap items-center gap-2">
                        <button type="button" class="btn-primary min-h-[36px] py-1 text-xs" [disabled]="attending()"
                                (click)="attendAsGuest(a.id)">{{ attending() ? 'Un instant…' : 'Obtenir mon billet' }}</button>
                        <button type="button" class="btn-ghost min-h-[36px] py-1 text-xs" (click)="attendPhoneFor.set(null)">Annuler</button>
                      </div>
                      <p class="mt-2 text-xs text-neutre-600">
                        Pas besoin de compte.
                        <a routerLink="/connexion" [queryParams]="{ redirect: '/evenements/' + slug() }" class="lien">J'ai déjà un compte</a>
                      </p>
                    </div>
                  } @else {
                    <button class="btn-secondary mt-2 min-h-[36px] py-1 text-xs" [disabled]="attending()"
                            (click)="auth.isAuthenticated() ? attend(a.id) : attendPhoneFor.set(a.id)">
                      Réserver cette session (gratuit)
                    </button>
                  }
                } @else if (a.acces === 'PAYANT') {
                  <p class="mt-1 text-xs text-neutre-600">Billet requis — voir la billetterie.</p>
                }
                @if (passesPour(a.id).length) {
                  <p class="mt-1.5 inline-flex items-center gap-1 text-xs font-medium text-vert-700">
                    <app-icon name="ticket" class="h-3.5 w-3.5" aria-hidden="true" />
                    Inclus dans : {{ passesPour(a.id).join(', ') }}
                  </p>
                }
                @if (attendError() && attendErrorFor() === a.id) {
                  <p class="mt-1 text-sm text-rouge-700">{{ attendError() }}</p>
                }
              </ng-template>
            </section>
          }

          @if (e.intervenants.length) {
            <section id="intervenants" class="scroll-mt-20">
              <h2 class="text-[1.625rem] text-neutre-900">Intervenants</h2>
              <ul class="mt-6 grid gap-4 sm:grid-cols-2">
                @for (s of e.intervenants; track s.id) {
                  <li class="flex items-start gap-4 rounded-xl border border-neutre-200 bg-white p-4">
                    @if (s.photoUrl) {
                      <img [src]="s.photoUrl" alt="" class="h-14 w-14 shrink-0 rounded-full object-cover ring-2 ring-neutre-100" />
                    } @else {
                      <span class="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-vert-50 font-display text-lg font-bold text-vert-700">{{ initiales(s.nom) }}</span>
                    }
                    <div class="min-w-0">
                      <p class="font-display font-semibold text-neutre-900">{{ s.nom }}</p>
                      @if (s.titre || s.organisation) {
                        <p class="text-sm text-neutre-600">{{ s.titre }}{{ s.titre && s.organisation ? ' · ' : '' }}{{ s.organisation }}</p>
                      }
                      @if (s.bio) { <p class="mt-2 line-clamp-3 text-sm text-neutre-700">{{ s.bio }}</p> }
                    </div>
                  </li>
                }
              </ul>
            </section>
          }

          <!-- STANDS -->
          @if (e.standsActifs) {
            <section id="stands" class="scroll-mt-20">
              <div class="flex flex-wrap items-end justify-between gap-3">
                <div>
                  <h2 class="text-[1.625rem] text-neutre-900">Stands</h2>
                  <p class="mt-1 text-sm text-neutre-600">Exposez votre activité pendant l'événement.</p>
                </div>
                <div class="flex flex-wrap items-center gap-4 text-sm">
                  <a [routerLink]="['/evenements', e.slug, 'pointage', 'signaler']" class="inline-flex items-center gap-1.5 font-medium text-vert-700 hover:underline">
                    <app-icon name="check" class="h-4 w-4" aria-hidden="true" /> Signaler mon passage
                  </a>
                  <a [routerLink]="['/evenements', e.slug, 'pointage']" class="inline-flex items-center gap-1.5 font-medium text-vert-700 hover:underline">
                    <app-icon name="users" class="h-4 w-4" aria-hidden="true" /> Pointage en direct
                  </a>
                </div>
              </div>

              <div class="mt-6 rounded-xl border border-neutre-200 bg-white p-5">
                @if (standReservation(); as sr) {
                  <div class="text-sm">
                    <p class="font-semibold text-neutre-900">Réservation {{ sr.numeroReservation }} — Stand {{ sr.standNumero }}</p>
                    <p class="mt-1 text-neutre-600">Statut : <span class="font-semibold">{{ sr.statut }}</span></p>
                    @if (sr.statut === 'RESERVE_TEMP') {
                      <p class="mt-2 text-ocre-700">Stand bloqué jusqu'à {{ dt(sr.dateLimitePaiement) }}.</p>
                      <button class="btn-primary mt-3" (click)="payStand()">Payer la réservation</button>
                    } @else if (sr.statut === 'CONFIRME') {
                      <p class="mt-2 flex items-center gap-1.5 font-medium text-vert-700"><app-icon name="check" class="h-4 w-4" aria-hidden="true" /> Stand confirmé.</p>
                    }
                  </div>
                } @else if (!auth.isAuthenticated()) {
                  <div class="flex flex-wrap items-center justify-between gap-4">
                    <p class="text-sm text-neutre-700">
                      @if (e.standsParticuliers) { Connectez-vous pour réserver un stand. }
                      @else { La réservation de stands est réservée aux comptes <b>structure</b> (entreprise, institution). }
                    </p>
                    <a routerLink="/connexion" [queryParams]="{ redirect: '/evenements/' + slug() }" class="btn-secondary">Se connecter</a>
                  </div>
                } @else if (!e.standsParticuliers && !auth.hasPermission('STAND_RESERVE')) {
                  <p class="text-sm text-neutre-700">La réservation de stands est réservée aux comptes <b>structure</b>.</p>
                } @else {
                  @if (e.standsParticuliers) {
                    <div class="inline-flex rounded-md bg-neutre-100 p-1 text-sm" role="group" aria-label="Réserver">
                      <button type="button" class="min-h-[36px] rounded px-3 font-medium"
                              [class]="standMode() === 'particulier' ? 'bg-white text-neutre-900 shadow-xs' : 'text-neutre-600'"
                              (click)="standMode.set('particulier')">En mon nom</button>
                      <button type="button" class="min-h-[36px] rounded px-3 font-medium"
                              [class]="standMode() === 'structure' ? 'bg-white text-neutre-900 shadow-xs' : 'text-neutre-600'"
                              (click)="standMode.set('structure')">Au nom d'une structure</button>
                    </div>
                  }

                  @if (standMode() === 'structure') {
                    @if (verifiedStructures().length === 0) {
                      <div class="mt-3 rounded-lg bg-ocre-50 p-4 text-sm">
                        <p class="font-medium text-ocre-800">
                          {{ structures().length ? 'Structure en attente de vérification' : 'Aucune structure vérifiée' }}
                        </p>
                        <a routerLink="/tableau-de-bord/structures" class="btn-secondary mt-3 inline-flex">Mes structures</a>
                      </div>
                    } @else {
                      <div class="mt-4 max-w-sm">
                        <label class="form-label" for="stand-structure">Réserver au nom de</label>
                        <select id="stand-structure" class="form-input" [ngModel]="standStructureId()"
                                (ngModelChange)="standStructureId.set($event)">
                          <option [ngValue]="null" disabled>Choisir une structure…</option>
                          @for (st of verifiedStructures(); track st.id) {
                            <option [ngValue]="st.id">{{ st.raisonSociale }}</option>
                          }
                        </select>
                      </div>
                    }
                  }

                  <div class="mt-4 space-y-4">
                    @for (t of standTypes(); track t.id) {
                      <div class="rounded-lg border border-neutre-200 p-4 text-sm">
                        <div class="flex flex-wrap items-center justify-between gap-2">
                          <p class="font-display font-semibold text-neutre-900">{{ t.nom }}</p>
                          <p class="flex items-center gap-3">
                            <span class="font-semibold tabular-nums text-neutre-900">{{ t.prixMontant > 0 ? fcfa(t.prixMontant) : 'Gratuit' }}</span>
                            <span class="text-neutre-600">{{ t.quantiteRestante }} disponible{{ t.quantiteRestante > 1 ? 's' : '' }}</span>
                          </p>
                        </div>
                        <div class="mt-3 flex flex-wrap gap-1.5">
                          @for (s of standsOfType(t.id); track s.id) {
                            <button type="button" class="min-h-[36px] min-w-[44px] rounded-md border px-2 text-xs font-semibold transition-colors"
                                    [class]="s.disponible ? 'border-vert-600 text-vert-700 hover:bg-vert-600 hover:text-white' : 'border-neutre-200 bg-neutre-50 text-neutre-400 line-through'"
                                    [attr.aria-label]="'Stand ' + s.numero + (s.disponible ? ', disponible' : ', indisponible')"
                                    [disabled]="!s.disponible || (standMode() === 'structure' && !standStructureId())"
                                    (click)="reserveStand(s)">{{ s.numero }}</button>
                          }
                        </div>
                      </div>
                    }
                  </div>
                  @if (standError()) { <p class="mt-3 text-sm text-rouge-700" role="alert">{{ standError() }}</p> }
                }
              </div>
            </section>
          }

          @if (e.partenaires.length) {
            <section id="partenaires" class="scroll-mt-20">
              <h2 class="text-[1.625rem] text-neutre-900">Partenaires</h2>
              <ul class="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
                @for (p of e.partenaires; track p.id) {
                  <li class="flex flex-col items-center justify-center gap-2 rounded-xl border border-neutre-200 bg-white p-4 text-center">
                    @if (p.logoUrl) {
                      <img [src]="p.logoUrl" alt="" class="h-12 w-full object-contain" />
                    }
                    <span class="text-sm font-medium text-neutre-800">{{ p.nom }}</span>
                  </li>
                }
              </ul>
            </section>
          }

          <section id="infos" class="scroll-mt-20">
            <h2 class="text-[1.625rem] text-neutre-900">Informations pratiques</h2>
            <dl class="mt-6 grid gap-4 sm:grid-cols-2">
              <div class="flex gap-4 rounded-xl border border-neutre-200 bg-white p-4">
                <app-icon name="calendar" class="h-6 w-6 shrink-0 text-vert-700" aria-hidden="true" />
                <div><dt class="text-sm text-neutre-600">Date et heure</dt><dd class="mt-0.5 font-medium text-neutre-900">{{ dateLongue(e) }}</dd></div>
              </div>
              <div class="flex gap-4 rounded-xl border border-neutre-200 bg-white p-4">
                <app-icon name="pin" class="h-6 w-6 shrink-0 text-vert-700" aria-hidden="true" />
                <div>
                  <dt class="text-sm text-neutre-600">Lieu</dt>
                  <dd class="mt-0.5 font-medium text-neutre-900">{{ e.lieu || '—' }}</dd>
                  <dd class="text-sm text-neutre-700">{{ [e.adresse, e.ville, e.pays].filter(estRempli).join(', ') }}</dd>
                  <dd><a [href]="lienCarte(e)" target="_blank" rel="noopener" class="lien mt-1 inline-flex items-center gap-1 text-sm">Itinéraire <app-icon name="arrow-right" class="h-3.5 w-3.5" aria-hidden="true" /></a></dd>
                </div>
              </div>
              @if (e.contactEmail || e.contactTelephone || e.siteWeb) {
                <div class="flex gap-4 rounded-xl border border-neutre-200 bg-white p-4 sm:col-span-2">
                  <app-icon name="building" class="h-6 w-6 shrink-0 text-vert-700" aria-hidden="true" />
                  <div class="min-w-0">
                    <dt class="text-sm text-neutre-600">Organisateur</dt>
                    <dd class="mt-0.5 font-medium text-neutre-900">{{ e.organizerNom }}</dd>
                    <dd class="mt-2 flex flex-wrap gap-x-6 gap-y-2 text-sm">
                      @if (e.contactEmail) { <a [href]="'mailto:' + e.contactEmail" class="lien inline-flex items-center gap-1.5"><app-icon name="mail" class="h-4 w-4" aria-hidden="true" />{{ e.contactEmail }}</a> }
                      @if (e.contactTelephone) { <a [href]="'tel:' + e.contactTelephone" class="lien inline-flex items-center gap-1.5"><app-icon name="phone" class="h-4 w-4" aria-hidden="true" />{{ e.contactTelephone }}</a> }
                      @if (e.siteWeb) { <a [href]="e.siteWeb" target="_blank" rel="noopener" class="lien inline-flex items-center gap-1.5"><app-icon name="globe" class="h-4 w-4" aria-hidden="true" />Site web</a> }
                    </dd>
                  </div>
                </div>
              }
            </dl>
            <a [routerLink]="['/evenements', slug(), 'flux']" class="mt-6 inline-flex items-center gap-2 text-sm font-medium text-vert-700 hover:underline">
              <app-icon name="users" class="h-4 w-4" aria-hidden="true" /> Suivre la présence en direct
            </a>
          </section>
        </div>

        <!-- ============ BILLETTERIE (colonne fixe) ============ -->
        <aside class="lg:sticky lg:top-[72px]" aria-labelledby="titre-billetterie">
          <div class="overflow-hidden rounded-xl border border-neutre-200 bg-white shadow-md">
            <div class="border-b border-neutre-200 bg-neutre-50 px-5 py-4">
              <h2 id="titre-billetterie" class="surtitre text-neutre-600">Billetterie</h2>
              <p class="mt-1 font-display text-2xl font-extrabold tabular-nums text-neutre-900">{{ prixApartir() }}</p>
            </div>

            <div class="p-5">
              <!-- Raccourci "en un clic" : billet gratuit unique, sans formulaire, sans compte. -->
              @if (quickTicket(); as t) {
                <div id="obtenir-billet" class="scroll-mt-24">
                  <p class="text-sm text-neutre-700">Billet <span class="font-semibold text-neutre-900">{{ t.nom }}</span> — gratuit</p>
                  @if (ticketReceipts().length) {
                    <p class="mt-3 rounded-lg bg-vert-50 px-3 py-2 text-sm text-vert-800">
                      Billet téléchargé. Pas besoin de l'imprimer : la version numérique,
                      affichée sur votre téléphone, suffit pour l'entrée.
                    </p>
                    <div class="mt-3 grid grid-cols-2 gap-2">
                      <button type="button" class="btn-secondary" (click)="apercu.set(ticketReceipts()[0])">
                        <app-icon name="eye" class="h-4 w-4" aria-hidden="true" /> Voir
                      </button>
                      <button type="button" class="btn-ghost border border-neutre-300" (click)="downloadTicket(ticketReceipts()[0])">
                        <app-icon name="download" class="h-4 w-4" aria-hidden="true" /> Télécharger
                      </button>
                    </div>
                  } @else {
                    <div class="mt-4">
                      <label class="form-label">Téléphone *</label>
                      <app-phone-input [(ngModel)]="guestPhone" />
                    </div>
                    @if (error()) { <p class="mt-2 text-sm text-rouge-700" role="alert">{{ error() }}</p> }
                    <button type="button" class="btn-primary mt-4 w-full" [disabled]="submitting()" (click)="submit()">
                      {{ submitting() ? 'Un instant…' : 'Obtenir mon billet' }}
                    </button>
                  }
                </div>
              }

              <div id="participer" class="scroll-mt-24">
                @if (registration(); as r) {
                  <div class="text-sm">
                    <p class="font-display font-semibold text-neutre-900">Inscription {{ r.reference }}</p>
                    <p class="text-neutre-600">Statut : <span class="font-semibold">{{ r.statut }}</span></p>
                    @if (r.ticketOrderStatut === 'EN_ATTENTE') {
                      <p class="mt-2 text-xs text-ocre-700">Finalisez le paiement pour valider vos billets.</p>
                      <button class="btn-primary mt-3 w-full" (click)="pay(r)">Payer maintenant</button>
                    } @else if (r.statut === 'CONFIRMEE') {
                      <p class="mt-2 flex items-center gap-1.5 font-medium text-vert-700"><app-icon name="check" class="h-4 w-4" aria-hidden="true" /> Inscription confirmée.</p>
                    } @else if (r.statut === 'EN_ATTENTE') {
                      <p class="mt-2 text-ocre-700">En attente de validation par l'organisateur.</p>
                    }

                    @if (ticketReceipts().length) {
                      <div class="mt-4 space-y-3">
                        <p class="rounded-lg bg-vert-50 px-3 py-2 text-xs text-vert-800">
                          Votre billet est prêt. Pas besoin de l'imprimer : la version numérique,
                          affichée sur votre téléphone, suffit pour l'entrée.
                        </p>
                        @for (t of ticketReceipts(); track t.id) {
                          <div class="flex items-center gap-3 rounded-lg border border-neutre-200 p-3">
                            @if (ticketQr()[t.id]; as qr) {
                              <img [src]="qr" alt="QR du billet" class="h-16 w-16 rounded border border-neutre-100" />
                            }
                            <div class="min-w-0 flex-1">
                              <p class="font-medium text-neutre-900">Billet {{ t.numero }}</p>
                              <p class="text-neutre-600">{{ t.categorieNom }}</p>
                            </div>
                          </div>
                          <div class="grid grid-cols-2 gap-2">
                            <button type="button" class="btn-secondary" (click)="apercu.set(t)">
                              <app-icon name="eye" class="h-4 w-4" aria-hidden="true" /> Voir
                            </button>
                            <button type="button" class="btn-ghost border border-neutre-300" (click)="downloadTicket(t)">
                              <app-icon name="download" class="h-4 w-4" aria-hidden="true" /> Télécharger
                            </button>
                          </div>
                        }
                      </div>
                    }

                    @if (auth.isGuest()) {
                      <div class="mt-4 rounded-lg border border-vert-200 bg-vert-50 p-3">
                        <p class="font-semibold text-vert-800">Créez votre compte</p>
                        <p class="mt-1 text-xs text-neutre-700">
                          Choisissez un mot de passe pour retrouver vos billets, inscriptions
                          et factures sur tous vos appareils.
                        </p>
                        <a routerLink="/finaliser-compte" class="btn-primary mt-3 w-full">Créer un compte</a>
                      </div>
                    } @else if (auth.isFullyAuthenticated() && r.statut === 'CONFIRMEE') {
                      <a routerLink="/tableau-de-bord/inscriptions" class="btn-secondary mt-4 w-full">Mes inscriptions</a>
                    }
                  </div>
                } @else if (quickTicket()) {
                  <!-- formulaire ci-dessus -->
                } @else if (estTermine(e)) {
                  <p class="text-sm text-neutre-700">Cet événement est terminé. Les inscriptions sont closes.</p>
                } @else {
                  <div class="space-y-4">
                    @if (singleFreeTicket(); as t) {
                      <p class="text-sm text-neutre-700">Billet <span class="font-semibold text-neutre-900">{{ t.nom }}</span> — gratuit</p>
                    } @else if (tickets().length) {
                      <fieldset>
                        <legend class="form-label">Choisissez vos billets</legend>
                        <ul class="mt-2 divide-y divide-neutre-100 rounded-lg border border-neutre-200">
                          @for (t of tickets(); track t.id) {
                            <li class="flex items-center justify-between gap-3 p-3">
                              <div class="min-w-0">
                                <p class="font-medium text-neutre-900">{{ t.nom }}</p>
                                @if (t.portee === 'ACTIVITE' && t.activites.length) {
                                  <p class="mt-0.5 text-xs text-neutre-600" [title]="titresActivites(t)">
                                    {{ t.description || resumeActivites(t) }}
                                    <span class="font-medium text-vert-700">· {{ t.activites.length }} session{{ t.activites.length > 1 ? 's' : '' }}</span>
                                  </p>
                                } @else if (t.portee === 'EVENEMENT' && t.description) {
                                  <p class="mt-0.5 text-xs text-neutre-600">{{ t.description }}</p>
                                }
                                <p class="text-sm">
                                  <span class="font-semibold tabular-nums" [class.text-vert-700]="!t.prixMontant">{{ prix(t.prixMontant) }}</span>
                                  <span class="text-neutre-500"> · {{ t.quantiteRestante }} restant{{ t.quantiteRestante > 1 ? 's' : '' }}</span>
                                </p>
                              </div>
                              <div class="flex shrink-0 items-center gap-1" role="group" [attr.aria-label]="'Quantité ' + t.nom">
                                <button type="button" class="flex h-9 w-9 items-center justify-center rounded-md border border-neutre-300 text-lg font-semibold text-neutre-700 hover:border-vert-600 hover:text-vert-700 disabled:opacity-40"
                                        [disabled]="!(qty()[t.id] || 0)" (click)="setQty(t.id, (qty()[t.id] || 0) - 1)" aria-label="Retirer un billet">−</button>
                                <span class="w-8 text-center font-display font-bold tabular-nums" aria-live="polite">{{ qty()[t.id] || 0 }}</span>
                                <button type="button" class="flex h-9 w-9 items-center justify-center rounded-md border border-neutre-300 text-lg font-semibold text-neutre-700 hover:border-vert-600 hover:text-vert-700 disabled:opacity-40"
                                        [disabled]="(qty()[t.id] || 0) >= maxFor(t)" (click)="setQty(t.id, (qty()[t.id] || 0) + 1)" aria-label="Ajouter un billet">+</button>
                              </div>
                            </li>
                          }
                        </ul>
                        @if (totalMontant() > 0) {
                          <p class="mt-3 flex items-center justify-between text-sm">
                            <span class="text-neutre-600">Total</span>
                            <span class="font-display text-lg font-extrabold tabular-nums text-neutre-900">{{ fcfa(totalMontant()) }}</span>
                          </p>
                        }
                      </fieldset>
                    }

                    @if (!auth.isAuthenticated()) {
                      <div class="grid gap-3">
                        @if (identiteRequise() !== 'NOM_SEUL') {
                          <div>
                            <label class="form-label" for="g-prenom">Prénom{{ identiteRequise() === 'PRENOM_SEUL' ? ' *' : '' }}</label>
                            <input id="g-prenom" class="form-input" autocomplete="given-name" [(ngModel)]="guestFirstName" />
                          </div>
                        }
                        @if (identiteRequise() !== 'PRENOM_SEUL') {
                          <div>
                            <label class="form-label" for="g-nom">Nom{{ identiteRequise() === 'NOM_SEUL' ? ' *' : '' }}</label>
                            <input id="g-nom" class="form-input" autocomplete="family-name" [(ngModel)]="guestLastName" />
                          </div>
                        }
                        <div>
                          <label class="form-label">Téléphone *</label>
                          <app-phone-input [(ngModel)]="guestPhone" />
                        </div>
                        <div>
                          <label class="form-label" for="g-email">Adresse e-mail <span class="font-normal text-neutre-500">(facultatif)</span></label>
                          <input id="g-email" class="form-input" type="email" autocomplete="email" [(ngModel)]="guestEmail" />
                          <p class="mt-1 text-xs text-neutre-600">Pour recevoir vos billets par e-mail.</p>
                        </div>
                      </div>
                    } @else {
                      <div>
                        <label class="form-label" for="p-nom">Nom du participant</label>
                        <input id="p-nom" class="form-input" [(ngModel)]="participantNom" />
                      </div>
                    }

                    @if (error()) { <p class="text-sm text-rouge-700" role="alert">{{ error() }}</p> }
                    <button class="btn-primary w-full" [disabled]="submitting()" (click)="submit()">
                      {{ submitting() ? 'Un instant…'
                         : singleFreeTicket() ? 'Obtenir mon billet'
                         : totalQty() > 0 ? 'Réserver ' + totalQty() + ' billet' + (totalQty() > 1 ? 's' : '') : 'S’inscrire' }}
                    </button>
                    @if (!auth.isAuthenticated()) {
                      <p class="text-center text-xs text-neutre-600">
                        Pas besoin de compte.
                        <a routerLink="/connexion" [queryParams]="{ redirect: '/evenements/' + slug() }" class="lien">J'ai déjà un compte</a>
                      </p>
                    }
                  </div>
                }
              </div>
            </div>

            <ul class="space-y-2 border-t border-neutre-200 bg-neutre-25 px-5 py-4 text-xs text-neutre-700">
              <li class="flex items-center gap-2"><app-icon name="shield" class="h-4 w-4 text-vert-600" aria-hidden="true" /> Paiement en ligne sécurisé</li>
              <li class="flex items-center gap-2"><app-icon name="qr" class="h-4 w-4 text-vert-600" aria-hidden="true" /> Billet électronique avec QR code</li>
              <li class="flex items-center gap-2"><app-icon name="check" class="h-4 w-4 text-vert-600" aria-hidden="true" /> Pas besoin d'imprimer</li>
            </ul>
          </div>
        </aside>
      </div>

      @if (apercu(); as b) {
        <app-ticket-viewer [billet]="b" (fermer)="apercu.set(null)" />
      }

      <!-- Barre de réservation mobile -->
      @if (!registration() && !estTermine(e)) {
        <div class="fixed inset-x-0 bottom-0 z-[100] border-t border-neutre-200 bg-white/95 px-4 py-3 shadow-[0_-8px_24px_rgba(33,29,26,0.08)] backdrop-blur lg:hidden">
          <div class="mx-auto flex max-w-conteneur items-center justify-between gap-4">
            <div class="min-w-0">
              <p class="truncate text-xs text-neutre-600">{{ e.nom }}</p>
              <p class="font-display font-extrabold tabular-nums text-neutre-900">{{ prixApartir() }}</p>
            </div>
            <a href="#participer" (click)="allerA($event, quickTicket() ? 'obtenir-billet' : 'participer')" class="btn-primary shrink-0 px-6">{{ libelleCta() }}</a>
          </div>
        </div>
      }
    } @else if (loadError()) {
      <div class="mx-auto max-w-etroit py-20 text-center">
        <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-neutre-100 text-neutre-500"><app-icon name="calendar" class="h-7 w-7" aria-hidden="true" /></span>
        <p class="mt-4 font-display text-xl font-bold text-neutre-900">{{ loadError() }}</p>
        <a routerLink="/" class="btn-secondary mt-6">Voir tous les événements</a>
      </div>
    } @else {
      <div class="pleine-largeur -mt-8 h-[28rem] animate-pulse bg-neutre-800 sm:-mt-10" aria-label="Chargement"></div>
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

  /** « À propos » : replié par défaut quand le texte est long. */
  aproposOuvert = signal(false);
  texteApropos = (e: EventPublic) =>
    e.descriptionDetaillee || e.descriptionCourte || 'Les détails de cet événement seront bientôt disponibles.';
  /** Long = plus de ~6 lignes : beaucoup de caractères ou beaucoup de retours à la ligne. */
  aproposLong = (e: EventPublic) => {
    const t = this.texteApropos(e);
    return t.length > 450 || t.split('\n').length > 6;
  };

  /** Billet affiché dans la fenêtre « Voir le billet ». */
  apercu = signal<BilletVisualisable | null>(null);

  /** Pass multi-activités (billets de portée ACTIVITE créés par l'organisateur). */
  passes = signal<EventTicket[]>([]);
  /** Pour une activité : les pass qui l'incluent (« Inclus dans le Pass Conférences »). */
  passesPour = (activityId: string) =>
    this.passes().filter((p) => p.activites.some((a) => a.id === activityId)).map((p) => p.nom);

  /** « Participer » sans compte : on demande le téléphone, puis on crée une session visiteur. */
  attendPhoneFor = signal<string | null>(null);
  attendPhone = '';

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
          this.standMode.set(e.standsParticuliers ? 'particulier' : 'structure');
          // Billets chargés après l'événement : il faut connaître le programme pour
          // reconnaître les billets automatiques des activités gratuites.
          const auto = new Set(e.programme.map((a) => a.freeTicketId).filter((id): id is string => !!id));
          this.ticketsService.publicTickets(slug).subscribe({
            next: (t) => {
              const enVente = t.filter((x) => x.enVente || x.quantiteRestante > 0);
              this.passes.set(enVente.filter((x) => !auto.has(x.id) && x.portee === 'ACTIVITE'));
              // Les billets automatiques (« Accès — … ») se prennent depuis le programme
              // via « Participer » : on ne les répète pas dans la billetterie. Les pass
              // multi-activités créés par l'organisateur, eux, y restent.
              const filtered = enVente.filter((x) => !auto.has(x.id));
              this.tickets.set(filtered);
              // A single category — free or paid — defaults to quantity 1 so
              // there's nothing to fill in beyond the contact form.
              if (filtered.length === 1) this.qty.set({ [filtered[0].id]: 1 });
              this.scrollToParticipation();
            },
            error: () => this.tickets.set([]),
          });
        },
        error: () => this.loadError.set("Cet événement n'est pas disponible."),
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


  // ---------- présentation (bandeau, sommaire, billetterie) ----------
  partageOk = signal(false);

  sommaire = computed(() => {
    const e = this.event();
    if (!e) return [];
    const s = [{ id: 'presentation', label: 'À propos' }];
    if (e.hasActivities && e.programme.length) s.push({ id: 'programme', label: 'Programme' });
    if (e.intervenants.length) s.push({ id: 'intervenants', label: 'Intervenants' });
    if (e.standsActifs) s.push({ id: 'stands', label: 'Stands' });
    if (e.partenaires.length) s.push({ id: 'partenaires', label: 'Partenaires' });
    s.push({ id: 'infos', label: 'Infos pratiques' });
    s.push({ id: 'participer', label: 'Billetterie' });
    return s;
  });

  prixApartir = computed(() => {
    const t = this.tickets();
    if (!t.length) return 'Inscription libre';
    const payants = t.map((x) => x.prixMontant || 0).filter((x) => x > 0);
    if (!payants.length) return 'Gratuit';
    const min = Math.min(...payants);
    return payants.length === t.length && t.length > 1 ? `Dès ${formatFcfa(min)}` : t.length > 1 ? `Gratuit – ${formatFcfa(Math.max(...payants))}` : formatFcfa(min);
  });

  totalMontant = computed(() =>
    this.tickets().reduce((sum, t) => sum + (this.qty()[t.id] || 0) * (t.prixMontant || 0), 0),
  );

  libelleCta = computed(() => {
    const t = this.tickets();
    return t.length && t.every((x) => !x.prixMontant) ? 'Obtenir mon billet' : t.length ? 'Réserver ma place' : "S'inscrire";
  });

  estTermine = (e: EventPublic) => e.statut === 'TERMINE' || e.statut === 'ANNULE' || new Date(e.dateFin).getTime() < Date.now();
  estRempli = (v?: string | null) => !!v;

  initiales = (nom: string) =>
    nom.split(/\s+/).filter(Boolean).slice(0, 2).map((m) => m[0]!.toUpperCase()).join('');

  dateLongue(e: EventPublic): string {
    const f = (iso: string, o: Intl.DateTimeFormatOptions) =>
      new Intl.DateTimeFormat('fr-FR', { ...o, timeZone: 'Africa/Ouagadougou' }).format(new Date(iso));
    const o: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' };
    const d = f(e.dateDebut, o);
    const fin = f(e.dateFin, o);
    const cap = (x: string) => x.charAt(0).toUpperCase() + x.slice(1);
    return d === fin ? `${cap(d)}, ${this.time(e.dateDebut)} – ${this.time(e.dateFin)}` : `Du ${d} au ${fin}`;
  }

  statut(s: EventStatus): { label: string; cls: string } | null {
    switch (s) {
      case 'INSCRIPTIONS_OUVERTES': return { label: 'Inscriptions ouvertes', cls: 'bg-vert-500 text-white' };
      case 'EN_COURS': return { label: 'En cours', cls: 'bg-vert-500 text-white' };
      case 'INSCRIPTIONS_FERMEES': return { label: 'Inscriptions closes', cls: 'bg-ocre-100 text-ocre-900' };
      case 'PUBLIE': return { label: 'Bientôt', cls: 'bg-bleu-100 text-bleu-900' };
      case 'TERMINE': return { label: 'Terminé', cls: 'bg-white/15 text-neutre-100' };
      case 'ANNULE': return { label: 'Annulé', cls: 'bg-rouge-500 text-white' };
      default: return null;
    }
  }

  allerA(ev: Event, id: string): void {
    ev.preventDefault();
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  lienCarte(e: EventPublic): string {
    const q = e.latitude != null && e.longitude != null
      ? `${e.latitude},${e.longitude}`
      : [e.lieu, e.adresse, e.ville, e.pays].filter(Boolean).join(', ');
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}`;
  }

  partager(e: EventPublic): void {
    const url = window.location.origin + '/evenements/' + e.slug;
    const nav = navigator as Navigator & { share?: (d: ShareData) => Promise<void> };
    if (nav.share) {
      nav.share({ title: e.nom, text: e.descriptionCourte ?? e.nom, url }).catch(() => {});
      return;
    }
    navigator.clipboard?.writeText(url).then(() => {
      this.partageOk.set(true);
      setTimeout(() => this.partageOk.set(false), 2500);
    });
  }

  /** Fichier .ics standard : Agenda Google, Outlook, Apple, Android. */
  ajouterAuCalendrier(e: EventPublic): void {
    const ics = (iso: string) => new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
    const esc = (t: string) => t.replace(/\\/g, '\\\\').replace(/[,;]/g, (m) => '\\' + m).replace(/\n/g, '\\n');
    const url = window.location.origin + '/evenements/' + e.slug;
    const lignes = [
      'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Plateforme nationale des evenements//BF//FR', 'BEGIN:VEVENT',
      `UID:${e.id}@plateforme-evenements`, `DTSTAMP:${ics(new Date().toISOString())}`,
      `DTSTART:${ics(e.dateDebut)}`, `DTEND:${ics(e.dateFin)}`,
      `SUMMARY:${esc(e.nom)}`,
      `LOCATION:${esc([e.lieu, e.ville].filter(Boolean).join(', '))}`,
      `DESCRIPTION:${esc((e.descriptionCourte ?? '') + '\n' + url)}`,
      `URL:${url}`, 'END:VEVENT', 'END:VCALENDAR',
    ];
    const blob = new Blob([lignes.join('\r\n')], { type: 'text/calendar;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${e.slug}.ics`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }

  range = (e: EventPublic) => formatDateRange(e.dateDebut, e.dateFin);
  dt = (iso?: string) => formatDateTime(iso);
  time = (iso?: string) => formatTime(iso);
  fcfa = (n?: number) => formatFcfa(n);
  prix = (n?: number) => priceLabel(n);
  maxFor = (t: EventTicket) => Math.min(t.quantiteRestante, t.limiteParUtilisateur);
  standsOfType = (typeId: string) => this.stands().filter((s) => s.standTypeId === typeId);

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

  /** Visiteur sans compte : session visiteur par téléphone, puis billet gratuit de la session. */
  attendAsGuest(activityId: string): void {
    this.attendError.set(null);
    this.attendErrorFor.set(null);
    const phone = this.attendPhone.trim();
    if (!/^\+?[0-9 ]{6,20}$/.test(phone)) {
      this.attendErrorFor.set(activityId);
      this.attendError.set('Renseignez un numéro de téléphone valide.');
      return;
    }
    this.attending.set(true);
    this.auth.guestSessionQuick(phone).subscribe({
      next: () => {
        this.attending.set(false);
        this.attendPhoneFor.set(null);
        this.attend(activityId);
      },
      error: (err: HttpErrorResponse) => {
        this.attending.set(false);
        this.attendErrorFor.set(activityId);
        const body = err.error as ApiError | undefined;
        this.attendError.set(
          body?.code === 'ACCOUNT_EXISTS'
            ? `${body.message} Utilisez « J'ai déjà un compte » pour vous connecter.`
            : (body?.message ?? 'Impossible de créer la session.'),
        );
      },
    });
  }

  titresActivites = (t: EventTicket) => t.activites.map((a) => a.titre).join(' · ');
  resumeActivites = (t: EventTicket) => {
    const courts = t.activites.map((a) => a.titre.replace(/^(Conférence|Panel|Atelier|Formation|Table ronde)\s*(inaugurale)?\s*:\s*/i, ''));
    return courts.length <= 2 ? courts.join(', ') : `${courts.slice(0, 2).join(', ')} et ${courts.length - 2} autre${courts.length > 3 ? 's' : ''}`;
  };

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
