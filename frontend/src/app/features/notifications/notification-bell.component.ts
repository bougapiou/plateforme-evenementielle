import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { AppNotification, NotificationsService } from './notifications.service';
import { formatDateTime } from '../../shared/format';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="relative">
      <button type="button" class="relative rounded-lg p-2 hover:bg-slate-100" (click)="toggle()">
        <span class="text-lg">🔔</span>
        @if (service.unread() > 0) {
          <span class="absolute -right-0.5 -top-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
            {{ service.unread() > 9 ? '9+' : service.unread() }}
          </span>
        }
      </button>

      @if (open()) {
        <div class="absolute right-0 z-20 mt-2 w-80 rounded-xl border border-slate-200 bg-white shadow-lg">
          <div class="flex items-center justify-between border-b border-slate-100 px-4 py-2 text-sm">
            <span class="font-semibold text-slate-700">Notifications</span>
            <button class="text-xs text-brand-700" (click)="markAll()">Tout marquer lu</button>
          </div>
          <ul class="max-h-96 divide-y divide-slate-100 overflow-y-auto text-sm">
            @for (n of items(); track n.id) {
              <li class="px-4 py-2" [class.bg-brand-50]="!n.lu">
                <a [routerLink]="n.lien || '/tableau-de-bord'" (click)="read(n)">
                  <p class="font-medium text-slate-800">{{ n.titre }}</p>
                  <p class="text-slate-500">{{ n.contenu }}</p>
                  <p class="mt-0.5 text-[11px] text-slate-400">{{ dt(n.createdAt) }}</p>
                </a>
              </li>
            } @empty {
              <li class="px-4 py-6 text-center text-slate-400">Aucune notification.</li>
            }
          </ul>
        </div>
      }
    </div>
  `,
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  service = inject(NotificationsService);
  open = signal(false);
  items = signal<AppNotification[]>([]);
  private sub?: Subscription;

  dt = (iso?: string) => formatDateTime(iso);

  ngOnInit(): void {
    this.service.refreshUnread().subscribe();
    this.sub = interval(30_000).subscribe(() => this.service.refreshUnread().subscribe());
  }
  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  toggle(): void {
    this.open.update((v) => !v);
    if (this.open()) this.service.list().subscribe((p) => this.items.set(p.content));
  }
  read(n: AppNotification): void {
    if (!n.lu) this.service.markRead(n.id).subscribe();
    this.open.set(false);
  }
  markAll(): void {
    this.service.markAllRead().subscribe(() =>
      this.service.list().subscribe((p) => this.items.set(p.content)),
    );
  }
}
