import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AuthResponse,
  CompleteRegistrationPayload,
  GuestSessionPayload,
  RegisterPayload,
  UserSummary,
} from './models';

const ACCESS_KEY = 'pne.access';
const REFRESH_KEY = 'pne.refresh';
const USER_KEY = 'pne.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  private _user = signal<UserSummary | null>(this.readUser());
  readonly user = this._user.asReadonly();
  /** A session exists — full account **or** guest checkout. */
  readonly isAuthenticated = computed(() => this._user() !== null);
  /** Session is a passwordless guest checkout (no real account yet). */
  readonly isGuest = computed(() => this._user()?.guest === true);
  /** A real account (guests excluded). */
  readonly isFullyAuthenticated = computed(
    () => this._user() !== null && this._user()!.guest !== true,
  );

  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  }
  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/login`, { email, password })
      .pipe(tap((res) => this.persist(res)));
  }

  register(payload: RegisterPayload): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/register`, payload)
      .pipe(tap((res) => this.persist(res)));
  }

  /** Requests a password-reset link by e-mail. Always resolves (no account enumeration). */
  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/password/forgot`, { email });
  }

  /** Sets a new password from a token received by e-mail. */
  resetPassword(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/password/reset`, { token, password });
  }

  /** Opens a passwordless guest session (checkout without an account). */
  guestSession(payload: GuestSessionPayload): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/guest`, payload)
      .pipe(tap((res) => this.persist(res)));
  }

  /** Same, but for a "no form" free ticket: just a phone number, no name. */
  guestSessionQuick(phone: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/guest-quick`, { phone })
      .pipe(tap((res) => this.persist(res)));
  }

  /** Turns the current guest session into a full account by choosing a password. */
  completeRegistration(payload: CompleteRegistrationPayload): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/complete`, payload)
      .pipe(tap((res) => this.persist(res)));
  }

  refresh(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/auth/refresh`, { refreshToken: this.refreshToken })
      .pipe(tap((res) => this.persist(res)));
  }

  logout(): void {
    const token = this.refreshToken;
    if (token) {
      this.http.post(`${this.base}/auth/logout`, { refreshToken: token }).subscribe({
        error: () => undefined,
      });
    }
    this.clear();
  }

  hasPermission(permission: string): boolean {
    return this._user()?.permissions.includes(permission) ?? false;
  }

  hasRole(role: string): boolean {
    return this._user()?.roles.includes(role) ?? false;
  }

  private persist(res: AuthResponse): void {
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this._user.set(res.user);
  }

  private clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
  }

  private readUser(): UserSummary | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as UserSummary) : null;
  }
}
