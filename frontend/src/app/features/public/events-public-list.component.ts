import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EventsService } from '../events/events.service';
import { EventCategory, EventStatus, EventSummary } from '../events/event.models';
import { formatTime } from '../../shared/format';
import { IconComponent, IconName } from '../../shared/icon.component';
import { EventCoverComponent } from '../../shared/event-cover.component';

const ZONE = 'Africa/Ouagadougou';
type Onglet = 'avenir' | 'passes';

/**
 * Accueil public : bandeau de recherche, événement à la une, catégories,
 * catalogue (à venir / passés), fonctionnement, espace organisateurs, FAQ.
 * Conforme à la Charte graphique de l'administration burkinabè v2.0.
 */
@Component({
  selector: 'app-events-public-list',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent, EventCoverComponent],
  template: `
    <!-- ============ BANDEAU ============ -->
    <section class="pleine-largeur relative -mt-8 overflow-hidden bg-neutre-900 text-white sm:-mt-10">
      <div class="mx-auto grid max-w-conteneur items-center gap-12 px-4 pb-16 pt-12 sm:px-6 lg:grid-cols-[1.15fr_1fr] lg:pb-20 lg:pt-16">
        <div>
          <h1 class="text-[clamp(2.25rem,1.6rem+2.9vw,3.75rem)] text-white">
            Les grands événements du Burkina Faso,
            <span class="text-or-300">au même endroit.</span>
          </h1>
          <p class="mt-5 max-w-xl text-[1.0625rem] leading-[1.75] text-neutre-300">
            Salons, foires, festivals, forums et conférences : inscrivez-vous, payez en ligne
            et recevez votre billet électronique en quelques minutes.
          </p>

          <!-- Recherche -->
          <form class="mt-8 rounded-xl bg-white p-2 shadow-xl" role="search" aria-label="Rechercher un événement"
                (submit)="$event.preventDefault(); rechercher()">
            <div class="flex flex-col gap-2 md:flex-row md:items-stretch">
              <label class="flex flex-1 items-center gap-3 rounded-md px-3 text-neutre-900 focus-within:bg-neutre-50 md:border-r md:border-neutre-200">
                <app-icon name="search" class="h-5 w-5 shrink-0 text-neutre-500" aria-hidden="true" />
                <span class="sr-only">Événement ou mot-clé</span>
                <input class="min-h-[48px] w-full bg-transparent text-[0.9375rem] placeholder:text-neutre-550 focus:outline-none"
                       placeholder="Événement, mot-clé…" name="search" [(ngModel)]="search" />
              </label>
              <label class="flex items-center gap-3 rounded-md px-3 text-neutre-900 focus-within:bg-neutre-50 md:w-44">
                <app-icon name="pin" class="h-5 w-5 shrink-0 text-neutre-500" aria-hidden="true" />
                <span class="sr-only">Ville</span>
                <input class="min-h-[48px] w-full bg-transparent text-[0.9375rem] placeholder:text-neutre-550 focus:outline-none"
                       placeholder="Ville" name="ville" [(ngModel)]="ville" />
              </label>
              <button type="submit" class="btn-primary min-h-[48px] px-6">Rechercher</button>
            </div>
          </form>

          @if (villes().length) {
            <p class="mt-4 flex flex-wrap items-center gap-2 text-sm text-neutre-400">
              <span>Villes :</span>
              @for (v of villes().slice(0, 4); track v) {
                <button type="button" class="rounded-full border border-white/20 px-3 py-1 text-neutre-200 transition-colors hover:border-white/60 hover:text-white"
                        (click)="ville = v; rechercher()">{{ v }}</button>
              }
            </p>
          }

          <dl class="mt-10 grid max-w-lg grid-cols-3 gap-6 border-t border-white/10 pt-6">
            <div>
              <dt class="text-xs text-neutre-400">Événements à venir</dt>
              <dd class="mt-1 font-display text-3xl font-extrabold tabular-nums text-white">{{ aVenirTous().length }}</dd>
            </div>
            <div>
              <dt class="text-xs text-neutre-400">Villes</dt>
              <dd class="mt-1 font-display text-3xl font-extrabold tabular-nums text-white">{{ villes().length }}</dd>
            </div>
            <div>
              <dt class="text-xs text-neutre-400">Catégories</dt>
              <dd class="mt-1 font-display text-3xl font-extrabold tabular-nums text-white">{{ categories().length }}</dd>
            </div>
          </dl>
        </div>

        <!-- Mosaïque d'affiches -->
        @if (mosaique().length >= 3) {
          <div class="hidden grid-cols-2 gap-4 lg:grid" aria-hidden="true">
            @for (e of mosaique(); track e.id; let i = $index) {
              <a [routerLink]="['/evenements', e.slug]" tabindex="-1"
                 class="group relative block aspect-[16/9] overflow-hidden rounded-xl shadow-xl ring-1 ring-white/10 transition-transform duration-300 ease-sortie hover:-translate-y-1"
                 [class.col-span-2]="i === 0">
                <app-event-cover class="h-full w-full" [nom]="e.nom" [coverUrl]="e.coverUrl" />
                <span class="absolute left-3 top-3 rounded-md bg-white/95 px-2.5 py-1 font-display text-[0.6875rem] font-bold uppercase tracking-wider text-neutre-900 shadow-sm">
                  {{ dateCourte(e) }}
                </span>
              </a>
            }
          </div>
        }
      </div>
      <div class="fs-tissage" aria-hidden="true"></div>
    </section>

    <!-- ============ À LA UNE ============ -->
    @if (aLaUne(); as e) {
      <section class="mt-14" aria-labelledby="titre-une">
        <p class="surtitre text-vert-700">À la une</p>
        <h2 id="titre-une" class="sr-only">Événement à la une</h2>
        <article class="group relative mt-3 grid overflow-hidden rounded-2xl border border-neutre-200 bg-white shadow-sm transition-shadow hover:shadow-lg md:grid-cols-[1.25fr_1fr]">
          <div class="relative flex items-center bg-neutre-900">
            <app-event-cover class="aspect-[16/9] w-full" [nom]="e.nom" [coverUrl]="e.coverUrl" [contain]="true" />
          </div>
          <div class="flex flex-col p-6 sm:p-8 lg:p-10">
            <div class="flex flex-wrap items-center gap-2">
              @if (statut(e.statut); as s) {
                <span class="badge gap-1.5" [class]="s.cls">
                  @if (e.statut === 'EN_COURS') { <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-current"></span> }
                  {{ s.label }}
                </span>
              }
              @if (e.categoryNom) { <span class="badge bg-neutre-100 text-neutre-700">{{ e.categoryNom }}</span> }
            </div>
            <h3 class="mt-4 text-[clamp(1.625rem,1.36rem+1.17vw,2.375rem)] leading-tight">
              <a [routerLink]="['/evenements', e.slug]" class="text-neutre-900 after:absolute after:inset-0 hover:text-vert-700">{{ e.nom }}</a>
            </h3>
            @if (e.descriptionCourte) {
              <p class="mt-3 line-clamp-3 text-neutre-700">{{ e.descriptionCourte }}</p>
            }
            <ul class="mt-6 space-y-2.5 text-sm text-neutre-800">
              <li class="flex items-center gap-3">
                <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-vert-50 text-vert-700"><app-icon name="calendar" class="h-[18px] w-[18px]" aria-hidden="true" /></span>
                <span>{{ dateLongue(e) }}</span>
              </li>
              <li class="flex items-center gap-3">
                <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-vert-50 text-vert-700"><app-icon name="pin" class="h-[18px] w-[18px]" aria-hidden="true" /></span>
                <span>{{ place(e) }}</span>
              </li>
              <li class="flex items-center gap-3">
                <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-vert-50 text-vert-700"><app-icon name="building" class="h-[18px] w-[18px]" aria-hidden="true" /></span>
                <span>Organisé par {{ e.organizerNom }}</span>
              </li>
            </ul>
            <div class="mt-auto flex flex-wrap items-center justify-between gap-4 pt-8">
              @if (compteur(e); as c) {
                <p class="font-display">
                  <span class="block text-xs font-semibold uppercase tracking-wider text-neutre-600">{{ c.label }}</span>
                  <span class="text-3xl font-extrabold tabular-nums text-neutre-900">{{ c.valeur }}</span>
                </p>
              }
              <!-- Vrai lien (au-dessus du lien de la carte) : ouvre la page directement sur la billetterie -->
              <a [routerLink]="['/evenements', e.slug]" [queryParams]="{ participer: 1 }"
                 class="btn-primary relative z-10 px-6">
                Réserver ma place <app-icon name="arrow-right" class="h-4 w-4" aria-hidden="true" />
              </a>
            </div>
          </div>
        </article>
      </section>
    }

    <!-- ============ CATÉGORIES (grand écran ; sur téléphone : liste déroulante dans le catalogue) ============ -->
    @if (categories().length) {
      <section class="mt-16 hidden md:block" aria-labelledby="titre-categories">
        <div class="flex items-end justify-between gap-4">
          <div>
            <p class="surtitre text-vert-700">Explorer</p>
            <h2 id="titre-categories" class="mt-2 text-[clamp(1.625rem,1.36rem+1.17vw,2.375rem)] text-neutre-900">Parcourir par catégorie</h2>
          </div>
        </div>
        <ul class="mt-6 grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          @for (c of categories(); track c.id) {
            <li>
              <button type="button" (click)="choisirCategorie(c.slug)"
                      class="group flex h-full w-full items-center gap-3 rounded-xl border bg-white p-4 text-left transition-all duration-150 hover:-translate-y-0.5 hover:border-vert-600 hover:shadow-md"
                      [class.border-vert-600]="categorie === c.slug" [class.ring-1]="categorie === c.slug" [class.ring-vert-600]="categorie === c.slug"
                      [class.border-neutre-200]="categorie !== c.slug" [attr.aria-pressed]="categorie === c.slug">
                <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-neutre-50 text-vert-700 transition-colors group-hover:bg-vert-50">
                  <app-icon [name]="iconeCategorie(c)" class="h-[22px] w-[22px]" aria-hidden="true" />
                </span>
                <span class="min-w-0">
                  <span class="block font-display text-[0.9375rem] font-semibold leading-snug text-neutre-900">{{ c.nom }}</span>
                  <span class="block text-xs text-neutre-600">{{ compteCategorie(c.nom) }}</span>
                </span>
              </button>
            </li>
          }
        </ul>
      </section>
    }

    <!-- ============ CATALOGUE ============ -->
    <section class="mt-12 scroll-mt-6 md:mt-16" id="catalogue" aria-labelledby="titre-catalogue">
      <div class="flex flex-col gap-4 border-b border-neutre-200 pb-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p class="surtitre text-vert-700">Catalogue</p>
          <h2 id="titre-catalogue" class="mt-2 text-[clamp(1.625rem,1.36rem+1.17vw,2.375rem)] text-neutre-900">
            {{ onglet() === 'avenir' ? 'Événements à venir' : 'Événements passés' }}
          </h2>
        </div>
        <div class="inline-flex self-start rounded-md bg-neutre-100 p-1 sm:self-auto" role="tablist" aria-label="Période">
          <button type="button" role="tab" [attr.aria-selected]="onglet() === 'avenir'" (click)="onglet.set('avenir')"
                  class="min-h-[40px] rounded px-4 font-display text-sm font-semibold transition-colors"
                  [class]="onglet() === 'avenir' ? 'bg-white text-neutre-900 shadow-xs' : 'text-neutre-600 hover:text-neutre-900'">
            À venir <span class="ml-1 tabular-nums text-neutre-500">{{ aVenir().length }}</span>
          </button>
          <button type="button" role="tab" [attr.aria-selected]="onglet() === 'passes'" (click)="onglet.set('passes')"
                  class="min-h-[40px] rounded px-4 font-display text-sm font-semibold transition-colors"
                  [class]="onglet() === 'passes' ? 'bg-white text-neutre-900 shadow-xs' : 'text-neutre-600 hover:text-neutre-900'">
            Passés <span class="ml-1 tabular-nums text-neutre-500">{{ passes().length }}</span>
          </button>
        </div>
      </div>

      <!-- Téléphone : choix de la catégorie par liste déroulante (évite de faire défiler toutes les tuiles) -->
      @if (categories().length) {
        <div class="mt-4 md:hidden">
          <label for="categorie-mobile" class="form-label">Catégorie</label>
          <div class="relative">
            <select id="categorie-mobile" class="form-input appearance-none pr-10" [ngModel]="categorie" (ngModelChange)="definirCategorie($event)">
              <option value="">Toutes les catégories</option>
              @for (c of categories(); track c.id) {
                <option [value]="c.slug">{{ c.nom }} — {{ compteCategorie(c.nom) }}</option>
              }
            </select>
            <app-icon name="chevron-down" class="pointer-events-none absolute right-3 top-1/2 h-5 w-5 -translate-y-1/2 text-neutre-500" aria-hidden="true" />
          </div>
        </div>
      }

      @if (filtresActifs()) {
        <div class="mt-4 flex flex-wrap items-center gap-2 text-sm" aria-live="polite">
          <span class="text-neutre-600">Filtres :</span>
          @if (search) { <span class="badge bg-vert-50 py-1 text-vert-800">« {{ search }} »</span> }
          @if (ville) { <span class="badge bg-vert-50 py-1 text-vert-800">{{ ville }}</span> }
          @if (categorie) { <span class="badge bg-vert-50 py-1 text-vert-800">{{ nomCategorie() }}</span> }
          <button type="button" class="ml-1 font-medium text-vert-700 underline underline-offset-2 hover:text-vert-800" (click)="effacer()">
            Tout effacer
          </button>
        </div>
      }

      <div class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        @if (chargement()) {
          @for (i of [1, 2, 3]; track i) {
            <div class="overflow-hidden rounded-xl border border-neutre-200 bg-white">
              <div class="aspect-[16/9] animate-pulse bg-neutre-100"></div>
              <div class="space-y-3 p-5">
                <div class="h-3 w-1/3 animate-pulse rounded bg-neutre-100"></div>
                <div class="h-5 w-3/4 animate-pulse rounded bg-neutre-100"></div>
                <div class="h-3 w-1/2 animate-pulse rounded bg-neutre-100"></div>
              </div>
            </div>
          }
        } @else {
          @for (e of liste(); track e.id) {
            <article class="group relative flex flex-col overflow-hidden rounded-xl border border-neutre-200 bg-white shadow-xs transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg">
              <div class="relative aspect-[16/9] overflow-hidden bg-neutre-100">
                <app-event-cover class="h-full w-full transition-transform duration-500 ease-sortie group-hover:scale-[1.03]" [nom]="e.nom" [coverUrl]="e.coverUrl" />
                <span class="absolute left-3 top-3 flex w-14 flex-col items-center rounded-md bg-white py-1.5 leading-none shadow-sm">
                  <span class="font-display text-[0.6875rem] font-bold uppercase tracking-wider text-vert-700">{{ month(e) }}</span>
                  <span class="mt-1 font-display text-2xl font-extrabold tabular-nums text-neutre-900">{{ day(e) }}</span>
                </span>
                @if (statut(e.statut); as s) {
                  <span class="badge absolute right-3 top-3 gap-1.5 shadow-sm" [class]="s.cls">
                    @if (e.statut === 'EN_COURS') { <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-current"></span> }
                    {{ s.label }}
                  </span>
                }
              </div>

              <div class="flex flex-1 flex-col p-5">
                <p class="font-display text-xs font-bold uppercase tracking-wider text-vert-700">
                  {{ dateCourte(e) }}@if (e.categoryNom) {<span class="text-neutre-500"> · {{ e.categoryNom }}</span>}
                </p>
                <h3 class="mt-2 text-lg leading-snug">
                  <a [routerLink]="['/evenements', e.slug]" class="text-neutre-900 after:absolute after:inset-0 group-hover:text-vert-700">{{ e.nom }}</a>
                </h3>
                @if (e.descriptionCourte) {
                  <p class="mt-2 line-clamp-2 text-sm text-neutre-600">{{ e.descriptionCourte }}</p>
                }
                <div class="mt-auto flex items-center justify-between gap-3 border-t border-neutre-100 pt-4 text-sm">
                  <span class="flex min-w-0 items-center gap-1.5 text-neutre-700">
                    <app-icon name="pin" class="h-4 w-4 shrink-0 text-neutre-500" aria-hidden="true" />
                    <span class="truncate">{{ e.ville || e.lieu || '—' }}</span>
                  </span>
                  <span class="flex shrink-0 items-center gap-1 font-display font-semibold text-vert-700">
                    {{ onglet() === 'avenir' ? 'Réserver' : 'Voir' }}
                    <app-icon name="arrow-right" class="h-4 w-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                  </span>
                </div>
              </div>
            </article>
          } @empty {
            <div class="col-span-full rounded-xl border border-dashed border-neutre-300 bg-white px-6 py-16 text-center">
              <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-neutre-50 text-neutre-500">
                <app-icon name="calendar" class="h-7 w-7" aria-hidden="true" />
              </span>
              <p class="mt-4 font-display text-lg font-semibold text-neutre-900">Aucun événement trouvé</p>
              <p class="mx-auto mt-1 max-w-sm text-sm text-neutre-600">
                @if (filtresActifs()) { Essayez d'autres mots-clés ou effacez les filtres pour voir tout le catalogue. }
                @else if (onglet() === 'avenir') { Aucun événement à venir n'est publié pour le moment. Revenez bientôt. }
                @else { Aucun événement passé à afficher. }
              </p>
              @if (filtresActifs()) {
                <button type="button" class="btn-secondary mt-5" (click)="effacer()">Effacer les filtres</button>
              }
            </div>
          }
        }
      </div>
    </section>

    <!-- ============ COMMENT ÇA MARCHE ============ -->
    <section class="mt-20" aria-labelledby="titre-etapes">
      <div class="text-center">
        <p class="surtitre text-vert-700">Simple et rapide</p>
        <h2 id="titre-etapes" class="mt-2 text-[clamp(1.625rem,1.36rem+1.17vw,2.375rem)] text-neutre-900">Votre billet en trois étapes</h2>
      </div>
      <ol class="mt-10 grid gap-8 md:grid-cols-3">
        @for (s of etapes; track s.titre; let i = $index) {
          <li class="relative text-center">
            @if (i < etapes.length - 1) {
              <span class="absolute left-[calc(50%+2.5rem)] right-[calc(-50%+2.5rem)] top-7 hidden border-t-2 border-dashed border-neutre-300 md:block" aria-hidden="true"></span>
            }
            <span class="relative mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-vert-500 text-white shadow-md">
              <app-icon [name]="s.icone" class="h-6 w-6" aria-hidden="true" />
              <span class="absolute -right-1 -top-1 flex h-6 w-6 items-center justify-center rounded-full bg-white font-display text-xs font-bold text-vert-700 ring-2 ring-vert-500">{{ i + 1 }}</span>
            </span>
            <h3 class="mt-5 text-lg text-neutre-900">{{ s.titre }}</h3>
            <p class="mx-auto mt-2 max-w-xs text-sm text-neutre-600">{{ s.texte }}</p>
          </li>
        }
      </ol>
      <ul class="mx-auto mt-12 flex max-w-3xl flex-wrap items-center justify-center gap-x-8 gap-y-3 text-sm text-neutre-700">
        <li class="flex items-center gap-2"><app-icon name="shield" class="h-5 w-5 text-vert-600" aria-hidden="true" /> Paiement sécurisé</li>
        <li class="flex items-center gap-2"><app-icon name="phone-money" class="h-5 w-5 text-vert-600" aria-hidden="true" /> Paiement en ligne FasoArzeka</li>
        <li class="flex items-center gap-2"><app-icon name="qr" class="h-5 w-5 text-vert-600" aria-hidden="true" /> Billet QR infalsifiable</li>
        <li class="flex items-center gap-2"><app-icon name="check" class="h-5 w-5 text-vert-600" aria-hidden="true" /> Sans impression</li>
      </ul>
    </section>

    <!-- ============ ORGANISATEURS ============ -->
    <section class="pleine-largeur mt-20 bg-vert-800 text-white" aria-labelledby="titre-orga">
      <div class="mx-auto grid max-w-conteneur gap-10 px-4 py-14 sm:px-6 lg:grid-cols-[1.1fr_1fr] lg:items-center lg:py-16">
        <div>
          <p class="surtitre text-vert-200">Organisateurs</p>
          <h2 id="titre-orga" class="mt-3 text-[clamp(1.625rem,1.36rem+1.17vw,2.375rem)] text-white">
            Vous organisez un salon, un festival ou un forum ?
          </h2>
          <p class="mt-4 max-w-xl text-vert-100">
            Publiez votre événement sur la plateforme nationale et gérez tout depuis un seul espace :
            de l'inscription au contrôle des entrées.
          </p>
          <div class="mt-8 flex flex-wrap gap-3">
            <a routerLink="/inscription" class="btn-inverse px-6">Créer un compte organisateur</a>
            <a routerLink="/connexion" class="btn-contour-clair px-6">Se connecter</a>
          </div>
        </div>
        <ul class="grid gap-3 sm:grid-cols-2">
          @for (a of atouts; track a.titre) {
            <li class="rounded-xl bg-white/[0.06] p-5 ring-1 ring-white/10">
              <app-icon [name]="a.icone" class="h-6 w-6 text-or-300" aria-hidden="true" />
              <p class="mt-3 font-display font-semibold text-white">{{ a.titre }}</p>
              <p class="mt-1 text-sm text-vert-100">{{ a.texte }}</p>
            </li>
          }
        </ul>
      </div>
    </section>

    <!-- ============ FAQ ============ -->
    <section class="mx-auto mt-20 max-w-texte" aria-labelledby="titre-faq">
      <div class="text-center">
        <p class="surtitre text-vert-700">Aide</p>
        <h2 id="titre-faq" class="mt-2 text-[clamp(1.625rem,1.36rem+1.17vw,2.375rem)] text-neutre-900">Questions fréquentes</h2>
      </div>
      <div class="mt-8 divide-y divide-neutre-200 rounded-xl border border-neutre-200 bg-white">
        @for (q of faq; track q.q) {
          <details class="group">
            <summary class="flex min-h-[56px] cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 font-display font-semibold text-neutre-900 hover:text-vert-700 [&::-webkit-details-marker]:hidden">
              {{ q.q }}
              <app-icon name="chevron-down" class="h-5 w-5 shrink-0 text-neutre-500 transition-transform duration-200 group-open:rotate-180" aria-hidden="true" />
            </summary>
            <p class="px-5 pb-5 text-neutre-700">{{ q.r }}</p>
          </details>
        }
      </div>
      <p class="mt-6 text-center text-sm text-neutre-600">
        Billet perdu ?
        <a routerLink="/retrouver-billet" class="lien">Retrouver mon billet</a>
      </p>
    </section>
  `,
})
export class EventsPublicListComponent {
  private service = inject(EventsService);

