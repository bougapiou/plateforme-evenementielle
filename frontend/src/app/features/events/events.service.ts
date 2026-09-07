import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import {
  Activity,
  EventCategory,
  EventDetail,
  EventPayload,
  EventPublic,
  EventSummary,
  Partner,
  Speaker,
} from './event.models';

@Injectable({ providedIn: 'root' })
export class EventsService extends ApiBase {
  categories(): Observable<EventCategory[]> {
    return this.get<EventCategory[]>('/event-categories');
  }

  // --- organiser ---
  mine(opts: { statut?: string; page?: number } = {}): Observable<Page<EventSummary>> {
    return this.get<Page<EventSummary>>('/events/mine', { ...opts, size: 20 });
  }

  byId(id: string): Observable<EventDetail> {
    return this.get<EventDetail>(`/events/${id}`);
  }

  create(payload: EventPayload): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${this.base}/events`, payload);
  }

  update(id: string, payload: EventPayload): Observable<EventDetail> {
    return this.http.put<EventDetail>(`${this.base}/events/${id}`, payload);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${id}`);
  }

  transition(id: string, action: string, body: unknown = {}): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${this.base}/events/${id}/${action}`, body);
  }

  // --- programme / speakers / partners ---
  activities(eventId: string): Observable<Activity[]> {
    return this.get<Activity[]>(`/events/${eventId}/activities`);
  }
  saveActivity(eventId: string, body: Partial<Activity>, id?: string): Observable<Activity> {
    return id
      ? this.http.put<Activity>(`${this.base}/events/${eventId}/activities/${id}`, body)
      : this.http.post<Activity>(`${this.base}/events/${eventId}/activities`, body);
  }
  deleteActivity(eventId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${eventId}/activities/${id}`);
  }

  speakers(eventId: string): Observable<Speaker[]> {
    return this.get<Speaker[]>(`/events/${eventId}/speakers`);
  }
  saveSpeaker(eventId: string, body: Partial<Speaker>, id?: string): Observable<Speaker> {
    return id
      ? this.http.put<Speaker>(`${this.base}/events/${eventId}/speakers/${id}`, body)
      : this.http.post<Speaker>(`${this.base}/events/${eventId}/speakers`, body);
  }
  deleteSpeaker(eventId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${eventId}/speakers/${id}`);
  }

  partners(eventId: string): Observable<Partner[]> {
    return this.get<Partner[]>(`/events/${eventId}/partners`);
  }
  savePartner(eventId: string, body: Partial<Partner>, id?: string): Observable<Partner> {
    return id
      ? this.http.put<Partner>(`${this.base}/events/${eventId}/partners/${id}`, body)
      : this.http.post<Partner>(`${this.base}/events/${eventId}/partners`, body);
  }
  deletePartner(eventId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${eventId}/partners/${id}`);
  }

  // --- admin ---
  adminList(opts: { search?: string; statut?: string; page?: number } = {}): Observable<Page<EventSummary>> {
    return this.get<Page<EventSummary>>('/events/admin', { ...opts, size: 20 });
  }

  // --- public ---
  publicList(opts: {
    search?: string;
    categorie?: string;
    ville?: string;
    page?: number;
  } = {}): Observable<Page<EventSummary>> {
    return this.get<Page<EventSummary>>('/public/events', { ...opts, size: 12 });
  }

  publicBySlug(slug: string): Observable<EventPublic> {
    return this.get<EventPublic>(`/public/events/${slug}`);
  }
}
