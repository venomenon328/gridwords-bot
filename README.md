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

## Lokaler Discord-Smoke-Test

Der genaue, technisch verifizierte Startweg wird im Rahmen von Issue #2 finalisiert und anschließend hier dokumentiert. Vorgesehen ist:

1. lokale, nicht versionierte Secret-Konfiguration anlegen,
2. PostgreSQL über Docker Compose starten,
3. Discord lokal aktivieren,
4. Anwendung starten,
5. erfolgreiche Gateway-Verbindung und Online-Status des Bots prüfen.

Eine bloß vorhandene `.env`-Datei wird nicht in jeder Maven-/Spring-Startvariante automatisch geladen; deshalb soll vor Abschluss von Issue #2 nicht von einem bestimmten Startbefehl ausgegangen werden.

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