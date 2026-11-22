import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ApiError } from '../../core/models';
import { EventsService } from '../events/events.service';
import { Activity, EventSummary } from '../events/event.models';
import { StandsService } from '../stands/stands.service';
import { Stand } from '../stands/stand.models';
import { formatDateTime, formatFcfa, formatTime } from '../../shared/format';
import {
  ActivityMetrics, EventReport, ExportFormat, FluxPoint, FluxReport, Granularite, ParticipantCriteria,
  ParticipantPreview, Perimetre, ReportMetrics, ReportsService, Source,
} from './reports.service';

type Tab = 'stats' | 'flux' | 'export';

const SOURCES: Record<Perimetre, { value: Source; label: string }[]> = {
  EVENEMENT: [
    { value: 'BILLETS', label: 'Détenteurs de billets' },
    { value: 'PRESENTS', label: 'Présents (entrées contrôlées)' },
    { value: 'INSCRITS', label: 'Participants inscrits' },
    { value: 'VISITES', label: 'Visiteurs de tous les stands' },
  ],
  ACTIVITE: [
    { value: 'BILLETS', label: "Détenteurs d'un billet pour l'activité" },
    { value: 'PRESENTS', label: "Présents à l'activité (scans)" },
  ],
  STAND: [{ value: 'VISITES', label: 'Visiteurs du stand' }],
};

