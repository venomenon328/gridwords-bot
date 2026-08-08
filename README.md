# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Aktueller Stand

Der funktionale Umfang bis einschließlich **Inkrement 13** ist implementiert, nach `main` gemergt, automatisiert gehärtet und produktiv ausgerollt.

Der aktuelle Produktionsrelease ist **Version 1.4.0**. Der produktive Stand basiert auf dem Merge-Commit

`213fe15dcc59e46856ea9be7066161fdc473353a`.

**Inkrement 12 – Rekorde und Rekordmeldungen** wurde mit Version **1.3.0** produktiv eingeführt.  
**Inkrement 13 – Achievements** wurde mit Version **1.4.0** produktiv eingeführt.

Für Inkrement 13 wurden vor dem Rollout Standardbuild, PostgreSQL-Integration, Migration/Upgrade sowie das vollständige Produktionscontainer-, Compose-, Backup-, Restore-, Resume- und Rollback-Gate erfolgreich ausgeführt. Der reale lokale Discord-Smoke deckte Startup, Migration 024, historischen Bootstrap, Introduction, `/achievements` und Restart-Idempotenz ab. Der anschließende Produktiv-Smoke/Canary wurde erfolgreich abgeschlossen.

Abnahmeprotokolle:

- [`docs/operations/10.6-game-specific-participation-acceptance.md`](docs/operations/10.6-game-specific-participation-acceptance.md)
- [`docs/operations/11-contextual-excuses-acceptance.md`](docs/operations/11-contextual-excuses-acceptance.md)
- [`docs/operations/12-records-acceptance.md`](docs/operations/12-records-acceptance.md)
- [`docs/operations/13-achievements-acceptance.md`](docs/operations/13-achievements-acceptance.md)
- [`docs/operations/13-achievements-live-canary.md`](docs/operations/13-achievements-live-canary.md)

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
- optionale kontextabhängige Ausreden bei klar definierten auffälligen Ergebnissen,
- jeweils drei private Ausredenvorschläge, einen einmaligen Stilwechsel, Auswahl oder Verzicht,
- Übernahme ausschließlich des gewählten Textes in dieselbe kanonische Ergebnisnachricht,
- Ablauf, Cooldown und Wiederholungsschutz für Ausreden,
- historische persönliche, serverweite individuelle und gemeinsame Rekorde für Ergebnisse und Serien,
- aggregierte, korrigierbare Rekordmeldungen mit Edit/Delete-Reconciliation,
- den ephemeren, strikt lesenden `/records`-Command mit `game`, `category` und optionaler Admin-Fremdansicht,
- persistente Record-Bootstrap-, Live-, Day-Close-, Delivery-, Retry- und Restart-Recovery,
- einen kuratierten Katalog mit **60 Achievements** für Erfahrung, Zuverlässigkeit, Serien, Leistung und besondere Spielsituationen,
- rückwirkende Achievement-Rekonstruktion aus der kanonischen Historie,
- aggregierte öffentliche Achievement-Freischaltungen,
- korrekturfähigen Achievement-State mit Invalidierung und Reaktivierung ohne öffentliche Aberkennungsnachricht,
- den ephemeren `/achievements`-Command für aktive Achievements mit Self-/Other- und Game-Filter,
- den ephemeren, self-only `/achievement-list`-Command mit allen 60 Definitionen und binärem `✅`/`❌`-Status ohne quantitative Fortschrittsanzeige,
- Claim-, Lease-, Retry-, Recovery-, Supersession- und Duplikatschutz,
- keine Discord-I/O innerhalb von Datenbanktransaktionen.

Standardzeitzone ist `Europe/Berlin`.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – Seriensemantik
- [`docs/requirements/dynamic-player-model.md`](docs/requirements/dynamic-player-model.md) – dynamische Spieler und historisches Ausgangsmodell
- [`docs/requirements/game-specific-participation.md`](docs/requirements/game-specific-participation.md) – spielbezogene Teilnahme
- [`docs/requirements/excuses.md`](docs/requirements/excuses.md) – kontextabhängige Ausreden
- [`docs/requirements/records.md`](docs/requirements/records.md) – Rekordsemantik
- [`docs/requirements/achievements.md`](docs/requirements/achievements.md) – vollständiger Achievement-Katalog und Fachsemantik
- [`docs/requirements/achievement-list.md`](docs/requirements/achievement-list.md) – vollständige persönliche Achievement-Liste
- [`docs/requirements/daily-status-reminders.md`](docs/requirements/daily-status-reminders.md) – Tagesstatus, Reminder und Cleanup
- [`docs/requirements/periodic-reports.md`](docs/requirements/periodic-reports.md) – Wochen- und Monatsberichte
- [`docs/requirements/production-deployment.md`](docs/requirements/production-deployment.md) – Produktions- und Deploymentweg
- [`docs/architecture.md`](docs/architecture.md) – Architektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – abgeschlossene Produktinkremente
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Docker und Tests
- [`docs/increments/11-contextual-excuses.md`](docs/increments/11-contextual-excuses.md) – Inkrement 11
- [`docs/increments/12-records.md`](docs/increments/12-records.md) – Inkrement 12
- [`docs/increments/13-achievements.md`](docs/increments/13-achievements.md) – Inkrement 13
- [`docs/operations/12-records-operations.md`](docs/operations/12-records-operations.md) – Record-Betrieb und Recovery
- [`docs/operations/13-achievements-operations.md`](docs/operations/13-achievements-operations.md) – Achievement-Betrieb und Recovery
- [`docs/operations/13-achievements-acceptance.md`](docs/operations/13-achievements-acceptance.md) – technische und reale Abnahme von Inkrement 13
- [`docs/operations/13-achievements-live-canary.md`](docs/operations/13-achievements-live-canary.md) – dokumentierter Produktiv-Canary von Inkrement 13
- [`docs/operations/`](docs/operations/) – Abnahmen, Betrieb, Deployment, Backup und Fehlerdiagnose
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

EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=false
EXCUSE_OFFER_LIFETIME=PT15M
EXCUSE_EXPIRATION_PAGE_SIZE=25
EXCUSE_EXPIRATION_MAX_PAGES=4

RECORD_BOOTSTRAP_POLL_DELAY=PT1M
RECORD_BOOTSTRAP_LEASE_DURATION=PT2M
RECORD_BOOTSTRAP_RETRY_BACKOFF=PT1M

RECORD_LIVE_EVALUATION_ENABLED=true
RECORD_LIVE_EVALUATION_POLL_DELAY=PT10S
RECORD_LIVE_EVALUATION_LEASE_DURATION=PT2M
RECORD_LIVE_EVALUATION_HEARTBEAT_INTERVAL=PT30S
RECORD_LIVE_EVALUATION_INITIAL_RETRY_BACKOFF=PT10S
RECORD_LIVE_EVALUATION_MAX_RETRY_BACKOFF=PT5M

RECORD_PUBLIC_ANNOUNCEMENTS_ENABLED=false
RECORD_ANNOUNCEMENT_POLL_DELAY=PT10S
RECORD_ANNOUNCEMENT_LEASE_DURATION=PT2M
RECORD_ANNOUNCEMENT_HEARTBEAT_INTERVAL=PT30S
RECORD_ANNOUNCEMENT_INITIAL_RETRY_BACKOFF=PT10S
RECORD_ANNOUNCEMENT_MAX_RETRY_BACKOFF=PT5M
```

Der versionierte Default der Ausreden bleibt bewusst deaktiviert. Für einen lokalen Discord-Test kann `EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=true` in der nicht versionierten `.env` gesetzt werden.

Achievement-Bootstrap und Achievement-Delivery verwenden persistente PostgreSQL-Zustände und starten im `database`-Profil automatisch. Die Schemaerweiterung liegt in Liquibase-Migration 024. Für die Achievement-Commands oder den Bootstrap ist kein separater History-Rebuild-Command erforderlich.

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

Migration/Upgrade:

```powershell
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
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
EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=true
```

Dann:

```powershell
docker compose up -d postgres
mvn "-Dspring-boot.run.profiles=database" spring-boot:run
```

Liquibase verwendet dieselben Migrationen wie die Integrationstests und der Produktionscontainer.

## Container- und Registry-Modell

Der Produktionscontainer wird aus dem versionierten `Dockerfile` gebaut und läuft als Nicht-Root-Benutzer mit Java 21.

Die normale GitHub-CI führt Standardbuild, PostgreSQL-Integration und das Migration-/Upgrade-Gate aus. Der Workflow `Container image` prüft zusätzlich das Produktionsimage sowie Compose-, Backup-, Restore-, Resume- und Rollbackpfade.

Eine Veröffentlichung nach `ghcr.io/venomenon328/gridwords-bot` erfolgt ausschließlich durch einen bewusst manuell gestarteten Workflow auf `main` mit einem neuen SemVer-Tag. Pull Requests veröffentlichen niemals ein Image.

Produktionsdeployments verwenden ausschließlich das freigegebene unveränderliche SHA-Image. Vor dem App-Update wird ein validiertes PostgreSQL-Backup erzeugt; der vorhandene Deploymentpfad führt bei fehlgeschlagenem Healthcheck einen verifizierten App-Rollback auf das vorherige Image aus.

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
- vollständiger Liquibase-Neuaufbau und Upgrade bestehender Schemas,
- Discord-Adaptertests an der JDA-Grenze ohne echte Verbindung,
- reale PNG-Fixtures für den QuadWords-Bildparser,
- feste injizierte `Clock` für zeitabhängige Tests,
- manueller Smoke-Test mit separater Discord-Testanwendung,
- kontrollierter Produktiv-Canary für risikoreiche externe Discord-Pfade,
- vollständiger Container-, Backup-, Restore-, Resume- und Rollback-Test in GitHub Actions.

H2 ersetzt keine PostgreSQL-Integrationstests.

## Geheimnisse

Discord-Bot-Token, Datenbankpasswörter, GHCR-Tokens und private SSH-Schlüssel gehören ausschließlich in lokale beziehungsweise serverseitige, nicht versionierte Konfigurationen. Automatisierte Tests und Entwicklungsaufträge dürfen keine echten Secrets anfordern oder verwenden.
