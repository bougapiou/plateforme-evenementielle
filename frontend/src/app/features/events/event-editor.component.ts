import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { EventsService } from './events.service';
import { Activity, EventCategory, EventDetail, EventTicket, Partner, Speaker } from './event.models';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { formatDateTime, formatFcfa, priceLabel } from '../../shared/format';
import { ApiError } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { TicketsService } from '../tickets/tickets.service';
import { StandsService } from '../stands/stands.service';
import { StandType } from '../stands/stand.models';
import { RegistrationsService } from '../registrations/registrations.service';
import { Registration } from '../registrations/registration.models';
import { CheckinService, CheckinView, StaffMember } from '../checkin/checkin.service';
import { StatsService, StatMap, EventSeries } from '../stats/stats.service';
import { BarChartComponent } from '../../shared/bar-chart.component';
import { ImageUploadComponent } from '../../shared/image-upload.component';
import { AccreditationsPanelComponent } from '../accreditations/accreditations-panel.component';

type Tab =
  | 'infos' | 'programme' | 'intervenants' | 'partenaires' | 'billetterie' | 'stands'
  | 'inscriptions' | 'accreditations' | 'controle' | 'stats';

@Component({
  selector: 'app-event-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule, FormsModule, RouterLink, StatusBadgeComponent, BarChartComponent,
    ImageUploadComponent, AccreditationsPanelComponent,
  ],
  template: `
    <a routerLink="/tableau-de-bord/evenements" class="text-sm text-slate-500">← Mes événements</a>

    @if (event(); as e) {
      <div class="mt-2 flex flex-wrap items-center gap-3">
        <h1 class="text-xl font-bold text-slate-800">{{ e.nom }}</h1>
        <app-status-badge [value]="e.statut" />
      </div>

      @if (e.motifRefus && e.statut === 'REFUSE') {
        <p class="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          Refusé : {{ e.motifRefus }}
        </p>
      }

      <div class="mt-3 flex flex-wrap gap-2">
        @for (a of actions(); track a.action) {
          <button type="button" class="btn {{ a.class }}" (click)="doTransition(a.action)">
            {{ a.label }}
          </button>
        }
      </div>
      @if (transitionError()) {
        <p class="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ transitionError() }}</p>
      }

      <div class="mt-5 flex gap-1 border-b border-slate-200 text-sm font-medium">
        @for (t of tabs; track t.id) {
          <button type="button" (click)="tab.set(t.id)"
                  class="border-b-2 px-3 py-2"
                  [class.border-brand-600]="tab() === t.id"
                  [class.text-brand-700]="tab() === t.id"
                  [class.border-transparent]="tab() !== t.id"
                  [class.text-slate-500]="tab() !== t.id">
            {{ t.label }}
          </button>
        }
      </div>

      <!-- INFOS -->
      @if (tab() === 'infos') {
        <form class="card mt-4 space-y-4 p-5" [formGroup]="form" (ngSubmit)="save()">
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="sm:col-span-2">
              <label class="form-label">Nom</label>
              <input class="form-input" formControlName="nom" />
            </div>
            <div><label class="form-label">Sigle</label><input class="form-input" formControlName="sigle" /></div>
            <div>
              <label class="form-label">Catégorie</label>
              <select class="form-input" formControlName="categoryId">
                <option value="">—</option>
                @for (c of categories(); track c.id) { <option [value]="c.id">{{ c.nom }}</option> }
              </select>
            </div>
            <div><label class="form-label">Début</label>
              <input type="datetime-local" class="form-input" formControlName="dateDebut" /></div>
            <div><label class="form-label">Fin</label>
              <input type="datetime-local" class="form-input" formControlName="dateFin" /></div>
            <div><label class="form-label">Ville</label><input class="form-input" formControlName="ville" /></div>
            <div><label class="form-label">Lieu</label><input class="form-input" formControlName="lieu" /></div>
            <div class="sm:col-span-2"><label class="form-label">Adresse</label>
              <input class="form-input" formControlName="adresse" /></div>
            <div><label class="form-label">Capacité max</label>
              <input type="number" class="form-input" formControlName="capaciteMax" /></div>
            <div><label class="form-label">E-mail de contact</label>
              <input class="form-input" formControlName="contactEmail" /></div>
            <div><label class="form-label">Ouverture des inscriptions</label>
              <input type="datetime-local" class="form-input" formControlName="inscriptionDebut" /></div>
            <div><label class="form-label">Clôture des inscriptions</label>
              <input type="datetime-local" class="form-input" formControlName="inscriptionFin" /></div>
            <div class="sm:col-span-2"><label class="form-label">Description courte</label>
              <input class="form-input" formControlName="descriptionCourte" /></div>
            <div class="sm:col-span-2"><label class="form-label">Description détaillée</label>
              <textarea rows="4" class="form-input" formControlName="descriptionDetaillee"></textarea></div>
            <div class="sm:col-span-2"><label class="form-label">Conditions de participation</label>
              <textarea rows="3" class="form-input" formControlName="conditionsParticipation"></textarea></div>
            <div class="sm:col-span-2">
              <label class="form-label">Image de couverture</label>
              <app-image-upload
                folder="evenements"
                [value]="form.controls.coverUrl.value"
                [disabled]="form.disabled"
                (valueChange)="form.controls.coverUrl.setValue($event); form.markAsDirty()"
              />
            </div>
            <div class="sm:col-span-2">
              <label class="form-label">Logo</label>
              <app-image-upload
                folder="evenements"
                [value]="form.controls.logoUrl.value"
                [disabled]="form.disabled"
                (valueChange)="form.controls.logoUrl.setValue($event); form.markAsDirty()"
              />
            </div>
          </div>
          <div class="flex flex-wrap gap-6">
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" formControlName="hasActivities" />
              Cet événement contient plusieurs activités
            </label>
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" formControlName="standsActifs" /> Réservation de stands
            </label>
            @if (form.controls.standsActifs.value) {
              <label class="flex items-center gap-2 text-sm">
                <input type="checkbox" formControlName="standsParticuliers" />
                Autoriser les particuliers à réserver un stand (sans structure)
              </label>
            }
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" formControlName="validationInscription" />
              Valider chaque inscription manuellement
            </label>
            <label class="flex items-center gap-2 text-sm">
              <input type="checkbox" formControlName="controleSortie" />
              Contrôle des sorties (comptage du flux entrées / sorties / présents)
            </label>
          </div>
          @if (error()) { <p class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p> }
          <button type="submit" class="btn-primary" [disabled]="saving() || form.disabled">
            {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
          </button>
          @if (form.disabled) {
            <p class="text-xs text-slate-400">Les informations ne sont plus modifiables dans cet état.</p>
          }
        </form>
      }

      <!-- PROGRAMME -->
      @if (tab() === 'programme') {
        @if (!e.hasActivities) {
          <p class="card mt-4 p-4 text-sm text-amber-700 bg-amber-50">
            Activez « Cet événement contient plusieurs activités » dans l'onglet Informations
            pour planifier un programme. Vous pouvez tout de même ajouter des créneaux ci-dessous.
          </p>
        }
        <form class="card mt-4 grid gap-3 p-4 sm:grid-cols-2" [formGroup]="activityForm"
              (ngSubmit)="saveActivity()">
          <input class="form-input sm:col-span-2" placeholder="Titre de l'activité" formControlName="titre" />
          <select class="form-input" formControlName="typeActivite">
            <option value="">Type…</option>
            @for (t of activityTypes; track t) { <option [value]="t">{{ t }}</option> }
          </select>
          <input class="form-input" placeholder="Salle" formControlName="salle" />
          <input type="datetime-local" class="form-input" formControlName="dateDebut" />
          <input type="datetime-local" class="form-input" formControlName="dateFin" />
          <div>
            <label class="form-label">Accès</label>
            <select class="form-input" formControlName="acces">
              <option value="SANS_BILLET">Sans billet (accès via le billet de l'événement)</option>
              <option value="GRATUIT">Gratuit sur billet (participation à la volée)</option>
              <option value="PAYANT">Payant (billets dédiés à créer dans « Billetterie »)</option>
            </select>
          </div>
          <input type="number" min="0" class="form-input" placeholder="Capacité (optionnel)"
                 formControlName="capacite" />
          <input class="form-input" placeholder="Intervenant" formControlName="intervenant" />
          <input class="form-input" placeholder="Modérateur" formControlName="moderateur" />
          <div class="sm:col-span-2">
            <label class="form-label">Visuel de l'activité</label>
            <app-image-upload
              folder="activites"
              [value]="activityForm.controls.imageUrl.value"
              (valueChange)="activityForm.controls.imageUrl.setValue($event)"
            />
          </div>
          <button type="submit" class="btn-primary sm:col-span-2">
            {{ editingActivityId() ? 'Modifier le créneau' : 'Ajouter au programme' }}
          </button>
        </form>
        <ul class="mt-4 space-y-2">
          @for (a of activities(); track a.id) {
            <li class="card flex items-center gap-3 p-3 text-sm">
              @if (a.imageUrl) {
                <img [src]="a.imageUrl" alt="" class="h-12 w-16 shrink-0 rounded object-cover" />
              }
              <div class="min-w-0 flex-1">
                <p class="font-medium text-slate-700">{{ a.titre }}</p>
                <p class="text-slate-400">
                  {{ dt(a.dateDebut) }}{{ a.salle ? ' · ' + a.salle : '' }}{{ a.typeActivite ? ' · ' + a.typeActivite : '' }}
                  @if (a.acces === 'GRATUIT') { <span class="badge bg-green-100 text-green-800">Gratuit</span> }
                  @else if (a.acces === 'PAYANT') { <span class="badge bg-amber-100 text-amber-800">Payant</span> }
                </p>
              </div>
              <div class="flex gap-2">
                <button class="text-xs text-brand-700" (click)="editActivity(a)">Modifier</button>
                <button class="text-xs text-red-600" (click)="removeActivity(a)">Supprimer</button>
              </div>
            </li>
          } @empty { <li class="text-sm text-slate-400">Aucune activité.</li> }
        </ul>
      }

      <!-- INTERVENANTS -->
      @if (tab() === 'intervenants') {
        <form class="card mt-4 grid gap-3 p-4 sm:grid-cols-2" [formGroup]="speakerForm"
              (ngSubmit)="saveSpeaker()">
          <input class="form-input" placeholder="Nom" formControlName="nom" />
          <input class="form-input" placeholder="Titre / fonction" formControlName="titre" />
          <input class="form-input sm:col-span-2" placeholder="Organisation" formControlName="organisation" />
          <textarea class="form-input sm:col-span-2" rows="2" placeholder="Bio" formControlName="bio"></textarea>
          <div class="sm:col-span-2">
            <label class="form-label">Photo</label>
            <app-image-upload
              folder="activites"
              [value]="speakerForm.controls.photoUrl.value"
              (valueChange)="speakerForm.controls.photoUrl.setValue($event)"
            />
          </div>
          <button type="submit" class="btn-primary sm:col-span-2">
            {{ editingSpeakerId() ? 'Modifier' : 'Ajouter l\\'intervenant' }}
          </button>
        </form>
        <ul class="mt-4 space-y-2">
          @for (s of speakers(); track s.id) {
            <li class="card flex items-center gap-3 p-3 text-sm">
              @if (s.photoUrl) {
                <img [src]="s.photoUrl" alt="" class="h-10 w-10 shrink-0 rounded-full object-cover" />
              }
              <div class="min-w-0 flex-1">
                <p class="font-medium text-slate-700">{{ s.nom }}</p>
                <p class="text-slate-400">{{ s.titre }}{{ s.organisation ? ' · ' + s.organisation : '' }}</p>
              </div>
              <div class="flex gap-2">
                <button class="text-xs text-brand-700" (click)="editSpeaker(s)">Modifier</button>
                <button class="text-xs text-red-600" (click)="removeSpeaker(s)">Supprimer</button>
              </div>
            </li>
          } @empty { <li class="text-sm text-slate-400">Aucun intervenant.</li> }
        </ul>
      }

      <!-- PARTENAIRES -->
      @if (tab() === 'partenaires') {
        <form class="card mt-4 grid gap-3 p-4 sm:grid-cols-2" [formGroup]="partnerForm"
              (ngSubmit)="savePartner()">
          <input class="form-input" placeholder="Nom" formControlName="nom" />
          <select class="form-input" formControlName="niveau">
            <option value="">Niveau…</option>
            <option value="PLATINE">Platine</option>
            <option value="OR">Or</option>
            <option value="ARGENT">Argent</option>
            <option value="BRONZE">Bronze</option>
            <option value="PARTENAIRE">Partenaire</option>
            <option value="PARTENAIRE_MEDIA">Partenaire média</option>
            <option value="PARTENAIRE_INSTITUTIONNEL">Partenaire institutionnel</option>
          </select>
          <input class="form-input sm:col-span-2" placeholder="Site web" formControlName="siteWeb" />
          <div class="sm:col-span-2">
            <label class="form-label">Logo du partenaire</label>
            <app-image-upload
              folder="activites"
              [value]="partnerForm.controls.logoUrl.value"
              (valueChange)="partnerForm.controls.logoUrl.setValue($event)"
            />
          </div>
          <button type="submit" class="btn-primary sm:col-span-2">
            {{ editingPartnerId() ? 'Modifier' : 'Ajouter le partenaire' }}
          </button>
        </form>
        <ul class="mt-4 space-y-2">
          @for (p of partners(); track p.id) {
            <li class="card flex items-center gap-3 p-3 text-sm">
              @if (p.logoUrl) {
                <img [src]="p.logoUrl" alt="" class="h-10 w-16 shrink-0 rounded object-contain" />
              }
              <div class="min-w-0 flex-1">
                <p class="font-medium text-slate-700">{{ p.nom }}</p>
                <p class="text-slate-400">{{ p.niveau }}</p>
              </div>
              <div class="flex gap-2">
                <button class="text-xs text-brand-700" (click)="editPartner(p)">Modifier</button>
                <button class="text-xs text-red-600" (click)="removePartner(p)">Supprimer</button>
              </div>
            </li>
          } @empty { <li class="text-sm text-slate-400">Aucun partenaire.</li> }
        </ul>
      }

      <!-- BILLETTERIE -->
      @if (tab() === 'billetterie') {
        <form class="card mt-4 grid gap-3 p-4 sm:grid-cols-2" [formGroup]="ticketForm"
              (ngSubmit)="saveTicket()">
          <input class="form-input" placeholder="Nom (ex : Standard, VIP…)" formControlName="nom" />
          <input type="number" class="form-input" placeholder="Prix (FCFA)" formControlName="prixMontant" />
          <input type="number" class="form-input" placeholder="Quantité disponible"
                 formControlName="quantiteTotale" />
          <input type="number" class="form-input" placeholder="Limite par personne"
                 formControlName="limiteParUtilisateur" />
          <select class="form-input" formControlName="portee">
            <option value="EVENEMENT">Accès à tout l'événement</option>
            <option value="ACTIVITE">Accès à des activités précises</option>
          </select>
          <input class="form-input" placeholder="Description" formControlName="description" />
          @if (ticketForm.value.portee === 'ACTIVITE') {
            <div class="sm:col-span-2 rounded-lg border border-slate-200 p-2 text-sm">
              <p class="mb-1 font-medium text-slate-600">Activités couvertes par ce ticket</p>
              @for (a of activities(); track a.id) {
                <label class="flex items-center gap-2">
                  <input type="checkbox" [value]="a.id" (change)="toggleActivity(a.id, $event)"
                         [checked]="selectedActivityIds().includes(a.id)" />
                  {{ a.titre }}
                </label>
              } @empty {
                <p class="text-slate-400">Ajoutez d'abord des activités dans l'onglet Programme.</p>
              }
            </div>
          }
          @if (ticketError()) {
            <p class="sm:col-span-2 text-sm text-red-700">{{ ticketError() }}</p>
          }
          <button type="submit" class="btn-primary sm:col-span-2">
            {{ editingTicketId() ? 'Modifier la catégorie' : 'Ajouter la catégorie' }}
          </button>
        </form>
        <ul class="mt-4 space-y-2">
          @for (t of tickets(); track t.id) {
            <li class="card flex items-center justify-between p-3 text-sm">
              <div>
                <p class="font-medium text-slate-700">{{ t.nom }} — {{ prix(t.prixMontant) }}</p>
                <p class="text-slate-400">
                  {{ t.quantiteVendue }}/{{ t.quantiteTotale }} vendus · {{ t.quantiteRestante }} restants
                  · {{ t.portee === 'ACTIVITE' ? (t.activites.length + ' activité(s)') : 'événement entier' }}
                </p>
              </div>
              <div class="flex gap-2">
                <button class="text-xs text-brand-700" (click)="editTicket(t)">Modifier</button>
                <button class="text-xs text-red-600" (click)="removeTicket(t)">Supprimer</button>
              </div>
            </li>
          } @empty { <li class="text-sm text-slate-400">Aucune catégorie de tickets.</li> }
        </ul>
      }

      <!-- STANDS -->
      @if (tab() === 'stands') {
        @if (!e.standsActifs) {
          <p class="card mt-4 bg-amber-50 p-4 text-sm text-amber-700">
            Activez « Réservation de stands » dans l'onglet Informations pour proposer des stands.
          </p>
        }
        <form class="card mt-4 grid gap-3 p-4 sm:grid-cols-2" [formGroup]="standForm"
              (ngSubmit)="saveStandType()">
          <input class="form-input" placeholder="Nom (ex : Standard, Premium, VIP)" formControlName="nom" />
          <input class="form-input" placeholder="Dimensions (ex : 3m x 3m)" formControlName="dimensions" />
          <input type="number" class="form-input" placeholder="Prix (FCFA)" formControlName="prixMontant" />
          <input type="number" class="form-input" placeholder="Nombre de stands" formControlName="quantiteTotale" />
          <input class="form-input sm:col-span-2" placeholder="Équipements inclus" formControlName="equipements" />
          @if (standError()) { <p class="sm:col-span-2 text-sm text-red-700">{{ standError() }}</p> }
          <button type="submit" class="btn-primary sm:col-span-2">
            {{ editingStandTypeId() ? 'Modifier le type' : 'Ajouter le type de stand' }}
          </button>
        </form>
        <ul class="mt-4 space-y-2">
          @for (t of standTypes(); track t.id) {
            <li class="card flex items-center justify-between p-3 text-sm">
              <div>
                <p class="font-medium text-slate-700">{{ t.nom }} — {{ prix(t.prixMontant) }}</p>
                <p class="text-slate-400">
                  {{ t.quantiteReservee }}/{{ t.quantiteTotale }} réservés · {{ t.quantiteRestante }} disponibles
                  {{ t.dimensions ? ' · ' + t.dimensions : '' }}
                </p>
              </div>
              <div class="flex gap-2">
                <button class="text-xs text-brand-700" (click)="editStandType(t)">Modifier</button>
                <button class="text-xs text-red-600" (click)="removeStandType(t)">Supprimer</button>
              </div>
            </li>
          } @empty { <li class="text-sm text-slate-400">Aucun type de stand.</li> }
        </ul>

        @if (standReservations().length) {
          <h3 class="mt-6 font-semibold text-slate-700">Réservations</h3>
          <ul class="mt-2 space-y-1 text-sm">
            @for (r of standReservations(); track r.id) {
              <li class="card flex items-center justify-between p-3">
                <span>{{ r.standNumero }} · {{ r.structureNom || r.numeroReservation }} · {{ r.montantFormatte }}</span>
                <app-status-badge [value]="r.statut" />
              </li>
            }
          </ul>
        }
      }

      <!-- INSCRIPTIONS -->
      @if (tab() === 'inscriptions') {
        <form class="card mt-4 grid gap-2 p-4 sm:grid-cols-3" (ngSubmit)="broadcast()">
          <input class="form-input sm:col-span-1" placeholder="Titre du message"
                 [(ngModel)]="broadcastTitre" name="bt" />
          <input class="form-input sm:col-span-2" placeholder="Message aux inscrits confirmés"
                 [(ngModel)]="broadcastContenu" name="bc" />
          <button type="submit" class="btn-primary sm:col-span-3">Envoyer à tous les inscrits</button>
          @if (broadcastInfo()) { <p class="sm:col-span-3 text-sm text-green-700">{{ broadcastInfo() }}</p> }
        </form>
        <div class="mt-4 space-y-2">
          @for (r of registrations(); track r.id) {
            <div class="card flex items-center justify-between p-3 text-sm">
              <div>
                <p class="font-medium text-slate-700">
                  {{ r.contactNom || r.reference }}
                  {{ r.structureNom ? ' — ' + r.structureNom : '' }}
                </p>
                <p class="text-slate-400">
                  {{ r.reference }} · {{ r.type }} · {{ r.nombreParticipants }} participant(s)
                  {{ r.ticketOrderStatut ? ' · billets ' + r.ticketOrderStatut : '' }}
                </p>
              </div>
              <div class="flex items-center gap-2">
                <app-status-badge [value]="r.statut" />
                @if (r.statut === 'EN_ATTENTE') {
                  <button class="btn-ghost text-green-700" (click)="confirmRegistration(r)">Valider</button>
                  <button class="btn-ghost text-red-700" (click)="rejectRegistration(r)">Refuser</button>
                }
              </div>
            </div>
          } @empty { <p class="text-sm text-slate-400">Aucune inscription.</p> }
        </div>
      }

      <!-- CONTROLE -->
      @if (tab() === 'accreditations') {
        <div class="mt-4">
          <p class="mb-3 text-sm text-slate-500">
            Badges nominatifs (conférencier, exposant, modérateur, presse, staff…) pour une
            activité précise ou pour tout l'événement. Chaque badge porte un QR scannable à l'entrée.
          </p>
          <app-accreditations-panel [eventId]="id()" />
        </div>
      }

      @if (tab() === 'controle') {
        <div class="mt-4 grid gap-4 lg:grid-cols-2">
          <div class="card p-4">
            <h3 class="font-semibold text-slate-800">Personnel de contrôle</h3>
            <form class="mt-3 flex gap-2" (ngSubmit)="addStaff()">
              <input class="form-input" placeholder="E-mail d'un utilisateur inscrit"
                     [(ngModel)]="staffEmail" name="staffEmail" />
              <button type="submit" class="btn-primary">Ajouter</button>
            </form>
            @if (staffError()) { <p class="mt-2 text-sm text-red-700">{{ staffError() }}</p> }
            <ul class="mt-3 divide-y divide-slate-100 text-sm">
              @for (s of staff(); track s.id) {
                <li class="flex items-center justify-between py-2">
                  <span>{{ s.fullName }} · {{ s.email }}</span>
                  <button class="text-xs text-red-600" (click)="removeStaff(s)">Retirer</button>
                </li>
              } @empty { <li class="py-2 text-slate-400">Aucun personnel.</li> }
            </ul>
            <p class="mt-2 text-xs text-slate-400">
              Le personnel accède au scanner via « Contrôle à l'entrée ».
            </p>
          </div>

          <div class="card p-4">
            <h3 class="font-semibold text-slate-800">Journal des contrôles</h3>
            <ul class="mt-3 divide-y divide-slate-100 text-sm">
              @for (c of checkins(); track c.id) {
                <li class="flex items-center justify-between py-2">
                  <span>{{ dt(c.scannedAt) }}</span>
                  <app-status-badge [value]="c.resultat" />
                </li>
              } @empty { <li class="py-2 text-slate-400">Aucun scan.</li> }
            </ul>
          </div>
        </div>
      }

      <!-- STATS -->
      @if (tab() === 'stats' && stats(); as s) {
        <div class="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div class="card p-4"><p class="text-xs uppercase text-slate-400">Billets vendus</p>
            <p class="mt-2 text-2xl font-bold">{{ s['billetsVendus'] }} / {{ s['billetsTotal'] }}</p></div>
          <div class="card p-4"><p class="text-xs uppercase text-slate-400">Remplissage</p>
            <p class="mt-2 text-2xl font-bold">{{ s['tauxRemplissage'] }} %</p></div>
          <div class="card p-4"><p class="text-xs uppercase text-slate-400">Revenus</p>
            <p class="mt-2 text-2xl font-bold">{{ fcfa(num(s['revenus'])) }}</p></div>
          <div class="card p-4"><p class="text-xs uppercase text-slate-400">Stands réservés</p>
            <p class="mt-2 text-2xl font-bold">{{ s['standsReserves'] }} / {{ s['standsTotal'] }}</p></div>
          <div class="card p-4"><p class="text-xs uppercase text-slate-400">Inscriptions confirmées</p>
            <p class="mt-2 text-2xl font-bold">{{ s['inscriptionsConfirmees'] }}</p></div>
          <div class="card p-4"><p class="text-xs uppercase text-slate-400">En attente</p>
            <p class="mt-2 text-2xl font-bold">{{ s['inscriptionsEnAttente'] }}</p></div>
          <div class="card p-4"><p class="text-xs uppercase text-slate-400">Structures participantes</p>
            <p class="mt-2 text-2xl font-bold">{{ s['structuresParticipantes'] }}</p></div>
          <div class="card p-4"><p class="text-xs uppercase text-slate-400">Entrées validées</p>
            <p class="mt-2 text-2xl font-bold">{{ s['entreesValidees'] }}</p></div>
        </div>
        @if (series()) {
          <div class="mt-4 grid gap-4 lg:grid-cols-2">
            <div class="card p-4">
              <h3 class="font-semibold text-slate-700">Billets par catégorie</h3>
              <app-bar-chart [data]="categorieBars()" />
            </div>
            <div class="card p-4">
              <h3 class="font-semibold text-slate-700">Inscriptions (30 j)</h3>
              <app-bar-chart [data]="inscriptionBars()" />
            </div>
          </div>
        }
      }
    } @else {
      <p class="mt-6 text-sm text-slate-500">Chargement…</p>
    }
  `,
})
export class EventEditorComponent {
  private fb = inject(FormBuilder);
  private service = inject(EventsService);
  private ticketsService = inject(TicketsService);
  private standsService = inject(StandsService);
  private registrationsService = inject(RegistrationsService);
  private checkinService = inject(CheckinService);
  private statsService = inject(StatsService);
  private auth = inject(AuthService);

