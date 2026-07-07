# Authentication – Implementierungsnotizen

## Ausgangslage

Die Anwendung hatte **keine Authentifizierung**. `sender`/`recipient` waren
freie Strings, die der Client mitgab. Das führte zu einer kritischen
Sicherheitslücke:

```
GET /api/messages/inbox/alice
```

lieferte jedem, der den Benutzernamen `alice` kennt oder errät, deren
komplette (entschlüsselte) Inbox zurück – unabhängig davon, ob der
Aufrufer tatsächlich Alice ist. Das ist ein klassischer **IDOR / Broken
Object Level Authorization**-Fehler (Platz 1 der OWASP API Security
Top 10). Die AES-256-GCM-Verschlüsselung in der Datenbank hat daran
nichts geändert – sie schützt vor einem DB-Leak, nicht vor einem
API-Layer ohne Zugriffskontrolle.

## Was implementiert wurde

### Backend (Spring Boot / Spring Security)

- **`User`-Entity** mit BCrypt-Passwort-Hash (kein Klartext, kein
  eigenes Hashing-Rad neu erfunden).
- **Access-Token + Refresh-Token-Pattern**:
  - Access-Token: JWT (HS256), 15 Minuten gültig, wird im
    `Authorization: Bearer`-Header übertragen.
  - Refresh-Token: zufälliger 512-Bit-Wert, **nur als SHA-256-Hash in
    der DB gespeichert**, 7 Tage gültig, als **httpOnly + SameSite=Strict**
    Cookie ausgeliefert (für JavaScript nicht lesbar → kein XSS-Zugriff).
  - Bei jedem Refresh wird der alte Token widerrufen und ein neuer
    ausgestellt (**Rotation** – ein Token ist nur genau einmal nutzbar).
- **`SecurityConfig`**: zentrale, stateless Filterkette statt
  `@CrossOrigin`-Wildwuchs; CORS-Whitelist statt offener Origin;
  explizite JSON-Fehlerantworten für 401/403.
- **IDOR-Fix**: `MessageController` liest Sender/Empfänger nur noch aus
  dem validierten `Authentication`-Objekt. `GET /api/messages/inbox/{id}`
  gibt es nicht mehr – es heisst jetzt `GET /api/messages/inbox` und
  liefert *immer* nur die eigene Inbox des angemeldeten Nutzers.
- **`GlobalExceptionHandler`**: einheitliche JSON-Fehler statt
  Stacktraces/Whitelabel-Error-Pages.
- Passwort-Policy: Mindestlänge 12 Zeichen (NIST SP 800-63B: Länge statt
  erzwungener Zeichenklassen-Mischung), Rate-Limiting fürs Login ist
  **bewusst nicht** implementiert (siehe "Was in einem echten System
  noch fehlt" unten).

### Frontend (Angular)

- **`AuthService`**: hält den Access-Token nur im Speicher (Signal), nie
  in `localStorage`/`sessionStorage` – ein XSS-Bug könnte den Token sonst
  einfach auslesen.
- **Funktionaler HTTP-Interceptor**: hängt den Access-Token an jeden
  Request an; bekommt er ein 401, versucht er **einmal** automatisch
  einen Refresh und wiederholt den Request transparent.
- Beim App-Start wird über den (für JS unsichtbaren) Refresh-Cookie
  versucht, die Session wiederherzustellen, ohne dass sich der Nutzer
  nach einem Reload neu einloggen muss.
- Echtes Login-/Register-Formular ersetzt das bisherige
  "Namen eintippen = eingeloggt"-Provisorium.

## Lokal testen

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
# Registrieren
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"korrektpferdbatterie"}'

# Nachricht senden (Access-Token aus der vorigen Antwort einsetzen)
curl -i -X POST http://localhost:8080/api/messages \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"recipient":"alice","subject":"Hallo","body":"Testnachricht"}'

# Eigene Inbox
curl -i http://localhost:8080/api/messages/inbox \
  -H "Authorization: Bearer <accessToken>"

# Ohne Token -> 401 statt fremder Inbox
curl -i http://localhost:8080/api/messages/inbox
```

## Was in einem echten (produktiven) System noch fehlen würde

Bewusst transparent, weil das genau die Art Trade-off-Diskussion ist,
die in einem Security-fokussierten Interview zählt:

- **Rate-Limiting/Brute-Force-Schutz** auf `/api/auth/login` (z.B.
  Bucket4j oder ein API-Gateway davor).
- **E-Mail-Verifikation** vor der ersten Anmeldung (die JavaMail-Infra
  ist bereits da – liesse sich mit einem Verifikations-Token nach
  demselben Muster wie der Refresh-Token umsetzen).
- **Refresh-Token-Reuse-Detection**: aktuell wird ein bereits benutzter
  Token nur abgelehnt; in Produktion würde man bei Wiederverwendung
  *alle* Tokens des Nutzers invalidieren, weil das ein starkes Indiz für
  Diebstahl ist.
- **Secrets-Management**: JWT-Secret und Verschlüsselungs-Key liegen
  aktuell in `application.properties`/Umgebungsvariablen – gehören in
  Produktion in einen Vault/Secrets-Manager mit Rotation.
- **2FA/MFA** als optionale zusätzliche Sicherheitsstufe.
