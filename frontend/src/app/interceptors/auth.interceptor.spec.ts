import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceMock: {
    getAccessToken: ReturnType<typeof vi.fn>;
    refreshAccessToken: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authServiceMock = {
      getAccessToken: vi.fn().mockReturnValue('initial-token'),
      refreshAccessToken: vi.fn(),
      logout: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceMock }
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches the Authorization header to non-auth requests', () => {
    httpClient.get('http://localhost:8080/api/messages/inbox').subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/messages/inbox');
    expect(req.request.headers.get('Authorization')).toBe('Bearer initial-token');
    req.flush([]);
  });

  it('does NOT attach the Authorization header to auth endpoints', () => {
    httpClient.post('http://localhost:8080/api/auth/login', {}).subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('on 401, refreshes the token once and retries the original request with the new token', () => {
    authServiceMock.refreshAccessToken.mockReturnValue(
      of({ accessToken: 'new-token', expiresInSeconds: 900, username: 'alice', role: 'USER' })
    );

    let result: unknown;
    httpClient.get('http://localhost:8080/api/messages/inbox').subscribe({
      next: (res) => (result = res)
    });

    const firstAttempt = httpMock.expectOne('http://localhost:8080/api/messages/inbox');
    firstAttempt.flush('Token abgelaufen', { status: 401, statusText: 'Unauthorized' });

    // Nach dem (gemockten) erfolgreichen Refresh wird derselbe Request
    // automatisch wiederholt - diesmal mit dem neuen Token im Header.
    const retriedAttempt = httpMock.expectOne('http://localhost:8080/api/messages/inbox');
    expect(retriedAttempt.request.headers.get('Authorization')).toBe('Bearer new-token');
    retriedAttempt.flush(['ok']);

    expect(result).toEqual(['ok']);
  });

  it('on 401 where the refresh itself fails, logs out and propagates the original error', () => {
    authServiceMock.refreshAccessToken.mockReturnValue(throwError(() => new Error('refresh failed')));

    let errorCaught: unknown;
    httpClient.get('http://localhost:8080/api/messages/inbox').subscribe({
      error: (err) => (errorCaught = err)
    });

    const req = httpMock.expectOne('http://localhost:8080/api/messages/inbox');
    req.flush('Token abgelaufen', { status: 401, statusText: 'Unauthorized' });

    expect(authServiceMock.logout).toHaveBeenCalledTimes(1);
    expect(errorCaught).toBeTruthy();
  });

  it('does not attempt a refresh for a 401 on an auth endpoint itself (avoids an infinite loop)', () => {
    let errorCaught: unknown;
    httpClient.post('http://localhost:8080/api/auth/login', {}).subscribe({
      error: (err) => (errorCaught = err)
    });

    const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
    req.flush('Falsche Anmeldedaten', { status: 401, statusText: 'Unauthorized' });

    expect(authServiceMock.refreshAccessToken).not.toHaveBeenCalled();
    expect(errorCaught).toBeTruthy();
  });

  it('passes non-401 errors straight through without attempting a refresh', () => {
    let errorCaught: unknown;
    httpClient.get('http://localhost:8080/api/messages/inbox').subscribe({
      error: (err) => (errorCaught = err)
    });

    const req = httpMock.expectOne('http://localhost:8080/api/messages/inbox');
    req.flush('Server-Fehler', { status: 500, statusText: 'Internal Server Error' });

    expect(authServiceMock.refreshAccessToken).not.toHaveBeenCalled();
    expect(errorCaught).toBeTruthy();
  });
});
