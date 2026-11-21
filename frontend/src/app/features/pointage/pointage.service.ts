import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import {
  PointageStand,
  PointageStandStat,
  PointageStats,
  SignalerPointagePassagesPayload,
  SignalerPointagePassagesResult,
} from './pointage.models';

@Injectable({ providedIn: 'root' })
export class PointageService extends ApiBase {
  stands(): Observable<PointageStand[]> {
    return this.get<PointageStand[]>('/public/pointage/stands');
  }
  signalerPlusieurs(body: SignalerPointagePassagesPayload): Observable<SignalerPointagePassagesResult> {
    return this.http.post<SignalerPointagePassagesResult>(`${this.base}/public/pointage/passages`, body);
  }
  stats(): Observable<PointageStats> {
    return this.get<PointageStats>('/public/pointage/stats');
  }
  standStats(standId: string): Observable<PointageStandStat> {
    return this.get<PointageStandStat>(`/public/pointage/stands/${standId}/stats`);
  }

  // --- Administration des choix (permission POINTAGE_MANAGE) ---
  adminStands(): Observable<PointageStand[]> {
    return this.get<PointageStand[]>('/admin/pointage/stands');
  }
  createStand(nom: string): Observable<PointageStand> {
    return this.http.post<PointageStand>(`${this.base}/admin/pointage/stands`, { nom });
  }
  renameStand(id: string, nom: string): Observable<PointageStand> {
    return this.http.put<PointageStand>(`${this.base}/admin/pointage/stands/${id}`, { nom });
  }
  deleteStand(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/pointage/stands/${id}`);
  }
}
