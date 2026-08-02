# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – verbindliche fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – verbindliche Seriensemantik
- [`docs/requirements/dynamic-player-model.md`](docs/requirements/dynamic-player-model.md) – dynamische Spieler, Teilnahmezeiträume und Reminder-Opt-out
- [`docs/requirements/daily-status-reminders.md`](docs/requirements/daily-status-reminders.md) – Tagesstatus, Scheduler und persistente Reminder-Auslieferung
- [`docs/requirements/periodic-reports.md`](docs/requirements/periodic-reports.md) – verbindliche Wochen-/Monatsbericht- und Report-Delivery-Semantik
- [`docs/requirements/production-deployment.md`](docs/requirements/production-deployment.md) – Produktions-, Deployment-, Secret- und Backupweg
- [`docs/architecture.md`](docs/architecture.md) – Architektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – Inkremente und Reihenfolge
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Docker und Tests
- [`docs/increments/09-production-deployment-hardening.md`](docs/increments/09-production-deployment-hardening.md) – Umsetzung und Abnahme von Inkrement 9
- [`docs/increments/10-periodic-reports.md`](docs/increments/10-periodic-reports.md) – paketweiser Plan für Inkrement 10
- [`docs/adr/`](docs/adr/) – Architekturentscheidungen
- [`AGENTS.md`](AGENTS.md) – Arbeitsregeln für Codex/Terra

## Aktueller Stand

Die Inkremente 0 bis 9 sowie die Zwischeninkremente 7.1 und 7.2 sind abgeschlossen.

Der Bot läuft produktiv auf einem gehärteten Debian-13-VPS mit:

- privatem GHCR-Image,
- Docker Engine und Docker Compose,
- getrennten Bot- und PostgreSQL-Containern,
- internem Datenbanknetz ohne PostgreSQL- oder Actuator-Hostport,
- frischer PostgreSQL-16-Datenbank und Liquibase,
- separater Discord-Produktionsanwendung,
- unveränderlichen SHA-Image-Tags,
- Deploy-, Verify-, Backup-, Restore- und App-Rollback-Skripten,
- 14 täglichen und 8 wöchentlichen lokalen Backupgenerationen,
- öffentlich ausschließlich erreichbarem SSH.

Inkrement 9 wurde über Issue #23 und PR #24 am 1. August 2026 abgeschlossen. Der produktive RC läuft weiterhin, weil er denselben funktionalen Quellstand wie das nach dem Merge veröffentlichte `main`-Image enthält. Weitere Restart-, Reminder-, Restore- und Rollbackbeobachtungen erfolgen bei realen Betriebsanlässen statt durch künstliche Eingriffe in die laufende Produktion.

Inkrement 10 ist über Issue #25, Branch `feature/periodic-reporting` und Draft-PR #26 vorbereitet. Es ergänzt vollständig abgeleitete, idempotente Wochen- und Monatsberichte. Die Umsetzung erfolgt in kleinen, einzeln reviewbaren Terra-Paketen.

## Fachlicher Funktionsumfang

Der Projektstand umfasst:

- deterministische GridWords- und QuadWords-Textparser,
- reinen QuadWords-Bildparser ohne OCR oder ML,
- vier normalisierte QuadWords-Boards,
- dynamische Spielerprofile,
- historisch stabile Teilnahmezeiträume,
- Self-Service- und Admin-Slash-Commands für Teilnahme und Reminderstatus,
- Reminder-Opt-out bei neuer beziehungsweise erneuter Aktivierung,
- kanonische GridWords- und QuadWords-Nachrichten,
- sichere Quelllöschung erst nach persistierter Bot-Veröffentlichung,
- Korrektur durch Edit derselben kanonischen Message-ID,
- persistente Tagesstatusnachrichten,
- fünf persönliche und zwei gemeinsame Serien,
- ausschließlich heute vorläufige und historisch endgültige Seriensemantik,
- Reminder um standardmäßig 18:00 und 23:00 Uhr,
- echte ID-basierte Mentions nur für Opt-ins,
- Klartextnamen für Opt-outs,
- Claim-, Lease-, Retry-, Recovery-, Supersession- und Duplikatschutz,
- keine Discord-I/O innerhalb von Datenbanktransaktionen.

## Inkrement 10: Wochen- und Monatsberichte

Verbindlich beschlossen:

```text
Wochenbericht:  Montag 08:00 über die abgeschlossene Vorwoche
Monatsbericht:  Monatserster 08:15 über den abgeschlossenen Vormonat
Zeitzone:       Europe/Berlin
Wochen-Catch-up: 72 Stunden
Monats-Catch-up: 7 Tage
```

Die Berichte:

