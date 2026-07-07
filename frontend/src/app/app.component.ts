import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MessageService } from './services/message.service';
import { AuthService } from './services/auth.service';
import { Message } from './models/message.model';

type AuthMode = 'login' | 'register';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class App implements OnInit {

  // Signals statt normaler Properties: lösen Change Detection zuverlässig
  // aus, auch bei asynchronen Antworten (HTTP), unabhängig vom
  // Zone.js-Verhalten.

  authMode = signal<AuthMode>('login');
  authError = signal('');
  authLoading = signal(false);
  sessionRestoring = signal(true);

  loginForm = { username: '', password: '' };
  registerForm = { username: '', email: '', password: '' };

  inbox = signal<Message[]>([]);
  errorMessage = signal('');

  newMessage = {
    recipient: '',
    subject: '',
    body: ''
  };
  sending = signal(false);
  sendSuccess = signal(false);

  constructor(
    private messageService: MessageService,
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    // Der Access-Token lebt nur im Speicher (siehe AuthService) und ist
    // nach einem Seiten-Reload weg - hier versuchen wir einmal, über den
    // httpOnly-Refresh-Cookie automatisch eine bestehende Session
    // wiederherzustellen, damit man nicht bei jedem F5 neu einloggen muss.
    this.authService.initializeSession().subscribe(() => {
      this.sessionRestoring.set(false);
      if (this.authService.isLoggedIn()) {
        this.loadInbox();
      }
    });
  }

  switchAuthMode(mode: AuthMode): void {
    this.authMode.set(mode);
    this.authError.set('');
    this.loginForm = { username: '', password: '' };
    this.registerForm = { username: '', email: '', password: '' };
  }

  login(): void {
    if (!this.loginForm.username || !this.loginForm.password) {
      return;
    }

    this.authLoading.set(true);
    this.authError.set('');

    this.authService.login({ ...this.loginForm }).subscribe({
      next: () => {
        this.authLoading.set(false);
        this.loginForm = { username: '', password: '' };
        this.loadInbox();
      },
      error: (err: HttpErrorResponse) => {
        this.authLoading.set(false);
        this.authError.set(this.extractAuthErrorMessage(err, 'Anmeldung fehlgeschlagen.'));
      }
    });
  }

  register(): void {
    if (!this.registerForm.username || !this.registerForm.email || !this.registerForm.password) {
      return;
    }

    this.authLoading.set(true);
    this.authError.set('');

    this.authService.register({ ...this.registerForm }).subscribe({
      next: () => {
        this.authLoading.set(false);
        this.registerForm = { username: '', email: '', password: '' };
        this.loadInbox();
      },
      error: (err: HttpErrorResponse) => {
        this.authLoading.set(false);
        this.authError.set(this.extractAuthErrorMessage(err, 'Registrierung fehlgeschlagen.'));
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.inbox.set([]);
    this.errorMessage.set('');
    this.sendSuccess.set(false);
    this.sending.set(false);
    this.newMessage = { recipient: '', subject: '', body: '' };
  }

  loadInbox(): void {
    this.errorMessage.set('');

    this.messageService.getInbox().subscribe({
      next: (messages) => {
        this.inbox.set(messages);
      },
      error: (err) => {
        console.error('Fehler beim Laden des Posteingangs', err);
        this.errorMessage.set('Posteingang konnte nicht geladen werden. Läuft das Backend auf Port 8080?');
      }
    });
  }

  sendMessage(): void {
    if (!this.newMessage.recipient || !this.newMessage.subject || !this.newMessage.body) {
      return;
    }

    this.sending.set(true);
    this.sendSuccess.set(false);
    this.errorMessage.set('');

    this.messageService.send({
      recipient: this.newMessage.recipient,
      subject: this.newMessage.subject,
      body: this.newMessage.body
    }).subscribe({
      next: () => {
        this.sending.set(false);
        this.sendSuccess.set(true);
        this.newMessage = { recipient: '', subject: '', body: '' };

        // Falls man sich selbst geschrieben hat, direkt im Posteingang zeigen
        this.loadInbox();
      },
      error: (err) => {
        console.error('Fehler beim Senden', err);
        this.errorMessage.set('Nachricht konnte nicht gesendet werden.');
        this.sending.set(false);
      }
    });
  }

  private extractAuthErrorMessage(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) {
      return 'Backend nicht erreichbar. Läuft es auf Port 8080?';
    }
    if (err.error?.message) {
      return err.error.message as string;
    }
    if (err.error?.details) {
      const firstDetail = Object.values(err.error.details as Record<string, string>)[0];
      if (firstDetail) {
        return firstDetail;
      }
    }
    return fallback;
  }
}
