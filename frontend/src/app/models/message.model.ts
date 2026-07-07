export interface Message {
  id: number;
  sender: string;
  recipient: string;
  subject: string;
  body: string;
  createdAt: string; // ISO-String, kommt so von Jackson/Spring
}

export interface SendMessageRequest {
  recipient: string;
  subject: string;
  body: string;
}