const DAY_FMT = new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' });

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Statistiques & exports</h1>
    <p class="mt-1 text-sm text-slate-500">
      Chiffres d'une journée ou de tout l'événement, flux des capteurs laser par jour ou par heure,
      et export des listes de participants.
    </p>

    <div class="card mt-4 flex flex-wrap items-end gap-3 p-4">
      @if (isAdmin()) {
        <div>
          <label class="form-label">Rechercher</label>
          <input class="form-input" [(ngModel)]="search" (keyup.enter)="loadEvents()"
                 placeholder="Nom de l'événement" />
        </div>
      }
      <div class="min-w-64 flex-1">
        <label class="form-label">Événement</label>
        <select class="form-input" [ngModel]="eventId()" (ngModelChange)="selectEvent($event)">
          <option value="">— Choisir —</option>
          @for (e of events(); track e.id) { <option [value]="e.id">{{ e.nom }}</option> }
        </select>
      </div>
    </div>

    @if (eventId()) {
      <div class="mt-4 flex gap-1 border-b border-slate-200">
        @for (t of tabs; track t.id) {
          <button type="button" (click)="setTab(t.id)"
                  class="-mb-px border-b-2 px-4 py-2 text-sm font-medium"
                  [class.border-brand-600]="tab() === t.id" [class.text-brand-700]="tab() === t.id"
                  [class.border-transparent]="tab() !== t.id" [class.text-slate-500]="tab() !== t.id">
            {{ t.label }}
          </button>
        }
      </div>

      <!-- ============================== STATISTIQUES ============================== -->
      @if (tab() === 'stats') {
        @if (report(); as r) {
          <div class="mt-4 flex flex-wrap gap-2">
            <button type="button" class="chip" [class.on]="statsDay() === null" (click)="loadStats(null)">
              Tout l'événement
            </button>
            @for (d of r.jours; track d; let i = $index) {
              <button type="button" class="chip" [class.on]="statsDay() === d" (click)="loadStats(d)">
                Jour {{ i + 1 }} · {{ dayLabel(d) }}
              </button>
            }
          </div>

          <div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            @for (k of kpis; track k.key) {
              <div class="card p-4">
                <p class="text-xs uppercase text-slate-400">{{ k.label }}</p>
                <p class="mt-1 text-2xl font-bold text-slate-800">{{ kpi(r.totaux, k.key) }}</p>
              </div>
            }
          </div>

          <div class="card mt-4 overflow-x-auto p-4">
            <h3 class="font-semibold text-slate-700">Jour par jour</h3>
            <table class="mt-2 w-full text-sm">
              <thead class="text-left text-xs uppercase text-slate-400">
                <tr>
                  <th class="py-1 pr-3">Jour</th><th class="pr-3">Billets</th><th class="pr-3">Inscriptions</th>
                  <th class="pr-3">Entrées (QR)</th><th class="pr-3">Sorties (QR)</th><th class="pr-3">Visiteurs uniques</th>
                  <th class="pr-3">Capteurs ↑</th><th class="pr-3">Capteurs ↓</th><th class="pr-3">Visites stands</th>
                  <th>Revenus</th>
                </tr>
              </thead>
              <tbody>
                @for (d of r.parJour; track d.jour; let i = $index) {
                  <tr class="border-t border-slate-100" [class.bg-brand-50]="statsDay() === d.jour">
                    <td class="py-1.5 pr-3 font-medium">J{{ i + 1 }} · {{ dayLabel(d.jour) }}</td>
                    <td class="pr-3">{{ d.valeurs.billetsEmis }}</td>
                    <td class="pr-3">{{ d.valeurs.inscriptions }}</td>
                    <td class="pr-3">{{ d.valeurs.entrees }}</td>
                    <td class="pr-3">{{ d.valeurs.sorties }}</td>
                    <td class="pr-3">{{ d.valeurs.visiteursUniques }}</td>
                    <td class="pr-3">{{ d.valeurs.capteurEntrees }}</td>
                    <td class="pr-3">{{ d.valeurs.capteurSorties }}</td>
                    <td class="pr-3">{{ d.valeurs.visitesStands }}</td>
                    <td>{{ fcfa(d.valeurs.revenus) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="card mt-4 p-4">
            <div class="flex flex-wrap items-end justify-between gap-3">
              <h3 class="font-semibold text-slate-700">Comparer avec une autre édition</h3>
              <select class="form-input max-w-sm" [ngModel]="compareId()" (ngModelChange)="compareWith($event)"
                      aria-label="Édition à comparer">
                <option value="">— Choisir un événement —</option>
                @for (e of events(); track e.id) {
                  @if (e.id !== eventId()) { <option [value]="e.id">{{ e.nom }}</option> }
                }
              </select>
            </div>
            @if (compare(); as c) {
              <table class="mt-3 w-full text-sm">
                <thead class="text-left text-xs uppercase text-slate-500">
                  <tr><th class="py-1 pr-3">Indicateur</th><th class="pr-3">{{ r.evenement }}</th>
                    <th class="pr-3">{{ c.evenement }}</th><th>Évolution</th></tr>
                </thead>
                <tbody>
                  @for (k of compareRows; track k.key) {
                    <tr class="border-t border-slate-100">
                      <td class="py-1.5 pr-3">{{ k.label }}</td>
                      <td class="pr-3 font-semibold">{{ $any(r.totaux)[k.key] }}</td>
                      <td class="pr-3">{{ $any(c.totaux)[k.key] }}</td>
                      <td [class.text-green-700]="delta($any(r.totaux)[k.key], $any(c.totaux)[k.key]) > 0"
                          [class.text-red-700]="delta($any(r.totaux)[k.key], $any(c.totaux)[k.key]) < 0">
                        {{ deltaLabel($any(r.totaux)[k.key], $any(c.totaux)[k.key]) }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>

          @if (r.parActivite.length) {
            <div class="card mt-4 overflow-x-auto p-4">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <h3 class="font-semibold text-slate-700">
                  Par activité {{ statsDay() ? '(' + dayLabel(statsDay()!) + ')' : '' }}
                </h3>
                <div class="flex gap-2">
                  <button type="button" class="btn-ghost text-brand-700" [disabled]="exporting()"
                          (click)="downloadActivities('CSV')">CSV</button>
                  <button type="button" class="btn-ghost text-brand-700" [disabled]="exporting()"
                          (click)="downloadActivities('XLSX')">Excel</button>
                  <button type="button" class="btn-primary" [disabled]="exporting()"
                          (click)="downloadBilan()">Rapport de bilan (PDF)</button>
                </div>
              </div>
              <p class="mt-1 text-xs text-slate-500">
                Taux de présence = personnes uniques scannées ÷ billets émis pour la session.
                Cliquez sur un en-tête pour trier.
              </p>
              <table class="mt-2 w-full text-sm">
                <thead class="text-left text-xs uppercase text-slate-500">
                  <tr>
                    @for (c of activityColumns; track c.key) {
                      <th class="cursor-pointer select-none py-1 pr-3 hover:text-brand-700"
                          [attr.aria-sort]="sortKey() === c.key ? (sortAsc() ? 'ascending' : 'descending') : null"
                          (click)="sortBy(c.key)">
                        {{ c.label }}@if (sortKey() === c.key) { {{ sortAsc() ? ' ▲' : ' ▼' }} }
                      </th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (a of sortedActivities(); track a.id) {
                    <tr class="border-t border-slate-100">
                      <td class="py-1.5 pr-3">{{ a.titre }}</td>
                      <td class="pr-3 text-slate-500">{{ dt(a.dateDebut) }}</td>
                      <td class="pr-3 text-slate-500">{{ a.salle || '—' }}</td>
                      <td class="pr-3 font-semibold">{{ a.inscrits }}</td>
                      <td class="pr-3">{{ a.inscritsSurPlace }}</td>
                      <td class="pr-3 font-semibold">{{ a.visiteursUniques }}</td>
                      <td class="pr-3" [class.text-red-700]="(a.tauxPresence ?? 100) < 50"
                          [class.text-green-700]="(a.tauxPresence ?? 0) >= 75">{{ pct(a.tauxPresence) }}</td>
                      <td class="pr-3">{{ a.capacite ?? '—' }}</td>
                      <td class="pr-3">{{ pct(a.tauxRemplissage) }}</td>
                      <td class="pr-3">{{ a.entrees }}</td>
                      <td class="pr-3">{{ a.sorties }}</td>
                      <td class="pr-3">{{ a.autoEnregistres }}</td>
                      <td>{{ a.dureeMoyenneMinutes != null ? a.dureeMoyenneMinutes + ' min' : '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        } @else if (!error()) {
          <p class="mt-4 text-sm text-slate-400">Chargement…</p>
        }
      }

      <!-- ============================== FLUX CAPTEURS ============================== -->
      @if (tab() === 'flux') {
        <div class="card mt-4 flex flex-wrap items-end gap-3 p-4">
          <div>
            <label class="form-label">Affichage</label>
            <div class="flex gap-1">
              <button type="button" class="chip" [class.on]="granularite() === 'JOUR'"
                      (click)="setGranularite('JOUR')">Par jour</button>
              <button type="button" class="chip" [class.on]="granularite() === 'HEURE'"
                      (click)="setGranularite('HEURE')">Par heure</button>
            </div>
          </div>
          @if (granularite() === 'HEURE') {
            <div>
              <label class="form-label">Journée</label>
              <input type="date" class="form-input" [ngModel]="fluxDay()" (ngModelChange)="setFluxDay($event)" />
            </div>
          }
          <div>
            <label class="form-label">Capteur</label>
            <select class="form-input" [ngModel]="capteurId()" (ngModelChange)="setCapteur($event)">
              <option value="">Tous les capteurs</option>
              @for (c of flux()?.capteurs ?? []; track c.id) {
                <option [value]="c.id">{{ c.nom }}{{ c.actif ? '' : ' (révoqué)' }}</option>
              }
            </select>
          </div>
          <button type="button" class="btn-ghost text-brand-700" (click)="loadFlux()">↻ Actualiser</button>
        </div>

        @if (flux(); as f) {
          <div class="mt-4 grid gap-3 sm:grid-cols-4">
            <div class="card p-4"><p class="text-xs uppercase text-slate-400">Entrées</p>
              <p class="mt-1 text-2xl font-bold text-brand-700">{{ f.totalEntrees }}</p></div>
            <div class="card p-4"><p class="text-xs uppercase text-slate-400">Sorties</p>
              <p class="mt-1 text-2xl font-bold text-amber-600">{{ f.totalSorties }}</p></div>
            <div class="card p-4"><p class="text-xs uppercase text-slate-400">Présents (fin de période)</p>
              <p class="mt-1 text-2xl font-bold text-slate-800">{{ lastPresent() }}</p></div>
            <div class="card p-4"><p class="text-xs uppercase text-slate-400">Pic d'affluence</p>
              <p class="mt-1 text-2xl font-bold text-slate-800">
                {{ f.pic ? pointLabel(f.pic) : '—' }}
              </p>
              @if (f.pic) { <p class="text-xs text-slate-400">{{ f.pic.entrees }} entrées</p> }
            </div>
          </div>

          <div class="card mt-4 p-4">
            <div class="flex flex-wrap items-center gap-4 text-xs text-slate-500">
              <span class="flex items-center gap-1"><i class="legend bg-brand-600"></i> Entrées</span>
              <span class="flex items-center gap-1"><i class="legend bg-amber-500"></i> Sorties</span>
              <span class="flex items-center gap-1"><i class="legend h-0.5 bg-slate-700"></i> Présents</span>
            </div>
            @if (f.points.length) {
              <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" class="mt-2 w-full" role="img"
                   aria-label="Flux des capteurs">
                @for (g of grid(); track g.y) {
                  <line x1="34" [attr.x2]="W" [attr.y1]="g.y" [attr.y2]="g.y" class="stroke-slate-100" />
                  <text x="30" [attr.y]="g.y + 3" text-anchor="end" class="fill-slate-400 text-[9px]">{{ g.v }}</text>
                }
                @for (p of f.points; track p.debut; let i = $index) {
                  <rect [attr.x]="slotX(i) + 1" [attr.y]="y(p.entrees)" [attr.width]="barW()"
                        [attr.height]="H - 22 - y(p.entrees)" rx="2" class="fill-brand-600">
                    <title>{{ pointLabel(p) }} : {{ p.entrees }} entrées</title>
                  </rect>
                  <rect [attr.x]="slotX(i) + 1 + barW()" [attr.y]="y(p.sorties)" [attr.width]="barW()"
                        [attr.height]="H - 22 - y(p.sorties)" rx="2" class="fill-amber-500">
                    <title>{{ pointLabel(p) }} : {{ p.sorties }} sorties</title>
                  </rect>
                  @if (showTick(i)) {
                    <text [attr.x]="slotX(i) + slotW() / 2" [attr.y]="H - 8" text-anchor="middle"
                          class="fill-slate-400 text-[9px]">{{ tick(p) }}</text>
                  }
                }
                <polyline [attr.points]="presentLine()" fill="none" class="stroke-slate-700" stroke-width="2" />
              </svg>
            } @else {
              <p class="mt-2 text-sm text-slate-400">Aucun passage compté sur cette période.</p>
            }
          </div>

          <div class="card mt-4 overflow-x-auto p-4">
            <h3 class="font-semibold text-slate-700">Détail {{ granularite() === 'HEURE' ? 'heure par heure' : 'jour par jour' }}</h3>
            <table class="mt-2 w-full text-sm">
              <thead class="text-left text-xs uppercase text-slate-400">
                <tr><th class="py-1 pr-3">{{ granularite() === 'HEURE' ? 'Heure' : 'Jour' }}</th>
                  <th class="pr-3">Entrées capteurs</th><th class="pr-3">Sorties capteurs</th>
                  <th class="pr-3">Présents</th><th class="pr-3">Entrées QR</th><th>Sorties QR</th></tr>
              </thead>
              <tbody>
                @for (p of f.points; track p.debut) {
                  @if (granularite() === 'JOUR' || p.entrees || p.sorties || p.scanEntrees || p.scanSorties) {
                    <tr class="border-t border-slate-100">
                      <td class="py-1.5 pr-3 font-medium">{{ pointLabel(p) }}</td>
                      <td class="pr-3">{{ p.entrees }}</td>
                      <td class="pr-3">{{ p.sorties }}</td>
                      <td class="pr-3">{{ p.presents }}</td>
                      <td class="pr-3 text-slate-500">{{ p.scanEntrees }}</td>
                      <td class="text-slate-500">{{ p.scanSorties }}</td>
                    </tr>
                  }
                }
              </tbody>
            </table>
            @if (granularite() === 'HEURE') {
              <p class="mt-2 text-xs text-slate-400">Les heures sans aucun passage sont masquées dans ce tableau.</p>
            }
          </div>

          @if (f.capteurs.length) {
            <div class="card mt-4 p-4">
              <h3 class="font-semibold text-slate-700">Par capteur (période affichée)</h3>
              <ul class="mt-2 divide-y divide-slate-100 text-sm">
                @for (c of f.capteurs; track c.id) {
                  <li class="flex flex-wrap items-center justify-between gap-2 py-2">
                    <span class="font-medium">{{ c.nom }}
                      @if (!c.actif) { <span class="badge bg-slate-100 text-slate-500">révoqué</span> }
                    </span>
                    <span class="text-slate-500">↑ {{ c.entrees }} · ↓ {{ c.sorties }}
                      @if (c.derniereActivite) { · vu {{ dt(c.derniereActivite) }} }
                    </span>
                  </li>
                }
              </ul>
            </div>
          } @else {
            <p class="mt-4 text-sm text-slate-400">
              Aucun capteur n'est déclaré pour cet événement (onglet Contrôle de l'événement).
            </p>
          }
        }
      }

      <!-- ============================== EXPORT ============================== -->
      @if (tab() === 'export') {
        <div class="card mt-4 grid gap-4 p-4 md:grid-cols-2">
          <div>
            <label class="form-label">Liste de</label>
            <select class="form-input" [ngModel]="perimetre()" (ngModelChange)="setPerimetre($event)">
              <option value="EVENEMENT">Tout l'événement</option>
              <option value="ACTIVITE" [disabled]="!activities().length">Une activité</option>
              <option value="STAND" [disabled]="!stands().length">Un stand</option>
            </select>
          </div>
          @if (perimetre() === 'ACTIVITE') {
            <div>
              <label class="form-label">Activité</label>
              <select class="form-input" [ngModel]="cibleId()" (ngModelChange)="cibleId.set($event); refreshPreview()">
                @for (a of activities(); track a.id) {
                  <option [value]="a.id">{{ time(a.dateDebut) }} · {{ a.titre }}</option>
                }
              </select>
            </div>
          } @else if (perimetre() === 'STAND') {
            <div>
              <label class="form-label">Stand</label>
              <select class="form-input" [ngModel]="cibleId()" (ngModelChange)="cibleId.set($event); refreshPreview()">
                @for (s of stands(); track s.id) {
                  <option [value]="s.id">Stand {{ s.numero }} · {{ s.standTypeNom }}</option>
                }
              </select>
            </div>
          }
          <div>
            <label class="form-label">Personnes</label>
            <select class="form-input" [ngModel]="source()" (ngModelChange)="source.set($event); refreshPreview()">
              @for (s of sources(); track s.value) { <option [value]="s.value">{{ s.label }}</option> }
            </select>
          </div>

          <div class="md:col-span-2">
            <label class="form-label">Période</label>
            <div class="flex flex-wrap gap-2">
              <button type="button" class="chip" [class.on]="!du() && !au()" (click)="setPeriod(null, null)">
                Toute la période
              </button>
              @for (d of days(); track d; let i = $index) {
                <button type="button" class="chip" [class.on]="isDayPeriod(d)" (click)="setDayPeriod(d)">
                  Jour {{ i + 1 }} · {{ dayLabel(d) }}
                </button>
              }
            </div>
            <div class="mt-2 flex flex-wrap items-center gap-2 text-sm">
              <span class="text-slate-500">ou intervalle précis : du</span>
              <input type="datetime-local" class="form-input w-auto" [ngModel]="du()"
                     (ngModelChange)="setPeriod($event, au())" />
              <span class="text-slate-500">au</span>
              <input type="datetime-local" class="form-input w-auto" [ngModel]="au()"
                     (ngModelChange)="setPeriod(du(), $event)" />
            </div>
          </div>

          <div class="md:col-span-2">
            <label class="form-label">Doublons</label>
            <div class="flex flex-wrap gap-4 text-sm">
              <label class="flex items-center gap-2">
                <input type="radio" name="doublons" [checked]="doublons()" (change)="setDoublons(true)" />
                Garder les doublons (une ligne par {{ rowUnit() }})
              </label>
              <label class="flex items-center gap-2">
                <input type="radio" name="doublons" [checked]="!doublons()" (change)="setDoublons(false)" />
                Supprimer les doublons (une ligne par personne)
              </label>
            </div>
            <p class="mt-1 text-xs text-slate-400">
              Une même personne est reconnue à son téléphone, sinon à son e-mail, sinon à son nom.
            </p>
          </div>
        </div>

        @if (preview(); as p) {
          <div class="card mt-4 p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 class="font-semibold text-slate-800">{{ p.titre }}</h3>
                <p class="text-xs text-slate-500">{{ p.filtres }}</p>
                <p class="mt-2 text-sm">
                  <b>{{ p.total }}</b> ligne{{ p.total > 1 ? 's' : '' }} ·
                  <b>{{ p.uniques }}</b> personne{{ p.uniques > 1 ? 's' : '' }} distincte{{ p.uniques > 1 ? 's' : '' }}
                  @if (p.doublons) {
                    · <span class="text-amber-700"><b>{{ p.doublons }}</b> doublon{{ p.doublons > 1 ? 's' : '' }}</span>
                  } @else {
                    · <span class="text-green-700">aucun doublon</span>
                  }
                </p>
              </div>
              <div class="flex flex-wrap gap-2">
                <button type="button" class="btn-primary" [disabled]="exporting() || !shownCount()" (click)="download('XLSX')">Excel</button>
                <button type="button" class="btn-primary" [disabled]="exporting() || !shownCount()" (click)="download('DOCX')">Word</button>
                <button type="button" class="btn-primary" [disabled]="exporting() || !shownCount()" (click)="download('PDF')">PDF</button>
                <button type="button" class="btn-primary" [disabled]="exporting() || !shownCount()" (click)="download('CSV')">CSV</button>
              </div>
            </div>

            <div class="mt-3 overflow-x-auto">
              <table class="w-full text-sm">
                <thead class="text-left text-xs uppercase text-slate-400">
                  <tr><th class="py-1 pr-3">#</th><th class="pr-3">Nom</th><th class="pr-3">Prénom</th>
                    <th class="pr-3">Téléphone</th><th class="pr-3">E-mail</th><th class="pr-3">Référence</th>
                    <th class="pr-3">Catégorie</th><th class="pr-3">Statut</th><th class="pr-3">Date</th>
                    @if (!doublons()) { <th>Occur.</th> }</tr>
                </thead>
                <tbody>
                  @for (l of p.lignes; track $index; let i = $index) {
                    <tr class="border-t border-slate-100">
                      <td class="py-1.5 pr-3 text-slate-400">{{ i + 1 }}</td>
                      <td class="pr-3">{{ l.nom || '—' }}</td>
                      <td class="pr-3">{{ l.prenom }}</td>
                      <td class="pr-3">{{ l.telephone }}</td>
                      <td class="pr-3">{{ l.email }}</td>
                      <td class="pr-3 font-mono text-xs">{{ l.reference }}</td>
                      <td class="pr-3">{{ l.categorie }}</td>
                      <td class="pr-3">{{ l.detail }}</td>
                      <td class="pr-3 text-slate-500">{{ dt(l.date) }}</td>
                      @if (!doublons()) {
                        <td [class.font-semibold]="l.occurrences > 1" [class.text-amber-700]="l.occurrences > 1">
                          {{ l.occurrences }}
                        </td>
                      }
                    </tr>
                  } @empty {
                    <tr><td colspan="10" class="py-4 text-center text-slate-400">Aucun participant pour ces critères.</td></tr>
                  }
                </tbody>
              </table>
              @if (shownCount() > p.lignes.length) {
                <p class="mt-2 text-xs text-slate-400">
                  Aperçu des {{ p.lignes.length }} premières lignes — le fichier exporté contient les {{ shownCount() }}.
                </p>
              }
            </div>
          </div>
        }
      }

      @if (error()) { <p class="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p> }
    }
  `,
  styles: `
    .chip {
      border: 1px solid #e2e8f0; border-radius: 9999px; padding: 0.3rem 0.8rem;
      font-size: 0.8rem; color: #475569; background: #fff;
    }
    .chip:hover { border-color: #94a3b8; }
    .chip.on { background: #1e3a8a; border-color: #1e3a8a; color: #fff; }
    .legend { display: inline-block; width: 0.75rem; height: 0.75rem; border-radius: 2px; }
  `,
})
export class ReportsComponent {
  private reports = inject(ReportsService);
  private eventsService = inject(EventsService);
  private standsService = inject(StandsService);
  private auth = inject(AuthService);

  readonly W = 720;
  readonly H = 220;

  tabs: { id: Tab; label: string }[] = [
    { id: 'stats', label: 'Statistiques' },
    { id: 'flux', label: 'Flux capteurs' },
    { id: 'export', label: 'Export des participants' },
  ];
  kpis: { key: keyof ReportMetrics; label: string }[] = [
    { key: 'billetsEmis', label: 'Billets émis' },
    { key: 'inscriptions', label: 'Inscriptions' },
    { key: 'entrees', label: 'Entrées contrôlées (QR)' },
    { key: 'visiteursUniques', label: 'Visiteurs uniques (QR)' },
    { key: 'capteurEntrees', label: 'Entrées capteurs' },
    { key: 'capteurSorties', label: 'Sorties capteurs' },
    { key: 'visitesStands', label: 'Visites de stands' },
    { key: 'revenus', label: 'Revenus' },
  ];

  isAdmin = computed(() => this.auth.hasPermission('EVENT_VALIDATE'));
  search = '';
  events = signal<EventSummary[]>([]);
  eventId = signal('');
  tab = signal<Tab>('stats');
  error = signal<string | null>(null);

  // stats
  report = signal<EventReport | null>(null);
  statsDay = signal<string | null>(null);
  days = computed(() => this.report()?.jours ?? []);

  // flux
  flux = signal<FluxReport | null>(null);
  granularite = signal<Granularite>('JOUR');
  fluxDay = signal<string>('');
  capteurId = signal('');

  // export
  activities = signal<Activity[]>([]);
  stands = signal<Stand[]>([]);
  perimetre = signal<Perimetre>('EVENEMENT');
  cibleId = signal<string>('');
  source = signal<Source>('BILLETS');
  du = signal<string>('');
  au = signal<string>('');
  doublons = signal(true);
  preview = signal<ParticipantPreview | null>(null);
  exporting = signal(false);

  // --- Comparaison entre éditions (STAT-06) ---
  compareId = signal('');
  compare = signal<EventReport | null>(null);
  compareRows = [
    { key: 'inscriptions', label: 'Inscriptions' }, { key: 'billetsEmis', label: 'Billets émis' },
    { key: 'visiteursUniques', label: 'Présents (personnes uniques)' }, { key: 'entrees', label: 'Entrées' },
    { key: 'visitesStands', label: 'Visites de stands' }, { key: 'revenus', label: 'Recettes (FCFA)' },
  ];
  compareWith(id: string): void {
    this.compareId.set(id);
    this.compare.set(null);
    if (id) this.reports.stats(id, null).subscribe({ next: (c) => this.compare.set(c), error: () => {} });
  }
  delta = (a: number, b: number) => (a ?? 0) - (b ?? 0);
  deltaLabel(a: number, b: number): string {
    if (!b) return '—';
    const pct = Math.round(((a - b) / b) * 100);
    return (pct > 0 ? '+' : '') + pct + ' %';
  }

  // « Par activité » table: sortable on every column.
  activityColumns: { key: keyof ActivityMetrics; label: string }[] = [
    { key: 'titre', label: 'Activité' }, { key: 'dateDebut', label: 'Début' }, { key: 'salle', label: 'Salle' },
    { key: 'inscrits', label: 'Inscrits' }, { key: 'inscritsSurPlace', label: 'Sur place' },
    { key: 'visiteursUniques', label: 'Présents uniques' }, { key: 'tauxPresence', label: 'Taux présence' },
    { key: 'capacite', label: 'Capacité' }, { key: 'tauxRemplissage', label: 'Remplissage' },
    { key: 'entrees', label: 'Entrées' }, { key: 'sorties', label: 'Sorties' },
    { key: 'autoEnregistres', label: 'Auto-enreg.' }, { key: 'dureeMoyenneMinutes', label: 'Durée moy.' },
  ];
  sortKey = signal<keyof ActivityMetrics>('dateDebut');
  sortAsc = signal(true);
  sortedActivities = computed(() => {
    const k = this.sortKey();
    const dir = this.sortAsc() ? 1 : -1;
    return [...(this.report()?.parActivite ?? [])].sort((a, b) => {
      const x = a[k] ?? null;
      const y = b[k] ?? null;
      if (x === y) return 0;
      if (x === null) return 1;
      if (y === null) return -1;
      return (typeof x === 'number' && typeof y === 'number'
        ? x - y : String(x).localeCompare(String(y), 'fr')) * dir;
    });
  });
  sortBy(k: keyof ActivityMetrics): void {
    if (this.sortKey() === k) this.sortAsc.set(!this.sortAsc());
    else {
      this.sortKey.set(k);
      this.sortAsc.set(typeof (this.report()?.parActivite[0]?.[k]) !== 'number');
    }
  }
  pct = (v?: number | null) => (v == null ? '—' : `${v.toLocaleString('fr-FR')} %`);

  /** One-click closing report for the authorities (STAT-04). */
  downloadBilan(): void {
    this.exporting.set(true);
    this.reports.bilan(this.eventId()).subscribe({
      next: (res) => {
        this.exporting.set(false);
        this.saveBlob(res, 'bilan-evenement.pdf');
      },
      error: () => {
        this.exporting.set(false);
        this.error.set("Le rapport n'a pas pu être généré.");
      },
    });
  }

  downloadActivities(format: 'CSV' | 'XLSX'): void {
    this.exporting.set(true);
    this.reports.exportActivities(this.eventId(), this.statsDay(), format).subscribe({
      next: (res) => {
        this.exporting.set(false);
        this.saveBlob(res, `statistiques-activites.${format.toLowerCase()}`);
      },
      error: () => {
        this.exporting.set(false);
        this.error.set("L'export a échoué.");
      },
    });
  }

  private saveBlob(res: HttpResponse<Blob>, fallback: string): void {
    const name = /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ?? fallback;
    const url = URL.createObjectURL(res.body!);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  sources = computed(() => SOURCES[this.perimetre()]);
  shownCount = computed(() => {
    const p = this.preview();
    return !p ? 0 : this.doublons() ? p.total : p.uniques;
  });
  rowUnit = computed(() =>
    this.source() === 'PRESENTS' ? 'passage' : this.source() === 'BILLETS' ? 'billet'
      : this.source() === 'VISITES' ? 'visite' : 'inscription');

  private previewSub?: Subscription;

  constructor() {
    this.loadEvents();
  }

  loadEvents(): void {
    const list$ = this.isAdmin()
      ? this.eventsService.adminList({ search: this.search })
      : this.eventsService.mine();
    list$.subscribe({ next: (p) => this.events.set(p.content), error: () => this.events.set([]) });
  }

  selectEvent(id: string): void {
    this.eventId.set(id);
    this.report.set(null);
    this.flux.set(null);
    this.preview.set(null);
    this.error.set(null);
    this.statsDay.set(null);
    this.fluxDay.set('');
    this.capteurId.set('');
    this.perimetre.set('EVENEMENT');
    this.cibleId.set('');
    this.source.set('BILLETS');
    this.du.set('');
    this.au.set('');
    if (!id) return;
    this.loadStats(null);
    this.eventsService.activities(id).subscribe({ next: (a) => this.activities.set(a), error: () => {} });
    this.standsService.standsForEvent(id).subscribe({ next: (s) => this.stands.set(s), error: () => this.stands.set([]) });
    if (this.tab() === 'flux') this.loadFlux();
    if (this.tab() === 'export') this.refreshPreview();
  }

  setTab(t: Tab): void {
    this.tab.set(t);
    this.error.set(null);
    if (t === 'flux' && !this.flux()) this.loadFlux();
    if (t === 'export' && !this.preview()) this.refreshPreview();
  }

  // ---------------------------------------------------------------- stats

  loadStats(jour: string | null): void {
    this.statsDay.set(jour);
    this.reports.stats(this.eventId(), jour).subscribe({
      next: (r) => this.report.set(r),
      error: (e) => this.fail(e),
    });
  }

  kpi(m: ReportMetrics, key: keyof ReportMetrics): string {
    if (key === 'inscriptions') return `${m.inscriptions} (${m.inscriptionsConfirmees} conf.)`;
    return key === 'revenus' ? formatFcfa(m.revenus) : String(m[key]);
  }

  // ----------------------------------------------------------------- flux

  setGranularite(g: Granularite): void {
    this.granularite.set(g);
    if (g === 'HEURE' && !this.fluxDay()) {
      this.fluxDay.set(this.defaultDay());
    }
    this.loadFlux();
  }

  setFluxDay(d: string): void {
    this.fluxDay.set(d);
    this.loadFlux();
  }

  setCapteur(id: string): void {
    this.capteurId.set(id);
    this.loadFlux();
  }

  loadFlux(): void {
    const g = this.granularite();
    this.reports.flux(this.eventId(), g, g === 'HEURE' ? this.fluxDay() || null : null, this.capteurId() || null)
      .subscribe({
        next: (f) => {
          this.flux.set(f);
          if (f.jour && !this.fluxDay()) this.fluxDay.set(f.jour);
        },
        error: (e) => this.fail(e),
      });
  }

  private defaultDay(): string {
    const days = this.days();
    const today = new Date();
    const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    if (!days.length || days.includes(iso)) return iso;
    return iso > days[days.length - 1] ? days[days.length - 1] : days[0];
  }

  lastPresent = computed(() => {
    const pts = this.flux()?.points ?? [];
    return pts.length ? pts[pts.length - 1].presents : 0;
  });
  private maxValue = computed(() =>
    Math.max(1, ...(this.flux()?.points ?? []).flatMap((p) => [p.entrees, p.sorties, p.presents])));
  slotW = computed(() => (this.W - 40) / Math.max(1, this.flux()?.points.length ?? 1));
  barW = computed(() => Math.max(2, this.slotW() / 2 - 2));
  slotX = (i: number) => 38 + i * this.slotW();
  y = (v: number) => 8 + (this.H - 30) * (1 - v / this.maxValue());
  grid = computed(() => {
    const max = this.maxValue();
    return [0, 0.25, 0.5, 0.75, 1].map((f) => ({ v: Math.round(max * f), y: this.y(max * f) }));
  });
  presentLine = computed(() =>
    (this.flux()?.points ?? [])
      .map((p, i) => `${this.slotX(i) + this.slotW() / 2},${this.y(p.presents)}`).join(' '));
  showTick = (i: number) => {
    const n = this.flux()?.points.length ?? 0;
    return n <= 16 || i % Math.ceil(n / 12) === 0;
  };
  tick = (p: FluxPoint) => (this.granularite() === 'HEURE' ? p.periode : this.dayLabel(p.periode));
  pointLabel = (p: FluxPoint) =>
    this.granularite() === 'HEURE' ? `${p.periode} – ${String((+p.periode.slice(0, 2) + 1) % 24).padStart(2, '0')}h`
      : this.dayLabel(p.periode);

  // --------------------------------------------------------------- export

  setPerimetre(p: Perimetre): void {
    this.perimetre.set(p);
    this.source.set(SOURCES[p][0].value);
    this.cibleId.set(p === 'ACTIVITE' ? this.activities()[0]?.id ?? '' : p === 'STAND' ? this.stands()[0]?.id ?? '' : '');
    this.refreshPreview();
  }

  setPeriod(du: string | null, au: string | null): void {
    this.du.set(du ?? '');
    this.au.set(au ?? '');
    this.refreshPreview();
  }

  setDayPeriod(day: string): void {
    this.setPeriod(`${day}T00:00`, `${this.nextDay(day)}T00:00`);
  }

  isDayPeriod(day: string): boolean {
    return this.du() === `${day}T00:00` && this.au() === `${this.nextDay(day)}T00:00`;
  }

  setDoublons(v: boolean): void {
    this.doublons.set(v);
    this.refreshPreview();
  }

  private criteria(): ParticipantCriteria {
    return {
      perimetre: this.perimetre(),
      cibleId: this.perimetre() === 'EVENEMENT' ? null : this.cibleId() || null,
      source: this.source(),
      du: this.du() || null,
      au: this.au() || null,
      doublons: this.doublons(),
    };
  }

  refreshPreview(): void {
    if (!this.eventId()) return;
    if (this.perimetre() !== 'EVENEMENT' && !this.cibleId()) {
      this.preview.set(null);
      return;
    }
    this.error.set(null);
    this.previewSub?.unsubscribe();
    this.previewSub = this.reports.participants(this.eventId(), this.criteria()).subscribe({
      next: (p) => this.preview.set(p),
      error: (e) => this.fail(e),
    });
  }

  download(format: ExportFormat): void {
    this.exporting.set(true);
    this.reports.exportParticipants(this.eventId(), this.criteria(), format).subscribe({
      next: (res) => {
        this.exporting.set(false);
        const name = /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') ?? '')?.[1]
          ?? `participants.${format.toLowerCase()}`;
        const url = URL.createObjectURL(res.body!);
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => {
        this.exporting.set(false);
        this.error.set("L'export a échoué.");
      },
    });
  }

  // -------------------------------------------------------------- helpers

  dayLabel(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return DAY_FMT.format(new Date(y, m - 1, d));
  }

  private nextDay(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    const n = new Date(y, m - 1, d + 1);
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
  }

  private fail(e: HttpErrorResponse): void {
    this.error.set((e.error as ApiError)?.message ?? 'Chargement impossible.');
  }

  fcfa = (n: number) => formatFcfa(n);
  dt = (iso?: string) => formatDateTime(iso);
  time = (iso?: string) => formatTime(iso);
}
