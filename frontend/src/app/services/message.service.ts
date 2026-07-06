import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Message, SendMessageRequest } from '../models/message.model';

@Injectable({
  providedIn: 'root'
})
export class MessageService {

  // In einer echten App würde das über environment.ts konfiguriert,
  // hier für die Demo bewusst einfach gehalten.
  private readonly baseUrl = 'http://localhost:8080/api/messages';

  constructor(private http: HttpClient) {}

  send(request: SendMessageRequest): Observable<Message> {
    return this.http.post<Message>(this.baseUrl, request);
  }

  getInbox(recipient: string): Observable<Message[]> {
    return this.http.get<Message[]>(`${this.baseUrl}/inbox/${recipient}`);
  }

  getSent(sender: string): Observable<Message[]> {
    return this.http.get<Message[]>(`${this.baseUrl}/sent/${sender}`);
  }
}
