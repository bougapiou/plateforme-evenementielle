import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import { Activity, EventSummary } from '../events/event.models';

export type CheckinResult = 'VALIDE' | 'DEJA_UTILISE' | 'INVALIDE';
export type CheckinDirection = 'ENTREE' | 'SORTIE';

export interface ScanResponse {
  resultat: CheckinResult;
  sens: CheckinDirection;
  reentree: boolean;
  message: string;
  eventNom: string;
  activiteNom?: string;
  participantNom?: string;
  categorieNom?: string;
  numeroBillet?: string;
  heureEntree?: string;
  premierControleLe?: string;
}

export interface CheckinView {
  id: string;
  ticketId?: string;
  activityId?: string;
  resultat: CheckinResult;
  sens: CheckinDirection;
  scannedAt: string;
  scannedBy?: string;
  detail?: string;
}

export type FlowCounters = Record<string, number>;

export interface ActivityFlow {
  id: string;
  titre: string;
  dateDebut?: string;
  acces?: string;
  flux: FlowCounters;
}

export interface AttendanceView {
  eventId: string;
  eventNom: string;
  event: FlowCounters;
  activites: ActivityFlow[];
  /** Anonymous laser-sensor counts (entrees / sorties / presents), apart from ticket scans. */
  comptagePhysique?: FlowCounters;
}

export interface SensorView {
  id: string;
  nom: string;
  clePrefixe: string;
  actif: boolean;
  derniereActivite?: string;
  entrees: number;
  sorties: number;
}

export interface SensorCreated {
  capteur: SensorView;
  /** Shown only once, right after creation. */
  cle: string;
}

/** One row per ticket number: its full entry / exit / re-entry history. */
export interface TicketFlowView {
  ticketId: string;
  numero: string;
  participantNom?: string;
  categorieNom?: string;
  entrees: number;
  sorties: number;
  reentrees: number;
  present: boolean;
  dernierScan?: string;
}

export interface StaffMember {
  id: string;
  userId: string;
  fullName: string;
  email: string;
  /** Activities (rooms / slots) the agent is restricted to; empty = whole event. */
  activityIds: string[];
  fonction?: 'CONTROLE' | 'PROGRAMME';
}

export interface OnSiteResponse {
  numeroBillet: string;
  scan: ScanResponse;
}

export interface SelfCheckinConfig {
  activityId: string;
  actif: boolean;
  jeton?: string;
}

export interface SelfCheckinInfo {
  evenement: string;
  activite: string;
  salle?: string;
  debut: string;
  fin?: string;
}

@Injectable({ providedIn: 'root' })
export class CheckinService extends ApiBase {
  /** Events the signed-in user may run entry control for. */
  controllableEvents(): Observable<EventSummary[]> {
    return this.get<EventSummary[]>('/checkins/events');
  }
  /** Activities of an event, for picking which one to control. */
  eventActivities(eventId: string): Observable<Activity[]> {
    return this.get<Activity[]>(`/checkins/events/${eventId}/activities`);
  }
  scan(
    token: string,
    eventId: string,
    activityId?: string,
    sens: CheckinDirection = 'ENTREE',
  ): Observable<ScanResponse> {
    return this.http.post<ScanResponse>(`${this.base}/checkins/scan`, {
      token,
      eventId,
      activityId: activityId ?? null,
      sens,
    });
  }
  checkins(eventId: string): Observable<Page<CheckinView>> {
    return this.get<Page<CheckinView>>(`/events/${eventId}/checkins`, { size: 50 });
  }
  stats(eventId: string, activityId?: string): Observable<Record<string, number>> {
    return this.get<Record<string, number>>(`/events/${eventId}/checkin-stats`, { activityId });
  }
  /** Real-time attendance: event-level flow + one line per activity. */
  attendance(eventId: string): Observable<AttendanceView> {
    return this.get<AttendanceView>(`/events/${eventId}/attendance`);
  }
  /** Same, but public — no login, one event at a time, found by its slug. */
  publicAttendance(slug: string): Observable<AttendanceView> {
    return this.get<AttendanceView>(`/public/events/${slug}/attendance`);
  }
  /** Full per-ticket entry/exit/re-entry history for the event or one activity. */
  ticketDetails(eventId: string, activityId?: string): Observable<TicketFlowView[]> {
    return this.get<TicketFlowView[]>(`/events/${eventId}/checkin-details`, { activityId });
  }
  staff(eventId: string): Observable<StaffMember[]> {
    return this.get<StaffMember[]>(`/events/${eventId}/staff`);
  }
  addStaff(eventId: string, email: string, fonction: 'CONTROLE' | 'PROGRAMME' = 'CONTROLE'): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/events/${eventId}/staff`, { email, fonction });
  }
  removeStaff(eventId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${eventId}/staff/${userId}`);
  }
  /** Restricts an agent to some activities (empty list = whole event). */
  assignStaffActivities(eventId: string, userId: string, activityIds: string[]): Observable<string[]> {
    return this.http.put<string[]>(`${this.base}/events/${eventId}/staff/${userId}/activities`, { activityIds });
  }
  /** On-site registration: free ticket for a walk-in visitor, let in at once. */
  onSite(eventId: string, body: { activityId?: string; nom: string; telephone: string }): Observable<OnSiteResponse> {
    return this.http.post<OnSiteResponse>(`${this.base}/checkins/events/${eventId}/sur-place`, body);
  }
  configureSelfCheckin(eventId: string, activityId: string, actif: boolean): Observable<SelfCheckinConfig> {
    return this.http.post<SelfCheckinConfig>(
      `${this.base}/events/${eventId}/activities/${activityId}/auto-enregistrement`, {}, { params: { actif } });
  }
  selfCheckinQr(eventId: string, activityId: string): Observable<Blob> {
    return this.http.get(`${this.base}/events/${eventId}/activities/${activityId}/auto-enregistrement/qr.png`,
      { responseType: 'blob' });
  }
  publicSelfCheckinInfo(jeton: string): Observable<SelfCheckinInfo> {
    return this.get<SelfCheckinInfo>(`/public/auto-enregistrement/${jeton}`);
  }
  publicSelfCheckin(jeton: string, telephone: string): Observable<ScanResponse> {
    return this.http.post<ScanResponse>(`${this.base}/public/auto-enregistrement/${jeton}`, { telephone });
  }
  sensors(eventId: string): Observable<SensorView[]> {
    return this.get<SensorView[]>(`/events/${eventId}/sensors`);
  }
  createSensor(eventId: string, nom: string): Observable<SensorCreated> {
    return this.http.post<SensorCreated>(`${this.base}/events/${eventId}/sensors`, { nom });
  }
  revokeSensor(eventId: string, sensorId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${eventId}/sensors/${sensorId}`);
  }
}
