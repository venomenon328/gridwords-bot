# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – verbindliche fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – verbindliche Seriensemantik
- [`docs/architecture.md`](docs/architecture.md) – Zielarchitektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – Inkremente und Reihenfolge
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Docker, Tests und Arbeitsweise
- [`docs/adr/`](docs/adr/) – akzeptierte Architekturentscheidungen
- [`AGENTS.md`](AGENTS.md) – Arbeitsregeln für Codex/Terra

## Aktueller Stand

Die Inkremente 0 bis 5 sind abgeschlossen. Der aktuelle Stand umfasst:

- Java 21, Spring Boot, Maven und JDA
- deterministische GridWords- und QuadWords-Textparser
- PostgreSQL-Persistenz mit Liquibase
- idempotente Spieler-, Ergebnis- und Submission-Speicherung
- gefilterten Discord-Inbound-Ablauf für einen Server, einen Channel und zwei Spieler
- kanonische GridWords-Embeds mit vollständigem Raster und eindeutig benannten Serien
- Korrekturen durch Edit derselben Bot-Nachricht
- Publication-Claims, Delivery-Fences, Lost-Message-Recovery und Duplikatbereinigung
- sichere GridWords-Ersetzung erst nach persistierter kanonischer Message-ID
- token- und lease-geschützte Quelllöschung mit Retry und Startup-Recovery
- `UNKNOWN_MESSAGE` als idempotenten Lösch-Erfolg
- getrennte Behandlung transienter und permanenter Discord-Fehler
- gezielte Bereinigung älterer, nach einer bestätigten Korrektur löschbarer Quellen
- keine kurzlebige `✅`-Reaktion auf GridWords-Quellen; QuadWords behält `✅`, Ablehnungen `⚠️`

PR #12 wurde automatisiert mit 136 Standardtests und 45 PostgreSQL-Integrationstests geprüft. Tobias hat den vollständigen Discord-/PostgreSQL-Smoke-Test am 30. Juli 2026 erfolgreich durchgeführt. Bestätigt wurden die sichere Erstveröffentlichung, Edit derselben Bot-Nachricht bei Korrekturen, die Löschfehlerbehandlung, die Bereinigung einer zuvor festhängenden supersedierten Quelle sowie der Neustart-/Recovery-Ablauf.

Als nächstes folgt Inkrement 6: der QuadWords-Bildparser. Kanonische QuadWords-Konsolidierung und sichere QuadWords-Ersetzung folgen anschließend in Inkrement 7.

## Lokale Voraussetzungen

Für den schnellen Standardbuild:

- Git
- JDK 21
- Maven 3.9 oder neuer
- eine geeignete IDE

Für lokale Persistenz-, Integrations- und Smoke-Tests steht Docker Desktop mit Docker Compose zur Verfügung und darf vorausgesetzt werden. `compose.yaml` ist die bevorzugte lokale PostgreSQL-Umgebung. Eine native PostgreSQL-Installation bleibt eine mögliche Alternative.

Der Standardbuild bleibt bewusst ohne Docker, PostgreSQL, Discord-Verbindung und Token ausführbar. Das ist eine Test- und Architekturgrenze, keine Forderung nach einer insgesamt Docker-freien Entwicklung. Maßgeblich ist [`docs/adr/0010-docker-available-local-development.md`](docs/adr/0010-docker-available-local-development.md).

## Lokale Konfiguration

Einmalig:

```powershell
Copy-Item .env.example .env
```

Der echte Discord-Token wird ausschließlich lokal in `.env` eingetragen. Er darf niemals committed, in einen Chat kopiert oder in Issue, PR, Log beziehungsweise Screenshot veröffentlicht werden.

Die Standardwerte für Compose sind:

```properties
POSTGRES_DB=gridwords
POSTGRES_USER=gridwords
POSTGRES_PASSWORD=gridwords-local
POSTGRES_PORT=5432
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=gridwords-local
```

## Schneller Standardbuild

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

Dieser Build umfasst Unit-, Parser-, Domain-, Application-, Architektur- und Discord-Adaptertests. Er öffnet keine echte Discord-Verbindung und startet keine Container.

## PostgreSQL-Integration lokal

Docker Desktop muss laufen:

```powershell
docker compose up -d postgres
docker compose ps
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Bei Änderungen an Persistenz, Liquibase, Claims, Recovery oder PostgreSQL-spezifischem Verhalten wird dieses Profil grundsätzlich auch lokal ausgeführt. GitHub Actions führt es zusätzlich verpflichtend aus.

PostgreSQL stoppen und Daten behalten:

```powershell
docker compose down
```

Datenbankvolume vollständig löschen:

```powershell
docker compose down -v
```

## Bot mit PostgreSQL und Discord starten

In `.env` mindestens:

```properties
DISCORD_BOT_TOKEN=DEIN_LOKALER_TOKEN
DISCORD_ENABLED=true
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=gridwords-local
```

Dann:

```powershell
docker compose up -d postgres
mvn "-Dspring-boot.run.profiles=database" spring-boot:run
```

Liquibase wendet beim Start dieselben Migrationen an wie in CI. Der Bot verarbeitet Ergebnisse nur im Profil `database`, wenn Discord und PostgreSQL verfügbar sind.

## Datenbankzugriff mit DBeaver

Der Compose-Container veröffentlicht PostgreSQL standardmäßig auf dem Windows-Host:

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

Relevante Tabellen sind insbesondere `player`, `submission`, `game_result` und `canonical_delivery_attempt`.

## Teststrategie

- Standardtests: ohne Netzwerk, Token, Datenbank und Container
- PostgreSQL-Integration: echtes PostgreSQL über das Maven-Profil `database-integration`
- Discord-Adaptertests: JDA-Grenze ohne echte Verbindung
- manuelle Smoke-Tests: echte Discord-Verbindung und Compose-PostgreSQL
- H2 ersetzt keine PostgreSQL-Integrationstests

## Geheimnisse

Der Discord-Bot-Token gehört ausschließlich in eine lokale, nicht versionierte Konfiguration beziehungsweise später in den Secret Store des Hosts. Automatisierte Tests und Entwicklungsaufträge dürfen keinen echten Token anfordern oder verwenden.
