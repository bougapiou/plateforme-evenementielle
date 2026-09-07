import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';
import {
  EventTicket,
  EventTicketPayload,
  MyTicket,
  TicketOrder,
} from '../events/event.models';

@Injectable({ providedIn: 'root' })
export class TicketsService extends ApiBase {
  // --- organiser: ticket categories ---
  forEvent(eventId: string): Observable<EventTicket[]> {
    return this.get<EventTicket[]>(`/events/${eventId}/tickets`);
  }
  save(eventId: string, body: EventTicketPayload, id?: string): Observable<EventTicket> {
    return id
      ? this.http.put<EventTicket>(`${this.base}/events/${eventId}/tickets/${id}`, body)
      : this.http.post<EventTicket>(`${this.base}/events/${eventId}/tickets`, body);
  }
  remove(eventId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/events/${eventId}/tickets/${id}`);
  }
  orders(eventId: string): Observable<Page<TicketOrder>> {
    return this.get<Page<TicketOrder>>(`/ticket-orders/for-event/${eventId}`, { size: 50 });
  }

  // --- public ---
  publicTickets(slug: string): Observable<EventTicket[]> {
    return this.get<EventTicket[]>(`/public/events/${slug}/tickets`);
  }

  // --- buyer ---
  createOrder(body: {
    eventId: string;
    structureId?: string;
    acheteurNom?: string;
    acheteurEmail?: string;
    lignes: { eventTicketId: string; quantite: number }[];
  }): Observable<TicketOrder> {
    return this.http.post<TicketOrder>(`${this.base}/ticket-orders`, body);
  }
  myOrders(): Observable<Page<TicketOrder>> {
    return this.get<Page<TicketOrder>>('/ticket-orders/my', { size: 50 });
  }
  cancelOrder(id: string): Observable<TicketOrder> {
    return this.http.post<TicketOrder>(`${this.base}/ticket-orders/${id}/cancel`, {});
  }
  paySandbox(id: string): Observable<TicketOrder> {
    return this.http.post<TicketOrder>(`${this.base}/ticket-orders/${id}/pay-sandbox`, {});
  }
  myTickets(): Observable<MyTicket[]> {
    return this.get<MyTicket[]>('/tickets/my');
  }
  qrBlob(ticketId: string): Observable<Blob> {
    return this.http.get(`${this.base}/tickets/${ticketId}/qr.png`, { responseType: 'blob' });
  }
  pdfBlob(ticketId: string): Observable<Blob> {
    return this.http.get(`${this.base}/tickets/${ticketId}/pdf`, { responseType: 'blob' });
  }
}
