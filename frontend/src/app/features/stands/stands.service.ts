import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import { Stand, StandReservation, StandType, StandTypePayload } from './stand.models';

@Injectable({ providedIn: 'root' })
export class StandsService extends ApiBase {
  // --- organiser ---
  types(eventId: string): Observable<StandType[]> {
    return this.get<StandType[]>(`/events/${eventId}/stand-types`);
  }
  saveType(eventId: string, body: StandTypePayload, id?: string): Observable<StandType> {
    return id
      ? this.http.put<StandType>(`${this.base}/events/${eventId}/stand-types/${id}`, body)
      : this.http.post<StandType>(`${this.base}/events/${eventId}/stand-types`, body);
  }
  removeType(eventId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${eventId}/stand-types/${id}`);
  }
  standsForEvent(eventId: string): Observable<Stand[]> {
    return this.get<Stand[]>(`/events/${eventId}/stands`);
  }
  reservationsForEvent(eventId: string): Observable<Page<StandReservation>> {
    return this.get<Page<StandReservation>>(`/stand-reservations/for-event/${eventId}`, { size: 50 });
  }

  // --- public ---
  publicTypes(slug: string): Observable<StandType[]> {
    return this.get<StandType[]>(`/public/events/${slug}/stand-types`);
  }
  publicStands(slug: string): Observable<Stand[]> {
    return this.get<Stand[]>(`/public/events/${slug}/stands`);
  }

  // --- structure ---
  reserve(body: {
    eventId: string;
    standId: string;
    structureId?: string;
    informations?: string;
  }): Observable<StandReservation> {
    return this.http.post<StandReservation>(`${this.base}/stand-reservations`, body);
  }
  myReservations(): Observable<Page<StandReservation>> {
    return this.get<Page<StandReservation>>('/stand-reservations/my', { size: 50 });
  }
  cancel(id: string): Observable<StandReservation> {
    return this.http.post<StandReservation>(`${this.base}/stand-reservations/${id}/cancel`, {});
  }
  paySandbox(id: string): Observable<StandReservation> {
    return this.http.post<StandReservation>(`${this.base}/stand-reservations/${id}/pay-sandbox`, {});
  }
  confirmationPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/stand-reservations/${id}/confirmation.pdf`, {
      responseType: 'blob',
    });
  }
}
