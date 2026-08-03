# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Aktueller Stand

Der funktionale Umfang bis einschließlich Inkrement 10 sowie der Zwischeninkremente 10.4, 10.5 und der Implementierungspakete 1–7 von Zwischeninkrement 10.6 ist umgesetzt und automatisiert geprüft. Paket 8 bündelt den abschließenden Compatibility-Audit, die reale Discord-Abnahme und das finale Produktionscontainer-Gate.

Der Quellstand trägt die Projektversion `1.0.0` und wird vor dem produktiven Release als Release Candidate auf einem separaten Discord-Testserver mit isolierter PostgreSQL-Datenbank abgenommen. Der bestehende Produktivbetrieb bleibt bis zur erfolgreichen RC-Abnahme unverändert. Ein produktiver Rollout und die manuelle Veröffentlichung nach GHCR erfolgen separat.

Der vor Paket 8 vollständig automatisiert geprüfte Funktionsstand umfasste:

- 501 Standardtests ohne externe Infrastruktur,
- 501 Surefire- und 138 Failsafe-Tests im PostgreSQL-Profil mit echtem PostgreSQL,
- grüne normale GitHub-CI für Standardbuild und Datenbankintegration.

Der vollständige Container-, Compose-, Backup-, Restore-, Resume- und Rollback-Pfad wird auf dem final abgenommenen Commit durch das Ereignis **Ready for review** ausgeführt. Bei Draft-Pull-Requests bleibt dieser zeitintensive Workflow bewusst übersprungen.

Die reale Discord-Abnahme und ihr Protokoll stehen unter [`docs/operations/10.6-game-specific-participation-acceptance.md`](docs/operations/10.6-game-specific-participation-acceptance.md).

## Fachlicher Funktionsumfang

Der Bot unterstützt insbesondere:

- deterministische GridWords- und QuadWords-Textparser,
- einen reinen QuadWords-Bildparser ohne OCR oder ML,
- vier normalisierte QuadWords-Boards sowie boardlose QuadWords-Ergebnisse,
- dynamische Spielerprofile und historisch stabile Teilnahmezeiträume je Spieltyp,
- unabhängige Teilnahme an GridWords, QuadWords, beiden oder keinem Spiel,
- Self-Service- und Admin-Slash-Commands mit optionaler Spielauswahl,
- einen globalen Reminderstatus mit spielbezogener Audience,
- kanonische Ergebnisnachrichten mit sicherer Quelllöschung,
- Korrekturen durch Edit derselben kanonischen Nachricht,
- persistente Tagesstatusnachrichten mit eindeutiger Nichtteilnahme-Darstellung,
- fünf persönliche und vier gemeinsame Serien,
- Reminder um standardmäßig 16:00 und 22:00 Uhr,
- täglichen Tagesabschluss und Channel-Bereinigung um 06:00 Uhr,
- Wochenberichte montags um 08:00 Uhr,
- Monatsberichte am Monatsersten um 08:15 Uhr,
- getrennte GridWords-/QuadWords-Nenner in Wochen- und Monatsberichten,
- interaktive, je Spiel getrennte Auswahlmenüs in Tagesstatusnachrichten,
- ausschließlich ephemere, lesende Ergebnisdetails,
- Claim-, Lease-, Retry-, Recovery-, Supersession- und Duplikatschutz,
- keine Discord-I/O innerhalb von Datenbanktransaktionen.

Standardzeitzone ist `Europe/Berlin`.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – Seriensemantik
- [`docs/requirements/dynamic-player-model.md`](docs/requirements/dynamic-player-model.md) – dynamische Spieler und historisches Ausgangsmodell
- [`docs/requirements/game-specific-participation.md`](docs/requirements/game-specific-participation.md) – verbindliche spielbezogene Teilnahme ab Zwischeninkrement 10.6
- [`docs/requirements/daily-status-reminders.md`](docs/requirements/daily-status-reminders.md) – Tagesstatus, Reminder und Cleanup
- [`docs/requirements/periodic-reports.md`](docs/requirements/periodic-reports.md) – Wochen- und Monatsberichte
- [`docs/requirements/production-deployment.md`](docs/requirements/production-deployment.md) – Produktions- und Deploymentweg
- [`docs/architecture.md`](docs/architecture.md) – Architektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – Inkremente und Status
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Docker und Tests
- [`docs/increments/10-periodic-reports.md`](docs/increments/10-periodic-reports.md) – Inkrement 10
- [`docs/increments/10.4-day-close-reminder-retention-cleanup.md`](docs/increments/10.4-day-close-reminder-retention-cleanup.md) – Tagesabschluss und Bereinigung
- [`docs/increments/10.5-interactive-result-details.md`](docs/increments/10.5-interactive-result-details.md) – interaktive Ergebnisdetails
- [`docs/increments/10.6-game-specific-participation.md`](docs/increments/10.6-game-specific-participation.md) – Entwicklungspakete für unabhängige Spielteilnahme
- [`docs/operations/10.6-game-specific-participation-acceptance.md`](docs/operations/10.6-game-specific-participation-acceptance.md) – automatisierte und reale Abschlussabnahme
- [`docs/operations/`](docs/operations/) – Betrieb, Deployment, Backup und Fehlerdiagnose
- [`docs/adr/`](docs/adr/) – Architekturentscheidungen
- [`AGENTS.md`](AGENTS.md) – Arbeitsregeln für Codex und Reviews

