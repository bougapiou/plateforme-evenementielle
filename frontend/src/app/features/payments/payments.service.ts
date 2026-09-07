import { Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';

export type PaymentStatus = 'EN_ATTENTE' | 'REUSSI' | 'ECHOUE' | 'ANNULE' | 'REMBOURSE';
export type PaymentTargetType = 'TICKET_ORDER' | 'STAND_RESERVATION';

export interface Payment {
  id: string;
  reference: string;
  transactionRef?: string;
  provider: string;
  moyen: string;
  targetType: PaymentTargetType;
  targetId: string;
  eventId?: string;
  montant: number;
  devise: string;
  montantFormatte: string;
  statut: PaymentStatus;
  paymentUrl?: string;
  echecMotif?: string;
  paidAt?: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentsService extends ApiBase {
  initiate(body: {
    targetType: PaymentTargetType;
    targetId: string;
    moyen?: string;
    returnUrl?: string;
  }): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments`, body);
  }

  simulate(reference: string, outcome: 'SUCCESS' | 'FAILED' = 'SUCCESS'): Observable<Payment> {
    return this.http.post<Payment>(
      `${this.base}/payments/${reference}/simulate?outcome=${outcome}`,
      {},
    );
  }

  /** Sandbox one-shot: initiate then simulate a successful callback. */
  payNow(targetType: PaymentTargetType, targetId: string, moyen = 'MOBILE_MONEY_ORANGE'): Observable<Payment> {
    return this.initiate({ targetType, targetId, moyen }).pipe(
      switchMap((p) => this.simulate(p.reference, 'SUCCESS')),
    );
  }

  mine(): Observable<Page<Payment>> {
    return this.get<Page<Payment>>('/payments/my', { size: 50 });
  }
}
