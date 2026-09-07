import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import { Organizer, OrganizerApplyPayload } from './organizer.models';

@Injectable({ providedIn: 'root' })
export class OrganizersService extends ApiBase {
  me(): Observable<Organizer> {
    return this.get<Organizer>('/organizers/me');
  }

  apply(payload: OrganizerApplyPayload): Observable<Organizer> {
    return this.http.post<Organizer>(`${this.base}/organizers/apply`, payload);
  }

  updateMe(payload: OrganizerApplyPayload & { logoUrl?: string }): Observable<Organizer> {
    return this.http.put<Organizer>(`${this.base}/organizers/me`, payload);
  }

  // --- admin ---
  adminList(opts: { statut?: string; page?: number }): Observable<Page<Organizer>> {
    return this.get<Page<Organizer>>('/organizers', { ...opts, size: 20 });
  }

  approve(id: string): Observable<Organizer> {
    return this.http.post<Organizer>(`${this.base}/organizers/${id}/approve`, {});
  }

  suspend(id: string): Observable<Organizer> {
    return this.http.post<Organizer>(`${this.base}/organizers/${id}/suspend`, {});
  }
}