## Lokale Voraussetzungen

Für den Standardbuild:

- Git,
- JDK 21,
- Maven 3.9 oder neuer.

Für PostgreSQL-, Container- und Discord-Smoke-Tests:

- Docker Desktop,
- Docker Compose,
- eine separate Discord-Testanwendung mit aktiviertem Message Content Intent.

## Lokale Konfiguration

Einmalig:

```powershell
Copy-Item .env.example .env
```

Der echte Discord-Token wird ausschließlich lokal in `.env` eingetragen. Er darf niemals committed oder in Chat, Issue, PR, Log oder Screenshot veröffentlicht werden.

Wichtige lokale Standardwerte:

```properties
POSTGRES_DB=gridwords
POSTGRES_USER=gridwords
POSTGRES_PASSWORD=gridwords-local
POSTGRES_PORT=5432
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=gridwords-local

REMINDER_FIRST_TIME=16:00
REMINDER_SECOND_TIME=22:00
DAILY_CLEANUP_TIME=06:00
WEEKLY_REPORT_TIME=08:00
MONTHLY_REPORT_TIME=08:15
TIME_ZONE=Europe/Berlin
```

## Lokale Validierung

Standardbuild:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

PostgreSQL-Integration:

```powershell
docker compose up -d postgres
docker compose ps
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

PostgreSQL stoppen und Daten behalten:

```powershell
docker compose down
```

Lokales Datenbankvolume vollständig löschen:

```powershell
docker compose down -v
```

## Bot lokal mit PostgreSQL und Discord starten

In `.env` mindestens setzen:

```properties
DISCORD_BOT_TOKEN=DEIN_TESTTOKEN
DISCORD_ENABLED=true
DISCORD_GUILD_ID=DEINE_TEST_GUILD_ID
DISCORD_CHANNEL_ID=DEINE_TEST_CHANNEL_ID
ADMIN_USER_IDS=DEINE_DISCORD_USER_ID
```

Dann:

```powershell
docker compose up -d postgres
mvn "-Dspring-boot.run.profiles=database" spring-boot:run
```

Liquibase verwendet dieselben Migrationen wie die Integrationstests und der Produktionscontainer.

## Container- und Registry-Modell

Der Produktionscontainer wird aus dem versionierten `Dockerfile` gebaut und läuft als Nicht-Root-Benutzer mit Java 21.

Die normale GitHub-CI führt bei jedem relevanten Feature-Push und Pull Request Standardbuild und PostgreSQL-Integration aus. Der separate Workflow `Container image` bleibt bei Draft-Pull-Requests übersprungen und wird auf dem finalen Commit durch **Ready for review** ausgelöst. Pushes auf `main` und manuelle Runs prüfen den vollständigen Container- und Betriebsweg weiterhin.

Eine Veröffentlichung nach `ghcr.io/venomenon328/gridwords-bot` erfolgt ausschließlich durch den bewusst manuell gestarteten Workflow `Container image` auf `main` mit einem neuen SemVer-Tag. Pull Requests veröffentlichen niemals ein Image.

Release Candidates werden gegen den Discord-Testserver geprüft. Nach erfolgreicher Abnahme wird ausschließlich derselbe freigegebene Commit im separaten Produktionsschritt veröffentlicht und ausgerollt. Ein erneuter Workflow-Build ist ein neuer Build desselben Commits; eine byte-identische Promotion setzt voraus, dass das lokal getestete Image erhalten und direkt veröffentlicht wird.

## Datenbankzugriff mit DBeaver

Lokale Entwicklung:

```text
Host: localhost
Port: 5432
Database: gridwords
Username: gridwords
Password: gridwords-local
```

JDBC-URL:

```text
jdbc:postgresql://localhost:5432/gridwords
```

Die Produktionsdatenbank besitzt keinen öffentlichen Hostport. Administrativer Zugriff erfolgt ausschließlich über einen SSH-Tunnel.

## Teststrategie

- Standardtests ohne Netzwerk, Token, Datenbank oder Container,
- PostgreSQL-Integration mit echtem PostgreSQL über das Profil `database-integration`,
- vollständiger Liquibase-Neuaufbau und Upgrade eines Schemas vor Zwischeninkrement 10.6,
- Discord-Adaptertests an der JDA-Grenze ohne echte Verbindung,
- reale PNG-Fixtures für den QuadWords-Bildparser,
- feste injizierte `Clock` für zeitabhängige Tests,
- manueller Smoke-Test mit separater Discord-Testanwendung,
- vollständiger Container-, Backup-, Restore-, Resume- und Rollback-Test in GitHub Actions.

H2 ersetzt keine PostgreSQL-Integrationstests.

## Geheimnisse

Discord-Bot-Token, Datenbankpasswörter, GHCR-Tokens und private SSH-Schlüssel gehören ausschließlich in lokale beziehungsweise serverseitige, nicht versionierte Konfigurationen. Automatisierte Tests und Entwicklungsaufträge dürfen keine echten Secrets anfordern oder verwenden.