  id = input.required<string>();
  event = signal<EventDetail | null>(null);
  categories = signal<EventCategory[]>([]);
  activities = signal<Activity[]>([]);
  speakers = signal<Speaker[]>([]);
  partners = signal<Partner[]>([]);
  tickets = signal<EventTicket[]>([]);
  selectedActivityIds = signal<string[]>([]);
  standTypes = signal<StandType[]>([]);
  standReservations = signal<any[]>([]);
  registrations = signal<Registration[]>([]);
  staff = signal<StaffMember[]>([]);
  checkins = signal<CheckinView[]>([]);
  staffEmail = '';
  staffError = signal<string | null>(null);
  broadcastTitre = '';
  broadcastContenu = '';
  broadcastInfo = signal<string | null>(null);
  stats = signal<StatMap | null>(null);
  series = signal<EventSeries | null>(null);
  editingStandTypeId = signal<string | null>(null);
  standError = signal<string | null>(null);

  tab = signal<Tab>('infos');
  tabs: { id: Tab; label: string }[] = [
    { id: 'infos', label: 'Informations' },
    { id: 'programme', label: 'Programme' },
    { id: 'intervenants', label: 'Intervenants' },
    { id: 'partenaires', label: 'Partenaires' },
    { id: 'billetterie', label: 'Billetterie' },
    { id: 'stands', label: 'Stands' },
    { id: 'inscriptions', label: 'Inscriptions' },
    { id: 'accreditations', label: 'Accréditations' },
    { id: 'controle', label: 'Contrôle' },
    { id: 'stats', label: 'Statistiques' },
  ];
  activityTypes = ['CEREMONIE', 'CONFERENCE', 'PANEL', 'ATELIER', 'FORMATION', 'TABLE_RONDE',
    'NETWORKING', 'PAUSE', 'SPECTACLE', 'AUTRE'];

