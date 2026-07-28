# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – verbindliche fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – abgenommene Präzisierung für Aktivitäts-, Komplett-, Lösungs- und Perfektserien
- [`docs/architecture.md`](docs/architecture.md) – Zielarchitektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – geplante Inkremente und Reihenfolge
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Tests, Secrets und Codex-Workflow
- [`docs/adr/`](docs/adr/) – akzeptierte Architekturentscheidungen
- [`AGENTS.md`](AGENTS.md) – automatisch heranzuziehende Arbeitsregeln für Codex

## Aktueller Stand

Der Branch `setup/project-scaffold` enthält das technische Grundgerüst:

- Java 21
- Spring Boot
- JDA
- PostgreSQL-Unterstützung und Liquibase
- optionale Docker-Compose-Konfiguration
- externe Konfiguration
- optionale Discord-Gateway-Verbindung

Die eigentliche Ergebnisverarbeitung ist noch nicht implementiert.

Der technische Stabilisierungsauftrag aus GitHub-Issue #2 ist umgesetzt:

- Offline-Build und GitHub Actions sind grün.
- Der Start ohne Discord, PostgreSQL und Container-Runtime ist automatisiert getestet.
- Der reale Discord-Gateway-Smoke-Test wurde am 29. Juli 2026 mit lokalem Token erfolgreich **ohne Docker und ohne PostgreSQL** durchgeführt.
- Der Bot erschien im vorgesehenen Testserver online und die JDA-Verbindung wurde erfolgreich aufgebaut.

Die Seriensemantik wurde nachträglich präzisiert: Persönlich werden Aktivität, vollständige tägliche Erledigung, die Lösungsserien beider Spiele und perfekte Tage getrennt betrachtet; gemeinsam gibt es eine Komplett- und eine Perfektserie. Maßgeblich ist das verlinkte Serienmodell.

## Lokale Voraussetzungen

Für den normalen lokalen Build und die Discord-/Fachlogikentwicklung werden nur benötigt:

- Git
- JDK 21
- Maven 3.9 oder neuer
- VS Code beziehungsweise eine andere IDE

**Docker Desktop ist keine Projektvoraussetzung.**

Optional werden später benötigt:

- eine nativ installierte PostgreSQL-Instanz für einen manuellen lokalen Start mit Persistenz oder
- eine funktionierende Docker-/Compose-Umgebung als alternative Komfortlösung.

PostgreSQL-Integrationstests werden vollständig in GitHub Actions ausgeführt, sobald das Persistenzinkrement implementiert ist.

## Standardbuild ohne externe Systeme

Der verbindliche lokale Standardbuild lautet:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Er muss funktionieren ohne:

- Discord-Token,
- Discord-Netzwerkverbindung,
- PostgreSQL,
- Docker beziehungsweise eine andere Container-Runtime.

GitHub Actions führt zusätzlich die für das jeweilige Inkrement vorgesehenen vollständigen Integrationstests aus.

## Lokale Konfiguration

Die Anwendung importiert optional die lokale Datei `.env`; sie bleibt durch `.gitignore` vom Repository ausgeschlossen. Betriebssystem-Umgebungsvariablen haben Vorrang.

Unter PowerShell:

```powershell
Copy-Item .env.example .env
```

`DISCORD_BOT_TOKEN` wird ausschließlich lokal in `.env` eingetragen und niemals committed, in einen Chat kopiert oder an Codex übergeben.

## Discord-Start ohne Docker und PostgreSQL

In `.env`:

```properties
DISCORD_BOT_TOKEN=DEIN_LOKALER_TOKEN
DISCORD_ENABLED=true
```

Danach:

```powershell
$env:SPRING_PROFILES_ACTIVE = "offline"
mvn spring-boot:run
```

Das Profil `offline` deaktiviert die Datenbank-Autokonfiguration, nicht aber die über `DISCORD_ENABLED=true` ausdrücklich aktivierte Discord-Verbindung.

Bei erfolgreichem Start erscheint sinngemäß:

```text
Discord connection ready as <Bot-Name> (application user id <ID>).
```

Der Bot reagiert im aktuellen Projektstand noch nicht auf Nachrichten, da noch kein Listener implementiert ist.

## Lokaler Start mit PostgreSQL

Für spätere Persistenzentwicklung wird eine nativ installierte PostgreSQL-Instanz unterstützt. Die Zugangsdaten werden in `.env` gesetzt:

```properties
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=lokales-passwort
```

Start:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=database
```

Discord kann dabei unabhängig über `DISCORD_ENABLED=true` aktiviert werden.

## Optionale Docker-Compose-Nutzung

`compose.yaml` bleibt als optionale Alternative erhalten. Auf einem Rechner mit funktionierender Container-Runtime kann PostgreSQL weiterhin so gestartet werden:

```bash
docker compose up -d postgres
```

Docker ist jedoch weder für den Standardbuild noch für den Discord-Smoke-Test verpflichtend.

## Teststrategie

- Unit-, Parser-, Domain-, Application-, Architektur- und Discord-Adaptertests laufen lokal ohne Container.
- Der normale lokale `mvn verify` startet keine Testcontainers-Umgebung.
- PostgreSQL-Integrationstests werden später über ein eigenes Maven-Profil ausgeführt.
- GitHub Actions führt dieses Profil in einer Umgebung mit verfügbarer Container-Runtime verpflichtend aus.
- Ein vollständiger manueller Persistenzstart kann lokal gegen eine native PostgreSQL-Installation erfolgen.

Die verbindliche Entscheidung steht in [`docs/adr/0004-docker-optional-local-development.md`](docs/adr/0004-docker-optional-local-development.md).

## Geheimnisse

Der Discord-Bot-Token darf niemals in Git, einen Chat, ein Issue, einen Screenshot oder einen Codex-Prompt gelangen. Er gehört ausschließlich in eine lokale, nicht versionierte Konfiguration beziehungsweise später in den Secret Store des Hosts.