  /** Tous les événements publiés (sans filtre) : statistiques, à la une, catégories. */
  private tous = signal<EventSummary[]>([]);
  /** Résultat de la recherche courante. */
  private resultats = signal<EventSummary[]>([]);
  categories = signal<EventCategory[]>([]);
  chargement = signal(true);
  onglet = signal<Onglet>('avenir');
  private filtres = signal(0); // déclenche le recalcul de filtresActifs()

  search = '';
  categorie = '';
  ville = '';

  readonly etapes: { icone: IconName; titre: string; texte: string }[] = [
    { icone: 'search', titre: 'Trouvez votre événement', texte: 'Parcourez le catalogue par catégorie, ville ou mot-clé.' },
    { icone: 'ticket', titre: 'Inscrivez-vous en ligne', texte: 'Un numéro de téléphone suffit. Payez en ligne si l’accès est payant.' },
    { icone: 'qr', titre: 'Présentez votre QR code', texte: 'Votre billet s’affiche sur votre téléphone et se scanne à l’entrée.' },
  ];

  readonly atouts: { icone: IconName; titre: string; texte: string }[] = [
    { icone: 'ticket', titre: 'Billetterie et inscriptions', texte: 'Catégories de billets, quotas et formulaires.' },
    { icone: 'store', titre: 'Réservation de stands', texte: 'Plan des stands, réservation et paiement.' },
    { icone: 'scan', titre: 'Contrôle d’accès QR', texte: 'Scan des billets à l’entrée, même sur mobile.' },
    { icone: 'chart', titre: 'Statistiques en direct', texte: 'Fréquentation, ventes et rapports.' },
  ];

