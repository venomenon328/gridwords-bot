# ADR 0013: Containerisierter Produktionsbetrieb auf Netcup VPS

## Status

Angenommen für Inkrement 9.

## Kontext

Die Kernversion des Bots ist nach Inkrement 8 funktional vollständig und real mit Discord sowie PostgreSQL abgenommen. Für den Dauerbetrieb wurden ein WD My Cloud EX2 Ultra und ein gemieteter Server evaluiert.

Das NAS besitzt für den vollständigen Java-/PostgreSQL-Stack zu wenig komfortable Ressourcen und keine offiziell belastbare Docker-Plattform. Ein Netcup VPS 500 G12 bietet x86-64, 4 GB RAM, ausreichend NVMe-Speicher und einen normalen Linux-/Docker-Betrieb.

Der Bot benötigt keine eingehenden Discord-Webhooks, keine öffentliche Weboberfläche und keinen öffentlich erreichbaren Datenbankport.

## Entscheidung

### Zielhost

- Netcup VPS 500 G12
- Debian 13
- Zugriff zunächst per öffentlicher IP und SSH
- keine Domain oder öffentliches HTTP/HTTPS in diesem Inkrement

### Runtime

- Docker Engine und Docker Compose
- Bot und PostgreSQL als getrennte Container
- internes Docker-Netz
- persistentes PostgreSQL-Volume
- kein veröffentlichter PostgreSQL-Port
- nur SSH ist extern erreichbar

### Image und Registry

- privates Bot-Image in GHCR
- GitHub Actions testet, baut und veröffentlicht Images
- unveränderliche Commit-Tags und explizite Release-Tags
- Produktion deployt niemals implizit `latest`

### Deployment

- Deployment erfolgt bewusst manuell durch lokalen SSH-Aufruf
- GitHub Actions erhält zunächst keinen SSH-Zugang zum Server
- vor jedem Deployment wird ein PostgreSQL-Backup erstellt
- App-Rollback erfolgt durch Deployment eines früheren Image-Tags
- Datenbankrollback erfolgt ausschließlich über Restore

### Discord und Daten

- separate Discord-Produktionsanwendung
- frische Produktionsdatenbank
- keine Übernahme lokaler Testdaten oder alter Discord-Message-IDs

### Backups

- lokal auf dem VPS
- 14 tägliche Stände
- 8 wöchentliche Stände
- Offsite-Backup folgt später

## Folgen

### Positiv

- normaler, gut dokumentierbarer Linux-/Docker-Betrieb
- reproduzierbares Deployment ohne Maven oder JDK auf dem Server
- klare Trennung von Entwicklung und Produktion
- PostgreSQL ist nicht öffentlich erreichbar
- Images sind versionierbar und App-Rollbacks einfach
- Serverzugang bleibt außerhalb von GitHub Actions

### Negativ

- lokales Backup schützt nicht gegen vollständigen Serververlust
- Deployment benötigt zunächst einen bewussten lokalen SSH-Aufruf
- private GHCR-Nutzung erfordert einen minimal berechtigten Package-Token auf dem Server
- Liquibase-Migrationen verhindern nicht automatisch jede Inkompatibilität bei App-Rollbacks

## Verworfene Alternativen

### WD My Cloud EX2 Ultra als vollständiger Host

Verworfen wegen 32-Bit-ARM-Plattform, 1 GB RAM, eingeschränktem My-Cloud-OS und fehlendem offiziell unterstütztem Dockerbetrieb.

### Automatisches Deployment aus GitHub Actions

Vorläufig verworfen, um keinen produktiven SSH-Schlüssel in GitHub Secrets hinterlegen zu müssen und unbeabsichtigte Deployments nach jedem Merge zu vermeiden.

### Öffentliche Domain und Reverse Proxy

Nicht erforderlich, weil der Bot nur ausgehende Discord-Verbindungen benötigt und keine Weboberfläche anbietet.

### Übernahme der lokalen Datenbank

Verworfen, um Testspieler, Testresultate und Message-IDs aus Testchannels nicht in Produktion zu übernehmen.

## Verbindliche Details

Die operative Ausgestaltung steht in:

- `docs/requirements/production-deployment.md`
- `docs/increments/09-production-deployment-hardening.md`
- den Betriebsdokumenten unter `docs/operations/`