  saving = signal(false);
  error = signal<string | null>(null);
  transitionError = signal<string | null>(null);
  editingActivityId = signal<string | null>(null);
  editingSpeakerId = signal<string | null>(null);
  editingPartnerId = signal<string | null>(null);
  editingTicketId = signal<string | null>(null);
  ticketError = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    nom: ['', Validators.required],
    sigle: [''],
    categoryId: [''],
    dateDebut: ['', Validators.required],
    dateFin: ['', Validators.required],
    ville: [''],
    lieu: [''],
    adresse: [''],
    capaciteMax: [null as number | null],
    contactEmail: [''],
    inscriptionDebut: [''],
    inscriptionFin: [''],
    descriptionCourte: [''],
    descriptionDetaillee: [''],
    conditionsParticipation: [''],
    logoUrl: [null as string | null],
    coverUrl: [null as string | null],
    hasActivities: [false],
    standsActifs: [false],
    standsParticuliers: [false],
    validationInscription: [false],
    controleSortie: [false],
  });

  activityForm = this.fb.nonNullable.group({
    titre: ['', Validators.required],
    typeActivite: [''],
    acces: ['SANS_BILLET'],
    capacite: [null as number | null],
    salle: [''],
    dateDebut: ['', Validators.required],
    dateFin: [''],
    intervenant: [''],
    moderateur: [''],
    imageUrl: [null as string | null],
  });
  speakerForm = this.fb.nonNullable.group({
    nom: ['', Validators.required], titre: [''], organisation: [''], bio: [''],
    photoUrl: [null as string | null],
  });
  partnerForm = this.fb.nonNullable.group({
    nom: ['', Validators.required], niveau: [''], siteWeb: [''],
    logoUrl: [null as string | null],
  });
  ticketForm = this.fb.nonNullable.group({
    nom: ['', Validators.required],
    prixMontant: [0, [Validators.required, Validators.min(0)]],
    quantiteTotale: [100, [Validators.required, Validators.min(1)]],
    limiteParUtilisateur: [10, [Validators.min(1)]],
    portee: ['EVENEMENT' as 'EVENEMENT' | 'ACTIVITE'],
    description: [''],
  });
  standForm = this.fb.nonNullable.group({
    nom: ['', Validators.required],
    dimensions: [''],
    prixMontant: [0, [Validators.required, Validators.min(0)]],
    quantiteTotale: [10, [Validators.required, Validators.min(1)]],
    equipements: [''],
  });

  private isAdmin = computed(() => this.auth.hasPermission('EVENT_VALIDATE'));

  actions = computed(() => {
    const e = this.event();
    if (!e) return [];
    const a: { action: string; label: string; class: string }[] = [];
    const owner = e.organizerId && this.auth.user()?.id;
    if (['BROUILLON', 'REFUSE'].includes(e.statut))
      a.push({ action: 'submit', label: 'Soumettre à validation', class: 'btn-primary' });
    if (e.statut === 'VALIDE')
      a.push({ action: 'publish', label: 'Publier', class: 'btn-primary' });
    if (['PUBLIE', 'INSCRIPTIONS_FERMEES'].includes(e.statut))
      a.push({ action: 'open-registrations', label: 'Ouvrir les inscriptions', class: 'btn-primary' });
    if (['PUBLIE', 'INSCRIPTIONS_OUVERTES'].includes(e.statut))
      a.push({ action: 'close-registrations', label: 'Fermer les inscriptions', class: 'btn-ghost text-slate-600' });
    if (this.isAdmin()) {
      if (e.statut === 'SOUMIS') {
        a.push({ action: 'validate', label: 'Valider (admin)', class: 'btn bg-green-600 text-white hover:bg-green-700' });
        a.push({ action: 'reject', label: 'Refuser (admin)', class: 'btn bg-red-600 text-white hover:bg-red-700' });
      }
      if (!['ANNULE', 'TERMINE'].includes(e.statut))
        a.push({ action: 'cancel', label: 'Annuler (admin)', class: 'btn-ghost text-red-700' });
    }
    return a;
  });

  constructor() {
    this.service.categories().subscribe((c) => this.categories.set(c));
    effect(() => {
      const id = this.id();
      if (id) this.load(id);
    });
  }

  dt = (iso?: string) => formatDateTime(iso);
  fcfa = (n?: number) => formatFcfa(n);
  prix = (n?: number) => priceLabel(n);

  private load(id: string): void {
    this.service.byId(id).subscribe((e) => {
      this.event.set(e);
      this.form.reset({
        nom: e.nom, sigle: e.sigle ?? '', categoryId: e.categoryId ?? '',
        dateDebut: toLocal(e.dateDebut), dateFin: toLocal(e.dateFin),
        ville: e.ville ?? '', lieu: e.lieu ?? '', adresse: e.adresse ?? '',
        capaciteMax: e.capaciteMax ?? null, contactEmail: e.contactEmail ?? '',
        inscriptionDebut: toLocal(e.inscriptionDebut), inscriptionFin: toLocal(e.inscriptionFin),
        descriptionCourte: e.descriptionCourte ?? '', descriptionDetaillee: e.descriptionDetaillee ?? '',
        conditionsParticipation: e.conditionsParticipation ?? '',
        logoUrl: e.logoUrl ?? null, coverUrl: e.coverUrl ?? null,
        hasActivities: e.hasActivities, standsActifs: e.standsActifs,
        standsParticuliers: e.standsParticuliers ?? false,
        validationInscription: e.validationInscription ?? false,
        controleSortie: e.controleSortie ?? false,
      });
      const editable = ['BROUILLON', 'REFUSE', 'VALIDE'].includes(e.statut) || this.isAdmin();
      editable ? this.form.enable() : this.form.disable();
    });
    this.service.activities(id).subscribe((a) => this.activities.set(a));
    this.service.speakers(id).subscribe((s) => this.speakers.set(s));
    this.service.partners(id).subscribe((p) => this.partners.set(p));
    this.ticketsService.forEvent(id).subscribe((t) => this.tickets.set(t));
    this.standsService.types(id).subscribe((t) => this.standTypes.set(t));
    this.standsService.reservationsForEvent(id).subscribe((p) => this.standReservations.set(p.content));
    this.registrationsService.forEvent(id).subscribe((p) => this.registrations.set(p.content));
    this.checkinService.staff(id).subscribe({ next: (s) => this.staff.set(s), error: () => {} });
    this.checkinService.checkins(id).subscribe({ next: (p) => this.checkins.set(p.content), error: () => {} });
    this.statsService.eventStats(id).subscribe({ next: (s) => this.stats.set(s), error: () => {} });
    this.statsService.eventSeries(id).subscribe({ next: (s) => this.series.set(s), error: () => {} });
  }

  num = (v: number | string) => (typeof v === 'number' ? v : Number(v));
  categorieBars = () =>
    (this.series()?.billetsParCategorie ?? []).map((c) => ({ label: c.label, value: c.valeur }));
  inscriptionBars = () =>
    (this.series()?.quotidien ?? [])
      .filter((d) => d.inscriptions > 0)
      .map((d) => ({ label: d.jour.slice(5), value: d.inscriptions }));

  addStaff(): void {
    if (!this.staffEmail.trim()) return;
    this.staffError.set(null);
    this.checkinService.addStaff(this.id(), this.staffEmail.trim()).subscribe({
      next: () => {
        this.staffEmail = '';
        this.checkinService.staff(this.id()).subscribe((s) => this.staff.set(s));
      },
      error: (err: HttpErrorResponse) =>
        this.staffError.set((err.error as ApiError)?.message ?? 'Ajout impossible.'),
    });
  }
  removeStaff(s: StaffMember): void {
    this.checkinService.removeStaff(this.id(), s.userId).subscribe(() =>
      this.checkinService.staff(this.id()).subscribe((x) => this.staff.set(x)),
    );
  }

  confirmRegistration(r: Registration): void {
    this.registrationsService.confirm(r.id).subscribe(() =>
      this.registrationsService.forEvent(this.id()).subscribe((p) => this.registrations.set(p.content)),
    );
  }
  rejectRegistration(r: Registration): void {
    const motif = prompt('Motif du refus ?');
    if (!motif) return;
    this.registrationsService.reject(r.id, motif).subscribe(() =>
      this.registrationsService.forEvent(this.id()).subscribe((p) => this.registrations.set(p.content)),
    );
  }
  broadcast(): void {
    if (!this.broadcastTitre.trim() || !this.broadcastContenu.trim()) return;
    this.registrationsService.broadcast(this.id(), this.broadcastTitre, this.broadcastContenu)
      .subscribe((r) => {
        this.broadcastInfo.set(`Message envoyé à ${r.destinataires} inscrit(s).`);
        this.broadcastTitre = '';
        this.broadcastContenu = '';
      });
  }

  // --- stands ---
  saveStandType(): void {
    if (this.standForm.invalid) return;
    this.standError.set(null);
    const v = this.standForm.getRawValue();
    const body = {
      nom: v.nom,
      dimensions: v.dimensions || undefined,
      prixMontant: Number(v.prixMontant),
      quantiteTotale: Number(v.quantiteTotale),
      equipements: v.equipements || undefined,
    };
    this.standsService.saveType(this.id(), body, this.editingStandTypeId() ?? undefined).subscribe({
      next: () => {
        this.editingStandTypeId.set(null);
        this.standForm.reset({ prixMontant: 0, quantiteTotale: 10 });
        this.standsService.types(this.id()).subscribe((t) => this.standTypes.set(t));
      },
      error: (err: HttpErrorResponse) =>
        this.standError.set((err.error as ApiError)?.message ?? 'Enregistrement impossible.'),
    });
  }
  editStandType(t: StandType): void {
    this.editingStandTypeId.set(t.id);
    this.standForm.reset({
      nom: t.nom, dimensions: t.dimensions ?? '', prixMontant: t.prixMontant,
      quantiteTotale: t.quantiteTotale, equipements: t.equipements ?? '',
    });
  }
  removeStandType(t: StandType): void {
    this.standsService.removeType(this.id(), t.id).subscribe({
      next: () => this.standsService.types(this.id()).subscribe((x) => this.standTypes.set(x)),
      error: (err: HttpErrorResponse) =>
        this.standError.set((err.error as ApiError)?.message ?? 'Suppression impossible.'),
    });
  }

  // --- billetterie ---
  toggleActivity(activityId: string, ev: Event): void {
    const checked = (ev.target as HTMLInputElement).checked;
    const cur = this.selectedActivityIds();
    this.selectedActivityIds.set(
      checked ? [...cur, activityId] : cur.filter((x) => x !== activityId),
    );
  }
  saveTicket(): void {
    if (this.ticketForm.invalid) return;
    this.ticketError.set(null);
    const v = this.ticketForm.getRawValue();
    const body = {
      nom: v.nom,
      prixMontant: Number(v.prixMontant),
      quantiteTotale: Number(v.quantiteTotale),
      limiteParUtilisateur: Number(v.limiteParUtilisateur),
      portee: v.portee,
      description: v.description || undefined,
      activityIds: v.portee === 'ACTIVITE' ? this.selectedActivityIds() : undefined,
    };
    this.ticketsService.save(this.id(), body, this.editingTicketId() ?? undefined).subscribe({
      next: () => {
        this.editingTicketId.set(null);
        this.selectedActivityIds.set([]);
        this.ticketForm.reset({ prixMontant: 0, quantiteTotale: 100, limiteParUtilisateur: 10, portee: 'EVENEMENT' });
        this.ticketsService.forEvent(this.id()).subscribe((t) => this.tickets.set(t));
      },
      error: (err: HttpErrorResponse) =>
        this.ticketError.set((err.error as ApiError)?.message ?? 'Enregistrement impossible.'),
    });
  }
  editTicket(t: EventTicket): void {
    this.editingTicketId.set(t.id);
    this.selectedActivityIds.set(t.activites.map((a) => a.id));
    this.ticketForm.reset({
      nom: t.nom, prixMontant: t.prixMontant, quantiteTotale: t.quantiteTotale,
      limiteParUtilisateur: t.limiteParUtilisateur, portee: t.portee, description: t.description ?? '',
    });
  }
  removeTicket(t: EventTicket): void {
    this.ticketsService.remove(this.id(), t.id).subscribe({
      next: () => this.ticketsService.forEvent(this.id()).subscribe((x) => this.tickets.set(x)),
      error: (err: HttpErrorResponse) =>
        this.ticketError.set((err.error as ApiError)?.message ?? 'Suppression impossible.'),
    });
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const payload = {
      ...v,
      categoryId: v.categoryId || undefined,
      capaciteMax: v.capaciteMax ?? undefined,
      dateDebut: new Date(v.dateDebut).toISOString(),
      dateFin: new Date(v.dateFin).toISOString(),
      inscriptionDebut: v.inscriptionDebut ? new Date(v.inscriptionDebut).toISOString() : undefined,
      inscriptionFin: v.inscriptionFin ? new Date(v.inscriptionFin).toISOString() : undefined,
    };
    this.service.update(this.id(), payload as any).subscribe({
      next: (e) => {
        this.saving.set(false);
        this.event.set(e);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set((err.error as ApiError)?.message ?? 'Enregistrement impossible.');
      },
    });
  }

  doTransition(action: string): void {
    this.transitionError.set(null);
    let body: unknown = {};
    if (action === 'reject') {
      const motif = prompt('Motif du refus ?');
      if (!motif) return;
      body = { motif };
    }
    this.service.transition(this.id(), action, body).subscribe({
      next: (e) => this.event.set(e),
      error: (err: HttpErrorResponse) =>
        this.transitionError.set((err.error as ApiError)?.message ?? 'Action impossible.'),
    });
  }

  // --- activities ---
  saveActivity(): void {
    if (this.activityForm.invalid) return;
    const v = this.activityForm.getRawValue();
    const body: Partial<Activity> = {
      titre: v.titre, salle: v.salle || undefined, intervenant: v.intervenant || undefined,
      moderateur: v.moderateur || undefined,
      imageUrl: v.imageUrl || undefined,
      typeActivite: (v.typeActivite || undefined) as Activity['typeActivite'],
      acces: (v.acces || 'SANS_BILLET') as Activity['acces'],
      capacite: v.capacite ?? undefined,
      dateDebut: new Date(v.dateDebut).toISOString(),
      dateFin: v.dateFin ? new Date(v.dateFin).toISOString() : undefined,
    };
    this.service.saveActivity(this.id(), body, this.editingActivityId() ?? undefined).subscribe(() => {
      this.editingActivityId.set(null);
      this.activityForm.reset();
      this.service.activities(this.id()).subscribe((a) => this.activities.set(a));
    });
  }
  editActivity(a: Activity): void {
    this.editingActivityId.set(a.id);
    this.activityForm.reset({
      titre: a.titre, typeActivite: a.typeActivite ?? '', salle: a.salle ?? '',
      acces: a.acces ?? 'SANS_BILLET', capacite: a.capacite ?? null,
      dateDebut: toLocal(a.dateDebut), dateFin: toLocal(a.dateFin),
      intervenant: a.intervenant ?? '', moderateur: a.moderateur ?? '',
      imageUrl: a.imageUrl ?? null,
    });
  }
  removeActivity(a: Activity): void {
    this.service.deleteActivity(this.id(), a.id).subscribe(() =>
      this.service.activities(this.id()).subscribe((x) => this.activities.set(x)),
    );
  }

  // --- speakers ---
  saveSpeaker(): void {
    if (this.speakerForm.invalid) return;
    const v = this.speakerForm.getRawValue();
    this.service.saveSpeaker(
      this.id(),
      { ...v, photoUrl: v.photoUrl || undefined },
      this.editingSpeakerId() ?? undefined,
    ).subscribe(() => {
        this.editingSpeakerId.set(null);
        this.speakerForm.reset();
        this.service.speakers(this.id()).subscribe((s) => this.speakers.set(s));
      });
  }
  editSpeaker(s: Speaker): void {
    this.editingSpeakerId.set(s.id);
    this.speakerForm.reset({
      nom: s.nom, titre: s.titre ?? '', organisation: s.organisation ?? '', bio: s.bio ?? '',
      photoUrl: s.photoUrl ?? null,
    });
  }
  removeSpeaker(s: Speaker): void {
    this.service.deleteSpeaker(this.id(), s.id).subscribe(() =>
      this.service.speakers(this.id()).subscribe((x) => this.speakers.set(x)),
    );
  }

  // --- partners ---
  savePartner(): void {
    if (this.partnerForm.invalid) return;
    const v = this.partnerForm.getRawValue();
    this.service.savePartner(this.id(),
      {
        nom: v.nom, niveau: (v.niveau || undefined) as Partner['niveau'],
        siteWeb: v.siteWeb || undefined, logoUrl: v.logoUrl || undefined,
      },
      this.editingPartnerId() ?? undefined).subscribe(() => {
        this.editingPartnerId.set(null);
        this.partnerForm.reset();
        this.service.partners(this.id()).subscribe((p) => this.partners.set(p));
      });
  }
  editPartner(p: Partner): void {
    this.editingPartnerId.set(p.id);
    this.partnerForm.reset({
      nom: p.nom, niveau: p.niveau ?? '', siteWeb: p.siteWeb ?? '',
      logoUrl: p.logoUrl ?? null,
    });
  }
  removePartner(p: Partner): void {
    this.service.deletePartner(this.id(), p.id).subscribe(() =>
      this.service.partners(this.id()).subscribe((x) => this.partners.set(x)),
    );
  }
}

function toLocal(iso?: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
