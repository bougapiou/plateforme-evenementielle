import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import {
  Frequentation,
  SignalerPassagePayload,
  SignalerPassagesPayload,
  SignalerPassagesResult,
  StandFrequentation,
  StandInfo,
} from './frequentation.models';

@Injectable({ providedIn: 'root' })
export class FrequentationService extends ApiBase {
  standInfo(slug: string, standId: string): Observable<StandInfo> {
    return this.get<StandInfo>(`/public/events/${slug}/stands/${standId}/passage`);
  }
  signaler(slug: string, standId: string, body: SignalerPassagePayload): Observable<void> {
    return this.http.post<void>(`${this.base}/public/events/${slug}/stands/${standId}/passage`, body);
  }
  signalerPlusieurs(slug: string, body: SignalerPassagesPayload): Observable<SignalerPassagesResult> {
    return this.http.post<SignalerPassagesResult>(`${this.base}/public/events/${slug}/passages`, body);
  }
  stats(slug: string): Observable<Frequentation> {
    return this.get<Frequentation>(`/public/events/${slug}/frequentation`);
  }
  standStats(slug: string, standId: string): Observable<StandFrequentation> {
    return this.get<StandFrequentation>(`/public/events/${slug}/stands/${standId}/frequentation`);
  }
}
