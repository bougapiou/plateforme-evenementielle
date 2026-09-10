import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';

export type AccreditationRole =
  | 'CONFERENCIER'
  | 'EXPOSANT'
  | 'MODERATEUR'
  | 'MAITRE_CEREMONIE'
  | 'PANELISTE'
  | 'COMPETITEUR'
  | 'INVITE'
  | 'PRESSE'
  | 'STAFF'
  | 'AUTRE';

export const ACCREDITATION_ROLES: { value: AccreditationRole; label: string }[] = [
  { value: 'CONFERENCIER', label: 'Conférencier' },
  { value: 'EXPOSANT', label: 'Exposant' },
  { value: 'MODERATEUR', label: 'Modérateur' },
  { value: 'MAITRE_CEREMONIE', label: 'Maître de cérémonie' },
  { value: 'PANELISTE', label: 'Panéliste' },
  { value: 'COMPETITEUR', label: 'Compétiteur' },
  { value: 'INVITE', label: 'Invité' },
  { value: 'PRESSE', label: 'Presse' },
  { value: 'STAFF', label: 'Staff / organisation' },
  { value: 'AUTRE', label: 'Autre (préciser)' },
];

export interface Accreditation {
  id: string;
  eventId: string;
  activityId?: string;
  activiteNom?: string;
  numero: string;
  personneNom: string;
  personneEmail?: string;
  organisation?: string;
  fonction: AccreditationRole;
  fonctionLibelle: string;
  photoUrl?: string;
  statut: 'ACTIVE' | 'REVOQUEE';
  qrImageUrl: string;
  badgePdfUrl: string;
  createdAt: string;
}

export interface AccreditationPayload {
  personneNom: string;
  personneEmail?: string;
  organisation?: string;
  fonction: AccreditationRole;
  fonctionLibre?: string;
  photoUrl?: string;
  activityId?: string;
}

@Injectable({ providedIn: 'root' })
export class AccreditationsService extends ApiBase {
  forEvent(eventId: string): Observable<Accreditation[]> {
    return this.get<Accreditation[]>(`/events/${eventId}/accreditations`);
  }
  issue(eventId: string, body: AccreditationPayload): Observable<Accreditation> {
    return this.http.post<Accreditation>(`${this.base}/events/${eventId}/accreditations`, body);
  }
  revoke(id: string): Observable<Accreditation> {
    return this.http.post<Accreditation>(`${this.base}/accreditations/${id}/revoke`, {});
  }
  badgePdfBlob(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/accreditations/${id}/badge.pdf`, { responseType: 'blob' });
  }
}
