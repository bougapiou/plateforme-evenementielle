import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { IconComponent } from '../../shared/icon.component';
import { PointageService } from '../pointage/pointage.service';
import { PointageStandStat } from '../pointage/pointage.models';

interface PublicLink {
  label: string;
  description: string;
  path: string;
}

/**
 * Admin page for the "Pointage des visiteurs" module: public links to share (no account needed
 * to open them) and management of the choices offered to visitors.
 */
@Component({
  selector: 'app-admin-pointage',
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <h1 class="text-xl font-bold text-slate-800">Pointage des visiteurs</h1>
    <p class="mt-1 text-sm text-slate-500">
      Le pointage n'apparaît plus dans le menu du site public : partagez les liens ci-dessous
      (affiche, QR code, message…). Ils s'ouvrent sans compte.
    </p>

    <!-- Liens publics -->
    <div class="mt-4 card divide-y divide-slate-100">
      @for (l of links; track l.path) {
        <div class="flex flex-wrap items-center justify-between gap-3 p-4">
          <div class="min-w-0">
            <p class="font-medium text-slate-700">{{ l.label }}</p>
            <p class="text-xs text-slate-500">{{ l.description }}</p>
            <code class="mt-1 block break-all text-xs text-brand-700">{{ url(l.path) }}</code>
          </div>
          <div class="flex shrink-0 items-center gap-2">
            <button type="button" class="btn-ghost text-sm" (click)="copy(l.path)">
              <app-icon [name]="copied() === l.path ? 'check' : 'repeat'" class="h-4 w-4" />
              {{ copied() === l.path ? 'Copié' : 'Copier' }}
            </button>
            <a [href]="url(l.path)" target="_blank" rel="noopener" class="btn-primary text-sm">
              Ouvrir <app-icon name="arrow-right" class="h-4 w-4" />
            </a>
          </div>
        </div>
      }
    </div>

    <!-- Gestion des choix -->
    <h2 class="mt-8 font-semibold text-slate-800">Choix proposés aux visiteurs</h2>
    <form class="mt-3 flex flex-wrap gap-2" (ngSubmit)="add()">
      <input class="form-input min-w-0 flex-1" name="nouveau" [(ngModel)]="nouveauNom"
             maxlength="200" placeholder="Nom du nouveau choix (ex. Ministère de la Santé)" />
      <button type="submit" class="btn-primary" [disabled]="busy() || !nouveauNom.trim()">
        Ajouter
      </button>
    </form>
    @if (error()) {
      <p class="mt-2 text-sm text-red-600">{{ error() }}</p>
    }

    <div class="mt-4 card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-slate-50 text-left text-slate-500">
          <tr>
            <th class="px-4 py-2">Choix</th>
            <th class="px-4 py-2 text-right">Passages</th>
            <th class="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          @for (s of stands(); track s.standId) {
            <tr>
              <td class="px-4 py-2">
                @if (editingId() === s.standId) {
                  <input class="form-input w-full py-1 text-sm" [(ngModel)]="editNom"
                         [ngModelOptions]="{ standalone: true }" maxlength="200"
                         (keydown.enter)="saveRename(s)" (keydown.escape)="editingId.set(null)" />
                } @else {
                  <span class="font-medium text-slate-700">{{ s.nom }}</span>
                }
              </td>
              <td class="px-4 py-2 text-right tabular-nums text-slate-600">{{ s.visites }}</td>
              <td class="whitespace-nowrap px-4 py-2 text-right">
                @if (editingId() === s.standId) {
                  <button type="button" class="btn-ghost text-green-700" [disabled]="busy()"
                          (click)="saveRename(s)">Enregistrer</button>
                  <button type="button" class="btn-ghost" (click)="editingId.set(null)">Annuler</button>
                } @else {
                  <button type="button" class="btn-ghost" (click)="startRename(s)">Renommer</button>
                  <button type="button" class="btn-ghost text-red-700" [disabled]="busy()"
                          (click)="remove(s)">Supprimer</button>
                }
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="3" class="px-4 py-6 text-center text-slate-400">
                Aucun choix. Ajoutez-en un ci-dessus.
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class AdminPointageComponent {
  private service = inject(PointageService);

  readonly links: PublicLink[] = [
    {
      label: 'Page de pointage',
      description: 'Le visiteur coche les choix visités et donne une identité facultative.',
      path: '/pointage-visiteurs',
    },
    {
      label: 'Statistiques en direct',
      description: 'Compteurs par choix, actualisés automatiquement (écran sur place).',
      path: '/pointage-visiteurs/stats',
    },
    {
      label: 'Rapport global',
      description: 'Synthèse de tous les passages.',
      path: '/pointage-visiteurs/rapport',
    },
  ];

  stands = signal<PointageStandStat[]>([]);
  busy = signal(false);
  error = signal<string | null>(null);
  copied = signal<string | null>(null);
  editingId = signal<string | null>(null);
  nouveauNom = '';
  editNom = '';

  constructor() {
    this.reload();
  }

  url(path: string): string {
    return `${window.location.origin}${path}`;
  }

  copy(path: string): void {
    navigator.clipboard?.writeText(this.url(path)).then(() => {
      this.copied.set(path);
      setTimeout(() => this.copied.set(null), 2000);
    });
  }

  reload(): void {
    this.service.stats().subscribe((s) => this.stands.set(s.stands));
  }

  add(): void {
    const nom = this.nouveauNom.trim();
    if (!nom) return;
    this.run(this.service.createStand(nom), () => (this.nouveauNom = ''));
  }

  startRename(s: PointageStandStat): void {
    this.editNom = s.nom;
    this.editingId.set(s.standId);
  }

  saveRename(s: PointageStandStat): void {
    const nom = this.editNom.trim();
    if (!nom) return;
    this.run(this.service.renameStand(s.standId, nom), () => this.editingId.set(null));
  }

  remove(s: PointageStandStat): void {
    const warning = s.visites
      ? `\n\nSes ${s.visites} passage(s) enregistré(s) seront aussi supprimés.`
      : '';
    if (!confirm(`Supprimer « ${s.nom} » ?${warning}`)) return;
    this.run(this.service.deleteStand(s.standId));
  }

  private run(obs: Observable<unknown>, onSuccess?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    obs.subscribe({
      next: () => {
        onSuccess?.();
        this.busy.set(false);
        this.reload();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.message ?? 'Action impossible.');
      },
    });
  }
}