  readonly faq = [
    { q: 'Dois-je créer un compte pour m’inscrire ?', r: 'Non. Un numéro de téléphone suffit pour obtenir votre billet. Vous pourrez créer un compte ensuite pour retrouver vos billets, inscriptions et factures sur tous vos appareils.' },
    { q: 'Comment payer mon billet ?', r: 'Le paiement se fait en ligne, de façon sécurisée, au moment de l’inscription. Votre billet est délivré dès la confirmation du paiement.' },
    { q: 'Dois-je imprimer mon billet ?', r: 'Non. La version numérique affichée sur votre téléphone suffit : le QR code est scanné à l’entrée. Vous pouvez aussi télécharger le billet en PDF.' },
    { q: 'J’ai perdu mon billet, que faire ?', r: 'Utilisez la page « Retrouver mon billet » avec le numéro de téléphone utilisé lors de l’inscription, ou connectez-vous à votre compte.' },
    { q: 'Comment réserver un stand ?', r: 'Ouvrez la page de l’événement et rendez-vous dans la section Stands. La réservation est ouverte aux comptes structure (entreprise, institution) et, selon l’événement, aux particuliers.' },
  ];

  constructor() {
    this.service.categories().subscribe((c) => this.categories.set(c));
    this.service.publicList({ size: 100 }).subscribe((p) => {
      this.tous.set(p.content);
      if (!this.filtresActifs()) {
        this.resultats.set(p.content);
        this.chargement.set(false);
      }
    });
  }

