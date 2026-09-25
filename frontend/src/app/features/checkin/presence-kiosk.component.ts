import { Component, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AttendanceView, CheckinService } from './checkin.service';
import { PresencePanelComponent } from '../../shared/presence-panel.component';
import { PresenceSource, isPresenceSource } from '../../shared/presence';
import { pollAttendance } from '../../shared/presence-poll';

const REFRESH_MS = 3000;

/**
 * Full-screen, chrome-free display of the live flow counters — meant for a monitor at the venue
 * entrance. No navbar, no sidebar: this route sits outside the dashboard layout on purpose.
 * `?activityId=` narrows it to one activity, `?source=qr|physique|combine` picks the numbers shown.
 */
@Component({
  selector: 'app-presence-kiosk',
  standalone: true,
  imports: [PresencePanelComponent],
  template: `
    <app-presence-panel [kiosk]="true" [closable]="true" [data]="data()" [activityId]="activityId"
                        [initialSource]="initialSource" [titleFallback]="eventNom"
                        [message]="unavailable() ? unavailableMessage : null" (closed)="close()" />
  `,
})
export class PresenceKioskComponent implements OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private checkin = inject(CheckinService);

  private eventId = this.route.snapshot.paramMap.get('eventId') ?? '';
  activityId = this.route.snapshot.queryParamMap.get('activityId');
  eventNom = this.route.snapshot.queryParamMap.get('nom') ?? 'Événement';
  initialSource: PresenceSource | null = (() => {
    const v = this.route.snapshot.queryParamMap.get('source');
    return isPresenceSource(v) ? v : null;
  })();

  data = signal<AttendanceView | null>(null);
  unavailable = signal(false);
  unavailableMessage = 'Événement introuvable, ou vous n\'avez plus accès à sa présence.';

  private poll = pollAttendance(
    () => this.checkin.attendance(this.eventId),
    REFRESH_MS,
    (v) => this.data.set(v),
    () => this.unavailable.set(true),
  );

  ngOnDestroy(): void {
    this.poll.unsubscribe();
  }

  close(): void {
    if (document.fullscreenElement) document.exitFullscreen?.().catch(() => {});
    this.router.navigateByUrl('/tableau-de-bord/presence');
  }
}
