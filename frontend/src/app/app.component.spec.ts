import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { App } from './app.component';
import { AuthService } from './services/auth.service';
import { MessageService } from './services/message.service';
import { Message } from './models/message.model';

describe('App', () => {
  let authServiceMock: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    username: ReturnType<typeof vi.fn>;
    role: ReturnType<typeof vi.fn>;
    initializeSession: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let messageServiceMock: {
    getInbox: ReturnType<typeof vi.fn>;
    getSent: ReturnType<typeof vi.fn>;
    send: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authServiceMock = {
      isLoggedIn: vi.fn().mockReturnValue(false),
      username: vi.fn().mockReturnValue(null),
      role: vi.fn().mockReturnValue(null),
      initializeSession: vi.fn().mockReturnValue(of(null)),
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn()
    };

    messageServiceMock = {
      getInbox: vi.fn().mockReturnValue(of([])),
      getSent: vi.fn().mockReturnValue(of([])),
      send: vi.fn()
    };

    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock }
      ]
    });
  });

  function createComponent() {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges(); // stösst ngOnInit an
    return fixture;
  }

  it('creates the component and checks for an existing session on init', () => {
    const fixture = createComponent();
    expect(fixture.componentInstance).toBeTruthy();
    expect(authServiceMock.initializeSession).toHaveBeenCalledTimes(1);
  });

  it('loads the inbox automatically if session restore finds the user already logged in', () => {
    authServiceMock.isLoggedIn.mockReturnValue(true);

    createComponent();

    expect(messageServiceMock.getInbox).toHaveBeenCalledTimes(1);
  });

  it('login() clears the form and loads the inbox on success', () => {
    authServiceMock.login.mockReturnValue(
      of({ accessToken: 't', expiresInSeconds: 900, username: 'alice', role: 'USER' })
    );

    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.loginForm = { username: 'alice', password: 'secret' };
    component.login();

    expect(authServiceMock.login).toHaveBeenCalledWith({ username: 'alice', password: 'secret' });
    expect(component.loginForm).toEqual({ username: '', password: '' });
    expect(messageServiceMock.getInbox).toHaveBeenCalled();
    expect(component.authLoading()).toBe(false);
  });

  it('login() shows the server error message on failure and keeps loading state consistent', () => {
    const error = new HttpErrorResponse({ status: 401, error: { message: 'Ungültige Anmeldedaten' } });
    authServiceMock.login.mockReturnValue(throwError(() => error));

    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.loginForm = { username: 'alice', password: 'falsch' };
    component.login();

    expect(component.authError()).toBe('Ungültige Anmeldedaten');
    expect(component.authLoading()).toBe(false);
  });

  it('login() does nothing if username or password is empty', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.loginForm = { username: '', password: '' };
    component.login();

    expect(authServiceMock.login).not.toHaveBeenCalled();
  });

  it('login() falls back to a network hint when the backend is unreachable (status 0)', () => {
    const error = new HttpErrorResponse({ status: 0 });
    authServiceMock.login.mockReturnValue(throwError(() => error));

    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.loginForm = { username: 'alice', password: 'secret' };
    component.login();

    expect(component.authError()).toContain('Backend nicht erreichbar');
  });

  it('sendMessage() resets the form and reloads the inbox on success', () => {
    messageServiceMock.send.mockReturnValue(of({} as Message));

    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.newMessage = { recipient: 'bob', subject: 'Hallo', body: 'Text' };
    component.sendMessage();

    expect(messageServiceMock.send).toHaveBeenCalledWith({ recipient: 'bob', subject: 'Hallo', body: 'Text' });
    expect(component.newMessage).toEqual({ recipient: '', subject: '', body: '' });
    expect(component.sendSuccess()).toBe(true);
    expect(messageServiceMock.getInbox).toHaveBeenCalled();
  });

  it('sendMessage() does nothing if a required field is missing', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.newMessage = { recipient: '', subject: 'Hallo', body: 'Text' };
    component.sendMessage();

    expect(messageServiceMock.send).not.toHaveBeenCalled();
  });

  it('logout() clears all local component state', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.inbox.set([
      { id: 1, sender: 'a', recipient: 'b', subject: 's', body: 'x', createdAt: '2026-01-01' }
    ]);
    component.sendSuccess.set(true);

    component.logout();

    expect(authServiceMock.logout).toHaveBeenCalledTimes(1);
    expect(component.inbox()).toEqual([]);
    expect(component.sendSuccess()).toBe(false);
  });
});
