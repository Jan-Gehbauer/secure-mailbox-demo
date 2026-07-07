import { Injectable, signal, computed } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';

/**
 * Hält den Auth-Zustand zentral.
 *
 * WICHTIG: Der Access-Token liegt NUR im Speicher (Signal), nie in
 * localStorage/sessionStorage. localStorage ist für jedes im Browser
 * laufende JavaScript lesbar - im Fall einer XSS-Lücke irgendwo in der
 * App könnte ein Angreifer den Token sonst einfach auslesen und
 * exfiltrieren. Der Preis dafür: der Access-Token ist nach einem
 * Seiten-Reload weg - das federt initializeSession() ab, indem es beim
 * App-Start einmal versucht, über den httpOnly-Refresh-Cookie (das
 * Frontend kann diesen Cookie selbst gar nicht lesen, der Browser
 * schickt ihn aber automatisch mit) einen neuen Access-Token zu holen.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly baseUrl = 'http://localhost:8080/api/auth';

  private readonly accessTokenSignal = signal<string | null>(null);
  private readonly usernameSignal = signal<string | null>(null);
  private readonly roleSignal = signal<string | null>(null);

  readonly isLoggedIn = computed(() => this.accessTokenSignal() !== null);
  readonly username = computed(() => this.usernameSignal());
  readonly role = computed(() => this.roleSignal());

  constructor(private http: HttpClient) {}

  getAccessToken(): string | null {
    return this.accessTokenSignal();
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, request, { withCredentials: true })
      .pipe(tap((response) => this.applySession(response)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, request, { withCredentials: true })
      .pipe(tap((response) => this.applySession(response)));
  }

  /**
   * Versucht beim App-Start, über den httpOnly-Refresh-Cookie (falls
   * vorhanden und gültig) eine bestehende Session wiederherzustellen,
   * ohne dass der Nutzer sich erneut einloggen muss. Schlägt das fehl
   * (kein Cookie, abgelaufen, ...), bleibt der Nutzer schlicht ausgeloggt -
   * kein Fehler, der irgendwo angezeigt werden müsste.
   */
  initializeSession(): Observable<AuthResponse | null> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((response) => this.applySession(response)),
        catchError(() => {
          this.clearSession();
          return of(null);
        })
      );
  }

  /** Wird auch vom HTTP-Interceptor bei einem 401 während eines normalen Requests genutzt. */
  refreshAccessToken(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/refresh`, {}, { withCredentials: true })
      .pipe(tap((response) => this.applySession(response)));
  }

  logout(): void {
    this.http
      .post(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(catchError((err: HttpErrorResponse) => of(null)))
      .subscribe(() => this.clearSession());

    // Lokalen Zustand sofort leeren, unabhängig vom Ausgang des Server-Calls -
    // aus Nutzersicht ist man in dem Moment ausgeloggt, in dem man klickt.
    this.clearSession();
  }

  private applySession(response: AuthResponse): void {
    this.accessTokenSignal.set(response.accessToken);
    this.usernameSignal.set(response.username);
    this.roleSignal.set(response.role);
  }

  private clearSession(): void {
    this.accessTokenSignal.set(null);
    this.usernameSignal.set(null);
    this.roleSignal.set(null);
  }
}