- verwenden alle Spieler mit mindestens einem Teilnahmetag in der Periode,
- berechnen individuelle Nenner ausschließlich aus historischen Teilnahmetagen,
- berechnen gemeinsame Kennzahlen nur an Tagen mit mindestens zwei aktiven Spielern,
- enthalten persönliche Spiel-, Tages- und Serienwerte,
- enthalten beide gemeinsamen Serien,
- verwenden ausschließlich Daten bis zum Periodenende,
- bleiben nach erfolgreicher Veröffentlichung Snapshots,
- speichern keine berechneten Statistikwerte als zweite fachliche Wahrheit,
- enthalten keine Mentions, Gewinnerlogik, Rankings oder Leaderboards,
- dürfen wegen Discord-Grenzen deterministisch auf mehrere Seiten verteilt werden.

Maßgeblich sind [`docs/requirements/periodic-reports.md`](docs/requirements/periodic-reports.md) und ADR 0014.

## QuadWords-Bildparser

Unterstützte Bildformate:

- PNG
- JPEG

WebP und andere Formate werden stabil als nicht unterstützt abgelehnt. Es wird keine zusätzliche Decoderbibliothek verwendet.

Zentrale Schutzgrenzen:

```text
Maximale Attachment-/Eingabegröße: 8 MiB
Maximale Breite:                  4096 Pixel
Maximale Höhe:                    4096 Pixel
Maximale Gesamtfläche:            12.000.000 Pixel
```

Der Parser erkennt eine 2×2-Anordnung mit genau fünf Spalten je Board. Er klassifiziert Zellen anhand mehrerer Flächenstichproben in `⬜`, `🟨` und `🟩`. Unsichere Geometrie, Farben, Struktur oder widersprüchliche aktive Zeilen führen zu einer kontrollierten Ablehnung.

## Lokale Voraussetzungen

Für den schnellen Standardbuild:

- Git
- JDK 21
- Maven 3.9 oder neuer
- eine geeignete IDE

Für Persistenz-, Integrations-, Container- und Smoke-Tests steht Docker Desktop mit Docker Compose zur Verfügung und darf vorausgesetzt werden. `compose.yaml` ist die bevorzugte lokale PostgreSQL-Umgebung. Der Standardbuild bleibt bewusst ohne Docker, PostgreSQL, Discord-Verbindung und Token ausführbar.

## Lokale Konfiguration

Einmalig:

```powershell
Copy-Item .env.example .env
```

Der echte Discord-Token wird ausschließlich lokal in `.env` eingetragen. Er darf niemals committed oder in Chat, Issue, PR, Log beziehungsweise Screenshot veröffentlicht werden.

Compose-Standardwerte:

```properties
POSTGRES_DB=gridwords
POSTGRES_USER=gridwords
POSTGRES_PASSWORD=gridwords-local
POSTGRES_PORT=5432
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=gridwords-local
```

## Lokale Validierung

Schneller Standardbuild:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

PostgreSQL-Integration mit Docker Desktop:

```powershell
docker compose up -d postgres
docker compose ps
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

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

Liquibase verwendet dieselben Migrationen wie die Integrationstests und GitHub Actions.

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

Die Produktionsdatenbank besitzt bewusst keinen öffentlichen Hostport. Ein administrativer Zugriff erfolgt ausschließlich über einen SSH-Tunnel.

Relevante Tabellen sind insbesondere `player`, `player_participation_period`, `submission`, `game_result`, `daily_status_message`, `reminder_delivery` und `canonical_delivery_attempt`. Report-Deliverytabellen werden erst in Paket 7 von Inkrement 10 ergänzt.

## Teststrategie

- Standardtests: ohne Netzwerk, Token, Datenbank und Container
- PostgreSQL-Integration: echtes PostgreSQL über `database-integration`
- Discord-Adaptertests: JDA-Grenze ohne echte Verbindung
- Bildparser-Fixtures: reale PNGs mit Golden-Ausgabe plus synthetische Fehler- und Layoutvarianten
- zeitabhängige Tests: feste injizierte `Clock`
- manuelle Smoke-Tests: echte Discord-Verbindung und Compose-PostgreSQL
- Produktionscontainer: separater vollständiger Container-/Betriebsworkflow
- H2 ersetzt keine PostgreSQL-Integrationstests

Finaler automatisierter Stand von Inkrement 9: **253 Standardtests und 76 PostgreSQL-Integrationstests** sowie der vollständige Container-, Backup-, Restore-, Upgrade- und Rollback-Vollpfad sind grün.

## Geheimnisse

Discord-Bot-Token, Datenbankpasswörter, GHCR-Tokens und private SSH-Schlüssel gehören ausschließlich in lokale beziehungsweise serverseitige, nicht versionierte Konfigurationen. Automatisierte Tests und Entwicklungsaufträge dürfen keine echten Secrets anfordern oder verwenden.
