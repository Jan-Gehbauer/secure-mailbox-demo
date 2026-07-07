import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from './message.service';
import { Message } from '../models/message.model';

describe('MessageService', () => {
  let service: MessageService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(MessageService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('send() posts to /api/messages without a sender field', () => {
    service.send({ recipient: 'bob', subject: 'Hallo', body: 'Testinhalt' }).subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/messages');
    expect(req.request.method).toBe('POST');
    // Wichtig: kein "sender"-Feld im Body - der Server leitet den Absender
    // aus dem JWT ab, der Client kann ihn nicht mehr behaupten.
    expect(req.request.body).toEqual({ recipient: 'bob', subject: 'Hallo', body: 'Testinhalt' });
    expect(Object.keys(req.request.body)).not.toContain('sender');

    req.flush({} as Message);
  });

  it('getInbox() calls /api/messages/inbox without any username in the URL', () => {
    service.getInbox().subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/messages/inbox');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getSent() calls /api/messages/sent', () => {
    service.getSent().subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/messages/sent');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getInbox() returns the messages provided by the backend', () => {
    const mockMessages: Message[] = [
      { id: 1, sender: 'alice', recipient: 'bob', subject: 'Hi', body: 'Text', createdAt: '2026-07-07T10:00:00Z' }
    ];

    let received: Message[] | undefined;
    service.getInbox().subscribe((messages) => (received = messages));

    httpMock.expectOne('http://localhost:8080/api/messages/inbox').flush(mockMessages);

    expect(received).toEqual(mockMessages);
  });
});
