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
}
