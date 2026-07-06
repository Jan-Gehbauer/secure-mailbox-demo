# Kubernetes-Deployment (lokal, Minikube oder kind)

Diese Manifeste sind für **lokales Testen** gedacht (Minikube oder kind),
nicht für ein Cloud-Deployment. Der Unterschied zu einer echten
Produktivumgebung wird unten kurz erklärt.

## Voraussetzungen

- Minikube ODER kind installiert und gestartet
- `kubectl` installiert und auf den lokalen Cluster konfiguriert

## Schritt 1: Docker-Images bauen

```bash
docker build -t secure-mailbox-backend:latest ./backend
docker build -t secure-mailbox-frontend:latest ./frontend
```

## Schritt 2: Images ins lokale Cluster laden

Kubernetes zieht Images normalerweise aus einer Registry (Docker Hub etc.).
Für lokale Images ohne Registry müssen sie explizit ins Cluster geladen werden:

**Minikube:**
```bash
minikube image load secure-mailbox-backend:latest
minikube image load secure-mailbox-frontend:latest
```

**kind:**
```bash
kind load docker-image secure-mailbox-backend:latest
kind load docker-image secure-mailbox-frontend:latest
```

## Schritt 3: Manifeste anwenden

Reihenfolge ist wichtig: Secret zuerst, dann die Komponenten, die davon abhängen.

```bash
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/mailhog.yaml
kubectl apply -f k8s/backend.yaml
kubectl apply -f k8s/frontend.yaml
```

## Schritt 4: Status prüfen

```bash
kubectl get pods
```

Alle Pods sollten nach kurzer Zeit `Running` und `1/1 Ready` zeigen.
Falls ein Pod hängt: `kubectl describe pod <name>` oder `kubectl logs <name>`.

## Schritt 5: Zugriff im Browser

Das Frontend spricht das Backend aktuell über eine feste URL
(`http://localhost:8080`) an - das ist eine bewusste Vereinfachung für die
Demo (in einer echten Umgebung würde man das über Umgebungsvariablen beim
Frontend-Build oder einen API-Gateway/Ingress lösen). Damit das im
Cluster trotzdem funktioniert, brauchen wir zwei Port-Forwards:

```bash
kubectl port-forward svc/backend 8080:8080
```

In einem zweiten Terminal:

```bash
kubectl port-forward svc/frontend 4200:80
```

Danach im Browser: **http://localhost:4200**

(Alternativ ist das Frontend auch über den NodePort erreichbar:
`minikube service frontend --url` gibt dir die URL dafür - dann müsstest
du aber auch das Backend uber einen NodePort statt Port-Forward
freigeben, sonst passt die hartcodierte Frontend-URL nicht mehr.)

## Aufräumen

```bash
kubectl delete -f k8s/
```

## Was hier bewusst vereinfacht ist (gut zu wissen fürs Gespräch)

- **Kein Ingress/API-Gateway**: In einer echten Umgebung würde man Frontend
  und Backend über einen Ingress mit einem gemeinsamen Hostnamen ausliefern,
  statt mit fest codierten `localhost`-URLs zu arbeiten.
- **Secret im Klartext im Repo**: `stringData` in `secret.yaml` ist nur
  base64-kodiert, nicht verschlüsselt. In der Praxis: Vault, Sealed Secrets,
  oder die Secret-Verwaltung der jeweiligen Cloud-Plattform.
- **Kein Autoscaling (HPA)**: `replicas: 2` ist fest codiert. Mit einem
  Horizontal Pod Autoscaler würde die Anzahl der Pods automatisch an die
  Last angepasst - genau das, was in der Stellenanzeige mit "Skalierung zu
  einem massentauglichen, kommerziellen Service" gemeint sein dürfte.
- **Postgres als einzelne Instanz ohne Replikation**: für eine Demo okay,
  in Produktion bräuchte man Replikation/Backups (z.B. über einen
  managed Postgres-Dienst statt Self-Hosting im Cluster).
