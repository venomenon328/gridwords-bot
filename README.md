# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – verbindliche fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – verbindliche Seriensemantik
- [`docs/requirements/dynamic-player-model.md`](docs/requirements/dynamic-player-model.md) – dynamische Spieler, Teilnahmezeiträume und Reminder-Opt-in
- [`docs/requirements/daily-status-reminders.md`](docs/requirements/daily-status-reminders.md) – Tagesstatus, Scheduler und persistente Reminder-Auslieferung
- [`docs/architecture.md`](docs/architecture.md) – Architektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – Inkremente und Reihenfolge
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Docker und Tests
- [`docs/adr/`](docs/adr/) – Architekturentscheidungen
- [`AGENTS.md`](AGENTS.md) – Arbeitsregeln für Codex/Terra

## Aktueller Stand

Die Inkremente 0 bis 7 sowie die Zwischeninkremente 7.1 und 7.2 sind abgeschlossen. Das dynamische Spielermodell, historisch stabile Teilnahmezeiträume sowie Teilnahme- und Reminder-Commands wurden automatisiert und am 30. Juli 2026 in einem realen Discord-/PostgreSQL-Smoke-Test mit mindestens drei Nutzern abgenommen.

Inkrement 8 ist in Draft-PR #22 vollständig implementiert und automatisiert abgenommen. Es umfasst persistente Tagesstatusnachrichten, alle persönlichen und gemeinsamen Serien, historische Finalisierung, DST-sicheres Scheduling sowie idempotente Reminder um 18:00 und 23:00 Uhr. Ausschließlich Tobias' realer Discord-/PostgreSQL-Smoke-Test ist noch offen; der PR bleibt bis dahin Draft und ungemergt.

Der Projektstand umfasst:

