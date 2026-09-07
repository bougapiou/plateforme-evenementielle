import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';

export type StatMap = Record<string, number | string>;

export interface EventSeries {
  quotidien: { jour: string; inscriptions: number; revenus: number }[];
  billetsParCategorie: { label: string; valeur: number }[];
}

@Injectable({ providedIn: 'root' })
export class StatsService extends ApiBase {
  adminOverview(): Observable<StatMap> {
    return this.get<StatMap>('/stats/admin/overview');
  }
  organizerOverview(): Observable<StatMap> {
    return this.get<StatMap>('/stats/organizer/overview');
  }
  eventStats(eventId: string): Observable<StatMap> {
    return this.get<StatMap>(`/stats/events/${eventId}`);
  }
  eventSeries(eventId: string): Observable<EventSeries> {
    return this.get<EventSeries>(`/stats/events/${eventId}/series`);
  }
}
