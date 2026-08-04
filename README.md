# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Aktueller Stand

Der funktionale Umfang bis einschließlich **Inkrement 11** ist implementiert, gemergt, automatisiert geprüft, auf einem separaten Discord-Testserver real abgenommen und produktiv ausgerollt.

Der aktuelle Produktionsrelease ist **Version 1.2.0**. Er basiert auf dem nach `main` gemergten Inkrement 11 und aktiviert die kontextabhängigen Ausreden bewusst über die serverseitige, nicht versionierte Laufzeitkonfiguration.

Der abgenommene Stand umfasst:

- 582 Standardtests ohne externe Infrastruktur,
- eine grüne PostgreSQL-Integrationsmatrix mit echtem PostgreSQL,
- grüne GitHub-CI für Standardbuild und Datenbankintegration,
- ein erfolgreiches Produktionscontainer-, Compose-, Backup-, Restore-, Resume- und Rollback-Gate,
- reale Discord-Abnahmen für Commands, spielbezogene Teilnahme, Tagesstatus, Menüs, Reminder, Recovery, Reports und kontextabhängige Ausreden,
- einen erfolgreichen produktiven Rollout des unveränderlichen SHA-Images.

Abnahmeprotokolle:

- [`docs/operations/10.6-game-specific-participation-acceptance.md`](docs/operations/10.6-game-specific-participation-acceptance.md)
- [`docs/operations/11-contextual-excuses-acceptance.md`](docs/operations/11-contextual-excuses-acceptance.md)

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
- Claim-, Lease-, Retry-, Recovery-, Supersession- und Duplikatschutz,
- keine Discord-I/O innerhalb von Datenbanktransaktionen.

Standardzeitzone ist `Europe/Berlin`.

Die früher vorgemerkten allgemeinen Inkremente für Statistik-/Konfigurations-Commands und generische regelbasierte Kommentare werden nicht weiterverfolgt. Nach Abschluss von Inkrement 11 ist derzeit kein weiteres Produktinkrement verbindlich eingeplant; neue Erweiterungen benötigen ein eigenes priorisiertes Issue und eine neue fachliche Entscheidung.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – Seriensemantik
- [`docs/requirements/dynamic-player-model.md`](docs/requirements/dynamic-player-model.md) – dynamische Spieler und historisches Ausgangsmodell
- [`docs/requirements/game-specific-participation.md`](docs/requirements/game-specific-participation.md) – spielbezogene Teilnahme
- [`docs/requirements/excuses.md`](docs/requirements/excuses.md) – kontextabhängige Ausreden, Auswahl, Persistenz und Wiederholungsschutz
- [`docs/requirements/daily-status-reminders.md`](docs/requirements/daily-status-reminders.md) – Tagesstatus, Reminder und Cleanup
- [`docs/requirements/periodic-reports.md`](docs/requirements/periodic-reports.md) – Wochen- und Monatsberichte
- [`docs/requirements/production-deployment.md`](docs/requirements/production-deployment.md) – Produktions- und Deploymentweg
- [`docs/architecture.md`](docs/architecture.md) – Architektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – abgeschlossene Inkremente und künftige Priorisierung
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Docker und Tests
- [`docs/increments/10-periodic-reports.md`](docs/increments/10-periodic-reports.md) – Inkrement 10
- [`docs/increments/10.4-day-close-reminder-retention-cleanup.md`](docs/increments/10.4-day-close-reminder-retention-cleanup.md) – Tagesabschluss und Bereinigung
- [`docs/increments/10.5-interactive-result-details.md`](docs/increments/10.5-interactive-result-details.md) – interaktive Ergebnisdetails
- [`docs/increments/10.6-game-specific-participation.md`](docs/increments/10.6-game-specific-participation.md) – unabhängige Spielteilnahme
- [`docs/increments/11-contextual-excuses.md`](docs/increments/11-contextual-excuses.md) – abgeschlossenes Inkrement 11
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
```

Der versionierte Default der Ausreden bleibt bewusst deaktiviert. Für einen lokalen Discord-Test kann `EXCUSE_GENERATOR_CONTEXTUAL_ENABLED=true` in der nicht versionierten `.env` gesetzt werden.

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

Die normale GitHub-CI führt Standardbuild und PostgreSQL-Integration aus. Der Workflow `Container image` prüft zusätzlich das Produktionsimage sowie Compose-, Backup-, Restore-, Resume- und Rollbackpfade.

Eine Veröffentlichung nach `ghcr.io/venomenon328/gridwords-bot` erfolgt ausschließlich durch einen bewusst manuell gestarteten Workflow auf `main` mit einem neuen SemVer-Tag. Pull Requests veröffentlichen niemals ein Image.

Release Candidates werden mit einer separaten Discord-Testanwendung und einer isolierten PostgreSQL-Datenbank geprüft. Nach erfolgreicher Abnahme wird ausschließlich der freigegebene Commit über einen unveränderlichen SHA-Tag ausgerollt.

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
- vollständiger Container-, Backup-, Restore-, Resume- und Rollback-Test in GitHub Actions.

H2 ersetzt keine PostgreSQL-Integrationstests.

## Geheimnisse

Discord-Bot-Token, Datenbankpasswörter, GHCR-Tokens und private SSH-Schlüssel gehören ausschließlich in lokale beziehungsweise serverseitige, nicht versionierte Konfigurationen. Automatisierte Tests und Entwicklungsaufträge dürfen keine echten Secrets anfordern oder verwenden.
