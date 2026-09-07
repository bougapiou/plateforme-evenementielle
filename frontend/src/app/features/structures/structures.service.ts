import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import {
  Structure,
  StructureMember,
  StructurePayload,
  StructureStatus,
  StructureSummary,
} from './structure.models';

@Injectable({ providedIn: 'root' })
export class StructuresService extends ApiBase {
  mine(): Observable<StructureSummary[]> {
    return this.get<StructureSummary[]>('/structures/mine');
  }

  byId(id: string): Observable<Structure> {
    return this.get<Structure>(`/structures/${id}`);
  }

  create(payload: StructurePayload): Observable<Structure> {
    return this.http.post<Structure>(`${this.base}/structures`, payload);
  }

  update(id: string, payload: StructurePayload): Observable<Structure> {
    return this.http.put<Structure>(`${this.base}/structures/${id}`, payload);
  }

  members(id: string): Observable<StructureMember[]> {
    return this.get<StructureMember[]>(`/structures/${id}/members`);
  }

  addMember(id: string, body: { email: string; roleInterne: string; fonction?: string }) {
    return this.http.post<StructureMember>(`${this.base}/structures/${id}/members`, body);
  }

  removeMember(id: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/structures/${id}/members/${userId}`);
  }

  // --- admin ---
  adminList(opts: { search?: string; statut?: string; page?: number }): Observable<Page<StructureSummary>> {
    return this.get<Page<StructureSummary>>('/structures', { ...opts, size: 20 });
  }

  setStatus(id: string, statut: StructureStatus): Observable<Structure> {
    return this.http.patch<Structure>(`${this.base}/structures/${id}/status`, { statut });
  }
}
