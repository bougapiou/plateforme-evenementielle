import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AttendanceView, CheckinService } from '../checkin/checkin.service';
import { PresencePanelComponent } from '../../shared/presence-panel.component';
import { PresenceSource, isPresenceSource } from '../../shared/presence';
import { pollAttendance } from '../../shared/presence-poll';

const REFRESH_MS = 4000;

/**
 * Version publique (sans connexion) de l'écran plein format du personnel de contrôle
 * (`checkin/presence-kiosk.component.ts`) — même présentation, par slug et via l'API publique. Hors des
 * deux layouts (public / tableau de bord) exprès : aucune navbar, aucun pied de page. Le lien accepte
 * `?source=qr|physique|combine` pour ouvrir l'écran directement sur la bonne source.
 */
@Component({
  selector: 'app-public-presence-kiosk',
  standalone: true,
  imports: [PresencePanelComponent],
  template: `
    <app-presence-panel [kiosk]="true" [closable]="true" [data]="data()" [initialSource]="initialSource"
                        titleFallback="Événement" [message]="notFound() ? notFoundMessage : null"
                        (closed)="close()" />
  `,
})
export class PublicPresenceKioskComponent implements OnDestroy {
  private router = inject(Router);
  private checkin = inject(CheckinService);

  slug = input.required<string>();
  data = signal<AttendanceView | null>(null);
  notFound = signal(false);
  notFoundMessage = "Cet événement n'a pas de présence à afficher (introuvable ou pas encore publié).";

  initialSource: PresenceSource | null = (() => {
    const v = inject(ActivatedRoute).snapshot.queryParamMap.get('source');
    return isPresenceSource(v) ? v : null;
  })();

  private poll?: Subscription;

  constructor() {
    effect(() => {
      const slug = this.slug();
      this.poll?.unsubscribe();
      this.notFound.set(false);
      if (!slug) return;
      this.poll = pollAttendance(
        () => this.checkin.publicAttendance(slug),
        REFRESH_MS,
        (v) => this.data.set(v),
        () => this.notFound.set(true),
      );
    });
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
  }

  close(): void {
    this.router.navigateByUrl(`/evenements/${this.slug()}/flux`);
  }
}
