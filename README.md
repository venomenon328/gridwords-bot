# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – verbindliche fachliche Anforderungen
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
- PostgreSQL über Docker Compose
- Liquibase
- externe Konfiguration
- optionale Discord-Gateway-Verbindung

Die eigentliche Ergebnisverarbeitung ist noch nicht implementiert.

Der erste Stabilisierungsauftrag ist GitHub-Issue #2. Solange dieser Auftrag nicht abgeschlossen und CI nicht grün ist, ist das Grundgerüst noch nicht mergebereit. Insbesondere müssen Dependency-Versionen und der lokale `.env`-Startweg verifiziert beziehungsweise korrigiert werden.

## Lokale Voraussetzungen

- JDK 21
- Maven 3.9 oder neuer
- Docker Desktop beziehungsweise Docker Engine mit Compose
- Git

## Standardbuild

Der verbindliche Offline-Build lautet:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Er muss nach Abschluss von Issue #2 ohne Discord-Token, ohne Discord-Verbindung und ohne manuell gestartete PostgreSQL-Datenbank erfolgreich sein.

## Lokale Konfiguration und Start

Die Anwendung startet standardmäßig im Profil `offline`: weder Discord noch PostgreSQL werden dabei kontaktiert. Die lokale Datei `.env` wird von Spring als Properties-Datei importiert; sie bleibt ignoriert. Betriebssystem-Umgebungsvariablen haben Vorrang.

Unter PowerShell wird die lokale Konfiguration so angelegt:

```powershell
Copy-Item .env.example .env
```

Anschließend die Datei `.env` lokal bearbeiten und insbesondere `DISCORD_BOT_TOKEN` nur dort eintragen. Die Beispielwerte sind ausschließlich für die lokale PostgreSQL-Instanz bestimmt.

Für einen manuellen Start mit Datenbank:

```powershell
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=database
```

Discord bleibt auch im Datenbankprofil deaktiviert, bis lokal `DISCORD_ENABLED=true` gesetzt wird. Ist Discord aktiviert, aber `DISCORD_BOT_TOKEN` leer, beendet die Anwendung den Start mit einer klaren Fehlermeldung. Ein echter Gateway-Smoke-Test erfolgt erst lokal mit einem echten Token und gehört nicht zum automatisierten Build.

Beispiel für eine nur für den aktuellen PowerShell-Prozess geltende Übersteuerung:

```powershell
$env:DISCORD_ENABLED = "true"
mvn spring-boot:run -Dspring-boot.run.profiles=database
```

## PostgreSQL manuell starten und stoppen

Start:

```bash
docker compose up -d postgres
```

Status:

```bash
docker compose ps
```

Stoppen:

```bash
docker compose down
```

Vollständiges lokales Zurücksetzen einschließlich Datenbankvolume:

```bash
docker compose down -v
```

Der manuelle Datenbankstart darf für den automatisierten Standardbuild nicht erforderlich sein.

## Geheimnisse

Der Discord-Bot-Token darf niemals in Git, einen Chat, ein Issue, einen Screenshot oder einen Codex-Prompt gelangen. Er gehört ausschließlich in eine lokale, nicht versionierte Konfiguration beziehungsweise später in den Secret Store des Hosts.