  // ---------- données dérivées ----------
  private estPasse = (e: EventSummary) => new Date(e.dateFin).getTime() < Date.now();
  private trierAsc = (a: EventSummary, b: EventSummary) => +new Date(a.dateDebut) - +new Date(b.dateDebut);

  aVenirTous = computed(() => this.tous().filter((e) => !this.estPasse(e)).sort(this.trierAsc));
  aVenir = computed(() => this.resultats().filter((e) => !this.estPasse(e)).sort(this.trierAsc));
  passes = computed(() => this.resultats().filter((e) => this.estPasse(e)).sort((a, b) => this.trierAsc(b, a)));
  liste = computed(() => (this.onglet() === 'avenir' ? this.aVenir() : this.passes()));

  aLaUne = computed(() => {
    const a = this.aVenirTous();
    return a.find((e) => e.statut === 'INSCRIPTIONS_OUVERTES' || e.statut === 'EN_COURS') ?? a[0] ?? null;
  });
  mosaique = computed(() => {
    const a = this.aVenirTous();
    return (a.length >= 3 ? a : [...a, ...this.tous().filter((e) => this.estPasse(e))]).slice(0, 3);
  });
  villes = computed(() => [...new Set(this.tous().map((e) => e.ville).filter((v): v is string => !!v))]);
  filtresActifs = computed(() => (this.filtres(), !!(this.search || this.ville || this.categorie)));
  nomCategorie = computed(() => (this.filtres(), this.categories().find((c) => c.slug === this.categorie)?.nom ?? ''));

