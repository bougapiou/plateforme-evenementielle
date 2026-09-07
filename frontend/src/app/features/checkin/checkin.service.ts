import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import { EventSummary } from '../events/event.models';

export type CheckinResult = 'VALIDE' | 'DEJA_UTILISE' | 'INVALIDE';

export interface ScanResponse {
  resultat: CheckinResult;
  message: string;
  eventNom: string;
  participantNom?: string;
  categorieNom?: string;
  numeroBillet?: string;
  heureEntree?: string;
  premierControleLe?: string;
}

export interface CheckinView {
  id: string;
  ticketId?: string;
  resultat: CheckinResult;
  scannedAt: string;
  scannedBy?: string;
  detail?: string;
}

export interface StaffMember {
  id: string;
  userId: string;
  fullName: string;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class CheckinService extends ApiBase {
  /** Events the signed-in user may run entry control for. */
  controllableEvents(): Observable<EventSummary[]> {
    return this.get<EventSummary[]>('/checkins/events');
  }
  scan(token: string, eventId: string): Observable<ScanResponse> {
    return this.http.post<ScanResponse>(`${this.base}/checkins/scan`, { token, eventId });
  }
  checkins(eventId: string): Observable<Page<CheckinView>> {
    return this.get<Page<CheckinView>>(`/events/${eventId}/checkins`, { size: 50 });
  }
  stats(eventId: string): Observable<Record<string, number>> {
    return this.get<Record<string, number>>(`/events/${eventId}/checkin-stats`);
  }
  staff(eventId: string): Observable<StaffMember[]> {
    return this.get<StaffMember[]>(`/events/${eventId}/staff`);
  }
  addStaff(eventId: string, email: string): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/events/${eventId}/staff`, { email });
  }
  removeStaff(eventId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${eventId}/staff/${userId}`);
  }
}
