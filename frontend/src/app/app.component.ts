import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from './services/message.service';
import { Message } from './models/message.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class App {

  // Signals statt normaler Properties: lösen Change Detection zuverlässig
  // aus, auch bei asynchronen Antworten (HTTP), unabhängig vom
  // Zone.js-Verhalten. Das behebt das Problem, dass die Ansicht erst
  // nach einem manuellen Klick aktualisiert wurde.

  currentUser = signal('');
  isLoggedIn = signal(false);

  inbox = signal<Message[]>([]);
  errorMessage = signal('');

  // Formular für neue Nachricht (einfaches Objekt reicht hier,
  // da es nur über ngModel durch echte Nutzer-Events verändert wird)
  newMessage = {
    recipient: '',
    subject: '',
    body: ''
  };
  sending = signal(false);
  sendSuccess = signal(false);

  constructor(private messageService: MessageService) {}

  login(): void {
    const name = this.currentUser().trim();
    if (name) {
      this.isLoggedIn.set(true);
      this.loadInbox();
    }
  }

  logout(): void {
    this.isLoggedIn.set(false);
    this.currentUser.set('');
    this.inbox.set([]);
  }

  loadInbox(): void {
    this.errorMessage.set('');

    this.messageService.getInbox(this.currentUser()).subscribe({
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
      sender: this.currentUser(),
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
}
