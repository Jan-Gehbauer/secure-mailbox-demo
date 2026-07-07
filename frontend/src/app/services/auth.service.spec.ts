import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AuthResponse } from '../models/auth.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const mockAuthResponse: AuthResponse = {
    accessToken: 'fake-access-token',
    expiresInSeconds: 900,
    username: 'alice',
    role: 'USER'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts logged out with no access token', () => {
    expect(service.isLoggedIn()).toBe(false);
    expect(service.getAccessToken()).toBeNull();
  });

  it('register() stores the access token and marks the user as logged in', () => {
    service
      .register({ username: 'alice', email: 'alice@example.com', password: 'sicheresPasswort123' })
      .subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/auth/register');
    expect(req.request.method).toBe('POST');
    // Noetig, damit der Browser das httpOnly Refresh-Token-Cookie mitschickt
    expect(req.request.withCredentials).toBe(true);
    req.flush(mockAuthResponse);

    expect(service.isLoggedIn()).toBe(true);
    expect(service.getAccessToken()).toBe('fake-access-token');
    expect(service.username()).toBe('alice');
  });

  it('login() stores the access token and username', () => {
    service.login({ username: 'alice', password: 'sicheresPasswort123' }).subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
    req.flush(mockAuthResponse);

    expect(service.isLoggedIn()).toBe(true);
    expect(service.role()).toBe('USER');
  });

  it('initializeSession() stays logged out silently if no valid refresh cookie exists', () => {
    let emittedValue: AuthResponse | null | undefined;

    service.initializeSession().subscribe((result) => {
      emittedValue = result;
    });

    const req = httpMock.expectOne('http://localhost:8080/api/auth/refresh');
    req.flush('Kein Refresh-Token vorhanden', { status: 401, statusText: 'Unauthorized' });

    // Kein Fehler wird nach aussen geworfen - stattdessen wird still mit
    // "null" abgeschlossen, damit die App einfach den Login-Screen zeigt.
    expect(emittedValue).toBeNull();
    expect(service.isLoggedIn()).toBe(false);
  });

  it('initializeSession() restores the session if a valid refresh cookie exists', () => {
    service.initializeSession().subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/auth/refresh');
    req.flush(mockAuthResponse);

    expect(service.isLoggedIn()).toBe(true);
    expect(service.username()).toBe('alice');
  });

  it('logout() clears the local session immediately, independent of the server response', () => {
    service.login({ username: 'alice', password: 'pw' }).subscribe();
    httpMock.expectOne('http://localhost:8080/api/auth/login').flush(mockAuthResponse);
    expect(service.isLoggedIn()).toBe(true);

    service.logout();

    // Aus Nutzersicht ist man SOFORT ausgeloggt, unabhaengig davon, ob
    // der Server-Call schon durchgelaufen ist.
    expect(service.isLoggedIn()).toBe(false);
    expect(service.getAccessToken()).toBeNull();

    const req = httpMock.expectOne('http://localhost:8080/api/auth/logout');
    req.flush(null);
  });

  it('logout() clears the session even if the server call fails', () => {
    service.login({ username: 'alice', password: 'pw' }).subscribe();
    httpMock.expectOne('http://localhost:8080/api/auth/login').flush(mockAuthResponse);

    service.logout();

    const req = httpMock.expectOne('http://localhost:8080/api/auth/logout');
    req.flush('Server-Fehler', { status: 500, statusText: 'Internal Server Error' });

    expect(service.isLoggedIn()).toBe(false);
  });
});
