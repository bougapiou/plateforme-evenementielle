import { Injectable } from '@angular/core';
import { HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import { EventDocument, RegisterPayload, Registration } from './registration.models';

@Injectable({ providedIn: 'root' })
export class RegistrationsService extends ApiBase {
  register(eventId: string, body: RegisterPayload): Observable<Registration> {
    return this.http.post<Registration>(`${this.base}/events/${eventId}/registrations`, body);
  }
  mine(): Observable<Page<Registration>> {
    return this.get<Page<Registration>>('/registrations/my', { size: 50 });
  }
  byId(id: string): Observable<Registration> {
    return this.get<Registration>(`/registrations/${id}`);
  }
  cancel(id: string): Observable<Registration> {
    return this.http.post<Registration>(`${this.base}/registrations/${id}/cancel`, {});
  }
  confirmationPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/registrations/${id}/confirmation.pdf`, { responseType: 'blob' });
  }

  // organiser
  forEvent(eventId: string): Observable<Page<Registration>> {
    return this.get<Page<Registration>>(`/events/${eventId}/registrations`, { size: 100 });
  }
  confirm(id: string): Observable<Registration> {
    return this.http.post<Registration>(`${this.base}/registrations/${id}/confirm`, {});
  }
  reject(id: string, motif: string): Observable<Registration> {
    return this.http.post<Registration>(`${this.base}/registrations/${id}/reject`, { motif });
  }

  // documents
  documents(id: string): Observable<EventDocument[]> {
    return this.get<EventDocument[]>(`/registrations/${id}/documents`);
  }
  uploadDocument(id: string, file: File, type?: string): Observable<HttpEvent<EventDocument>> {
    const form = new FormData();
    form.append('file', file);
    if (type) form.append('type', type);
    return this.http.post<EventDocument>(`${this.base}/registrations/${id}/documents`, form, {
      reportProgress: true,
      observe: 'events',
    });
  }
  deleteDocument(id: string, docId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/registrations/${id}/documents/${docId}`);
  }
}
