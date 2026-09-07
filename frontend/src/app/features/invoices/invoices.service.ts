import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';

export interface Invoice {
  id: string;
  numero: string;
  type: 'FACTURE' | 'RECU';
  paymentId: string;
  montant: number;
  devise: string;
  montantFormatte: string;
  clientNom?: string;
  lignes?: string;
  emiseLe: string;
  pdfUrl: string;
}

@Injectable({ providedIn: 'root' })
export class InvoicesService extends ApiBase {
  mine(): Observable<Invoice[]> {
    return this.get<Invoice[]>('/invoices/my');
  }
  forPayment(paymentId: string): Observable<Invoice[]> {
    return this.get<Invoice[]>(`/payments/${paymentId}/invoices`);
  }
  pdfBlob(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/invoices/${id}/pdf`, { responseType: 'blob' });
  }
}

/** Downloads a PDF blob fetched with the auth interceptor. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}
