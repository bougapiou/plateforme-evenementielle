import { Injectable } from '@angular/core';
import { EMPTY, Observable, switchMap } from 'rxjs';
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

  /**
   * Initiates a payment, then adapts to whichever provider is active:
   * - "sandbox" completes right away (instant demo payment, current UX).
   * - a real provider (e.g. "arzeka") sends the browser to its checkout
   *   page (`paymentUrl`) — the observable then simply completes with no
   *   value, since the page is navigating away.
   */
  payOrRedirect(
    targetType: PaymentTargetType,
    targetId: string,
    moyen?: string,
  ): Observable<Payment> {
    return this.initiate({ targetType, targetId, moyen }).pipe(
      switchMap((p) => {
        if (p.provider === 'sandbox') {
          return this.simulate(p.reference, 'SUCCESS');
        }
        window.location.href = p.paymentUrl!;
        return EMPTY;
      }),
    );
  }

  /** Re-checks a payment with the provider — a safety net if a webhook is missed/delayed. */
  recheck(reference: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/${reference}/recheck`, {});
  }

  mine(): Observable<Page<Payment>> {
    return this.get<Page<Payment>>('/payments/my', { size: 50 });
  }
}