- Java 21, Spring Boot, Maven, JDA, PostgreSQL und Liquibase
- deterministische GridWords- und QuadWords-Textparser
- dynamische Spielerprofile mit serverbezogenem Anzeigenamen und extern bestimmtem Administratorstatus
- historisch stabile, nicht überlappende Teilnahmezeiträume
- Self-Service- und Admin-Slash-Commands für Teilnahme und Reminder-Opt-in
- transportneutrale Reminder-Kandidaten mit konkret fehlenden Spielen
- persistente Tagesstatusnachrichten mit allen fünf persönlichen und beiden gemeinsamen Serien
- endgültige historische Serienprojektion und ausschließlich heute vorläufige Semantik
- idempotente Reminder-Delivery mit No-op, Supersession, Ablauf, Backoff und Recovery
- stabile Discord-Delivery-Schlüssel, Status-Fingerprints und kontrollierter Ersatz extern gelöschter Nachrichten
- idempotente Spieler-, Ergebnis- und Submission-Persistenz
- kanonische GridWords- und QuadWords-Embeds mit sicherer Quelllöschung nach persistierter Veröffentlichung
- kompaktes QuadWords-2×2-Raster und Monospace-Codeblöcke für beide Spiele
- Retry-, Claim-, Recovery-, Supersession- und Duplikatschutz
- transportneutrale Referenzen auf Discord-Anhänge
- verzögerten Download genau des ausgewählten QuadWords-Anhangs außerhalb des JDA-Event-Threads
- Download der originalen signierten Discord-CDN-Datei statt einer möglicherweise transformierten Medienproxy-Variante
- reinen QuadWords-Bildparser ohne OCR, ML, Netzwerk, Spring oder Datenbank
- vier normalisierte Boards in der Reihenfolge `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
- Persistenz aller vier Boards und der Parser-Version `quadwords-image-v2`
- kontrollierte Wiederaufnahme technischer Attachment-Fehler
- Schutz gegen ein Zurückstufen parallel bereits gespeicherter Ergebnisse
- Korrekturen beider Spieltypen durch Edit derselben kanonischen Bot-Message-ID
- spieltypbezogene Publication-Keys sowie gemeinsame Claims, Delivery-Fence, Retry, Recovery, Supersession und Duplikatbereinigung
- PublicationContext für persönliche und gemeinsame Komplett-/Perfektübergänge über die je Spieltag aktive Teilnehmermenge
- sichere Lesbarkeit bereits gespeicherter QuadWords-Ergebnisse aus `quadwords-share-v1`, ohne Publish, Delete-Handoff oder Refresh-Schleife bei fehlenden Boards
- permanente Löschfehler ohne Scheduler- oder Hot-Loop sowie kontrollierte Wiederaufnahme bei Neustart oder nach einer späteren bestätigten Veröffentlichung desselben Ergebnisses

Ein sicher geparstes und gespeichertes QuadWords-Ergebnis wird ohne zusätzliche `✅`-Reaktion als genau eine kanonische Bot-Nachricht veröffentlicht; erst nach persistierter Veröffentlichung wird die menschliche Quelle gelöscht. Eine stabile fachliche Bildablehnung bleibt mit `⚠️` sichtbar. Technische Downloadfehler sowie boardlose Legacy-Ergebnisse erhalten weder ein Erfolgssignal noch einen Discord-Publish-/Delete-Aufruf.

## QuadWords-Bildparser

Unterstützte Bildformate:

- PNG
- JPEG

WebP und andere Formate werden stabil als nicht unterstützt abgelehnt; es wird keine zusätzliche Decoderbibliothek verwendet.

Zentrale Schutzgrenzen:

```text
Maximale Attachment-/Eingabegröße: 8 MiB
Maximale Breite:                  4096 Pixel
Maximale Höhe:                    4096 Pixel
Maximale Gesamtfläche:            12.000.000 Pixel
```

Der Parser erkennt eine 2×2-Anordnung mit genau fünf Spalten je Board. Er klassifiziert Zellen anhand mehrerer Flächenstichproben in `⬜`, `🟨` und `🟩`. Unsichere Geometrie, Farben, Struktur oder widersprüchliche aktive Zeilen führen zu einer kontrollierten Ablehnung. Klar fehlende nachlaufende Zeilen eines bereits früher abgeschlossenen Teilboards werden als kanonische Leerzellen normalisiert; mindestens ein Teilboard muss die in der Kopfzeile gemeldete Versuchszahl erreichen.

Die freigegebenen realen PNG-Fixtures besitzen jeweils eine eingecheckte erwartete kanonische Ausgabe. Zusätzlich decken synthetische Dateien Skalierung, Ränder, beschädigte und abgeschnittene Bilder, unsichere Farben sowie Ressourcenlimits ab. JPEG wird programmatisch in einem Unit-Test erzeugt und geprüft.

## Lokale Voraussetzungen

Für den schnellen Standardbuild:

- Git
- JDK 21
- Maven 3.9 oder neuer
- eine geeignete IDE

Für Persistenz-, Integrations- und Smoke-Tests steht Docker Desktop mit Docker Compose zur Verfügung und darf vorausgesetzt werden. `compose.yaml` ist die bevorzugte lokale PostgreSQL-Umgebung. Der Standardbuild bleibt bewusst ohne Docker, PostgreSQL, Discord-Verbindung und Token ausführbar. Maßgeblich ist [`docs/adr/0010-docker-available-local-development.md`](docs/adr/0010-docker-available-local-development.md).

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

Relevante Tabellen sind insbesondere `player`, `player_participation_period`, `submission`, `game_result` und `canonical_delivery_attempt`. Die vier QuadWords-Raster stehen in den Spalten `quadwords_top_left_board`, `quadwords_top_right_board`, `quadwords_bottom_left_board` und `quadwords_bottom_right_board`. `player.reminder_opt_in` ist unabhängig vom aktuellen Teilnahmezustand.

## Teststrategie

- Standardtests: ohne Netzwerk, Token, Datenbank und Container
- PostgreSQL-Integration: echtes PostgreSQL über `database-integration`
- Discord-Adaptertests: JDA-Grenze ohne echte Verbindung
- Bildparser-Fixtures: reale PNGs mit Golden-Ausgabe plus synthetische Fehler- und Layoutvarianten
- JPEG-Kompatibilität: programmatisch erzeugtes Bild im Unit-Test
- manuelle Smoke-Tests: echte Discord-Verbindung und Compose-PostgreSQL
- H2 ersetzt keine PostgreSQL-Integrationstests

Automatisierter Stand von Inkrement 8: 246 Standardtests und 73 PostgreSQL-Integrationstests sind lokal grün. Abgedeckt sind alle 24 automatisierbaren Kriterien aus Issue #21, insbesondere historische Finalisierung, Vortagsnachträge, Status-Create/Edit/Recreate, JDA-Mention-Sicherheit, beide Reminderstufen, Startup/DST, Konkurrenz, Retry/Permanentfehler, Liquibase und vollständige Ergebnisregression. Weder automatisierte Tests noch der Build verwenden einen Discord-Token. Ausschließlich Tobias' realer Discord-/PostgreSQL-Smoke-Test für Inkrement 8 bleibt offen.

## Geheimnisse

Der Discord-Bot-Token gehört ausschließlich in eine lokale, nicht versionierte Konfiguration beziehungsweise später in den Secret Store des Hosts. Automatisierte Tests und Entwicklungsaufträge dürfen keinen echten Token anfordern oder verwenden.