  compteCategorie(nom: string): string {
    const n = this.aVenirTous().filter((e) => e.categoryNom === nom).length;
    return n === 0 ? 'Bientôt' : n === 1 ? '1 événement' : `${n} événements`;
  }

  iconeCategorie(c: EventCategory): IconName {
    const s = c.slug.toLowerCase();
    if (s.includes('salon') || s.includes('foire')) return 'store';
    if (s.includes('festival') || s.includes('concert') || s.includes('musique')) return 'music';
    if (s.includes('cinema') || s.includes('film')) return 'film';
    if (s.includes('forum')) return 'users';
    if (s.includes('conference')) return 'mic';
    if (s.includes('exposition')) return 'present';
    if (s.includes('seminaire') || s.includes('formation')) return 'book';
    if (s.includes('atelier')) return 'briefcase';
    return 'calendar';
  }

  // ---------- actions ----------
  rechercher(): void {
    this.filtres.update((n) => n + 1);
    this.chargement.set(true);
    this.service
      .publicList({ search: this.search || undefined, categorie: this.categorie || undefined, ville: this.ville || undefined, size: 100 })
      .subscribe({
        next: (p) => {
          this.resultats.set(p.content);
          this.chargement.set(false);
          if (this.filtresActifs()) {
            this.onglet.set(this.aVenir().length || !this.passes().length ? 'avenir' : 'passes');
          }
        },
        error: () => this.chargement.set(false),
      });
    if (this.filtresActifs()) {
      setTimeout(() => document.getElementById('catalogue')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
    }
  }

  /** Liste déroulante (téléphone) : sélection directe, « » = toutes. */
  definirCategorie(slug: string): void {
    this.categorie = slug ?? '';
    this.rechercher();
  }

  choisirCategorie(slug: string): void {
    this.categorie = this.categorie === slug ? '' : slug;
    this.rechercher();
  }

  effacer(): void {
    this.search = '';
    this.ville = '';
    this.categorie = '';
    this.rechercher();
  }

  // ---------- présentation ----------
  private part = (iso: string, opts: Intl.DateTimeFormatOptions) =>
    new Intl.DateTimeFormat('fr-FR', { ...opts, timeZone: ZONE }).format(new Date(iso));

  day = (e: EventSummary) => this.part(e.dateDebut, { day: '2-digit' });
  month = (e: EventSummary) => this.part(e.dateDebut, { month: 'short' }).replace('.', '');

  dateCourte = (e: EventSummary) => {
    const meme = this.part(e.dateDebut, { dateStyle: 'short' }) === this.part(e.dateFin, { dateStyle: 'short' });
    const debut = this.part(e.dateDebut, { weekday: 'short', day: 'numeric', month: 'short' }).replace(/\./g, '');
    if (meme) return `${debut} · ${formatTime(e.dateDebut)}`;
    const fin = this.part(e.dateFin, { day: 'numeric', month: 'short', year: 'numeric' }).replace(/\./g, '');
    return `${debut} → ${fin}`;
  };

  dateLongue = (e: EventSummary) => {
    const opts: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' };
    const d = this.part(e.dateDebut, opts);
    const f = this.part(e.dateFin, opts);
    return d === f ? `${d}, ${formatTime(e.dateDebut)} – ${formatTime(e.dateFin)}` : `Du ${d} au ${f}`;
  };

  place = (e: EventSummary) => [e.lieu, e.ville].filter(Boolean).join(', ') || '—';

  compteur(e: EventSummary): { label: string; valeur: string } | null {
    const now = Date.now();
    if (new Date(e.dateDebut).getTime() <= now) return { label: 'Statut', valeur: 'En cours' };
    const jours = Math.ceil((new Date(e.dateDebut).getTime() - now) / 86_400_000);
    return { label: 'Commence dans', valeur: jours <= 1 ? '1 jour' : `${jours} jours` };
  }

  statut(s: EventStatus): { label: string; cls: string } | null {
    switch (s) {
      case 'INSCRIPTIONS_OUVERTES': return { label: 'Inscriptions ouvertes', cls: 'bg-vert-50 text-vert-800 ring-1 ring-vert-200' };
      case 'EN_COURS': return { label: 'En cours', cls: 'bg-vert-600 text-white' };
      case 'INSCRIPTIONS_FERMEES': return { label: 'Inscriptions closes', cls: 'bg-ocre-50 text-ocre-800 ring-1 ring-ocre-100' };
      case 'PUBLIE': return { label: 'Bientôt', cls: 'bg-bleu-50 text-bleu-800 ring-1 ring-bleu-100' };
      case 'TERMINE': return { label: 'Terminé', cls: 'bg-neutre-100 text-neutre-700 ring-1 ring-neutre-200' };
      default: return null;
    }
  }
}
