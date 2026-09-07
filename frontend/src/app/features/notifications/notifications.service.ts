import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiBase } from '../../core/api';
import { Page } from '../../core/models';

export interface AppNotification {
  id: string;
  type: string;
  titre: string;
  contenu: string;
  lien?: string;
  lu: boolean;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationsService extends ApiBase {
  readonly unread = signal(0);

  list(): Observable<Page<AppNotification>> {
    return this.get<Page<AppNotification>>('/notifications', { size: 30 });
  }

  refreshUnread(): Observable<{ count: number }> {
    return this.get<{ count: number }>('/notifications/unread-count').pipe(
      tap((r) => this.unread.set(r.count)),
    );
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/notifications/${id}/read`, {}).pipe(
      tap(() => this.unread.update((n) => Math.max(0, n - 1))),
    );
  }

  markAllRead(): Observable<unknown> {
    return this.http.post(`${this.base}/notifications/read-all`, {}).pipe(
      tap(() => this.unread.set(0)),
    );
  }
}
