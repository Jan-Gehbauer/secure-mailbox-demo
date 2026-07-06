import { Component } from '@angular/core';
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

  // "Login" ist bewusst simpel gehalten: nur ein Name, kein echtes
  // Auth-System. Für die Demo reicht das, um Absender/Empfänger zu simulieren.
  currentUser = '';
  isLoggedIn = false;

  inbox: Message[] = [];
  loadingInbox = false;
  errorMessage = '';

  // Formular für neue Nachricht
  newMessage = {
    recipient: '',
    subject: '',
    body: ''
  };
  sending = false;
  sendSuccess = false;

  constructor(private messageService: MessageService) {}

  login(): void {
    if (this.currentUser.trim()) {
      this.isLoggedIn = true;
      this.loadInbox();
    }
  }

  logout(): void {
    this.isLoggedIn = false;
    this.currentUser = '';
    this.inbox = [];
  }

  loadInbox(): void {
    this.loadingInbox = true;
    this.errorMessage = '';

    this.messageService.getInbox(this.currentUser).subscribe({
      next: (messages) => {
        this.inbox = messages;
        this.loadingInbox = false;
      },
      error: (err) => {
        console.error('Fehler beim Laden des Posteingangs', err);
        this.errorMessage = 'Posteingang konnte nicht geladen werden. Läuft das Backend auf Port 8080?';
        this.loadingInbox = false;
      }
    });
  }

  sendMessage(): void {
    if (!this.newMessage.recipient || !this.newMessage.subject || !this.newMessage.body) {
      return;
    }

    this.sending = true;
    this.sendSuccess = false;
    this.errorMessage = '';

    this.messageService.send({
      sender: this.currentUser,
      recipient: this.newMessage.recipient,
      subject: this.newMessage.subject,
      body: this.newMessage.body
    }).subscribe({
      next: () => {
        this.sending = false;
        this.sendSuccess = true;
        this.newMessage = { recipient: '', subject: '', body: '' };

        // Falls man sich selbst geschrieben hat, direkt im Posteingang zeigen
        this.loadInbox();
      },
      error: (err) => {
        console.error('Fehler beim Senden', err);
        this.errorMessage = 'Nachricht konnte nicht gesendet werden.';
        this.sending = false;
      }
    });
  }
}
