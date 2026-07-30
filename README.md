# GridWords Bot

Discord-Bot für das tägliche gemeinsame Spielen von GridWords und QuadWords.

## Projektdokumentation

- [`docs/anforderungsspezifikation.md`](docs/anforderungsspezifikation.md) – verbindliche fachliche Grundanforderungen
- [`docs/requirements/series-model.md`](docs/requirements/series-model.md) – verbindliche Seriensemantik
- [`docs/architecture.md`](docs/architecture.md) – Architektur und Modulgrenzen
- [`docs/implementation-plan.md`](docs/implementation-plan.md) – Inkremente und Reihenfolge
- [`docs/development-guide.md`](docs/development-guide.md) – lokaler Build, Docker und Tests
- [`docs/adr/`](docs/adr/) – Architekturentscheidungen
- [`AGENTS.md`](AGENTS.md) – Arbeitsregeln für Codex/Terra

## Aktueller Stand

Die Inkremente 0 bis 5 sind abgeschlossen. Inkrement 6, der QuadWords-Bildparser, ist in Draft-PR #14 implementiert. Lokale Maven-Abnahme, finale CI-Prüfung und der echte Discord-/PostgreSQL-Smoke-Test stehen noch aus.

Der Projektstand umfasst:

- Java 21, Spring Boot, Maven, JDA, PostgreSQL und Liquibase
- deterministische GridWords- und QuadWords-Textparser
- idempotente Spieler-, Ergebnis- und Submission-Persistenz
- kanonische GridWords-Embeds und sichere GridWords-Quelllöschung
- Retry-, Claim-, Recovery-, Supersession- und Duplikatschutz
- transportneutrale Referenzen auf Discord-Anhänge
- verzögerten Download genau des ausgewählten QuadWords-Anhangs außerhalb des JDA-Event-Threads
- reinen QuadWords-Bildparser ohne OCR, ML, Netzwerk, Spring oder Datenbank
- vier normalisierte Boards in der Reihenfolge `Oben links`, `Oben rechts`, `Unten links`, `Unten rechts`
- Persistenz aller vier Boards und der Parser-Version `quadwords-image-v2`
- kontrollierte Wiederaufnahme technischer Attachment-Fehler
- Schutz gegen ein Zurückstufen parallel bereits gespeicherter Ergebnisse
- Kompatibilität mit bereits gespeicherten QuadWords-Ergebnissen aus `quadwords-share-v1`

In Inkrement 6 bleiben QuadWords-Originalnachrichten sichtbar. Ein sicher geparstes und gespeichertes Ergebnis erhält weiterhin `✅`; eine stabile fachliche Bildablehnung erhält `⚠️`. Technische Downloadfehler erhalten keine irreführende Reaktion. Kanonische QuadWords-Nachrichten und deren sichere Quelllöschung folgen erst in Inkrement 7.

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

Relevante Tabellen sind insbesondere `player`, `submission`, `game_result` und `canonical_delivery_attempt`. Die vier QuadWords-Raster stehen in den Spalten `quadwords_top_left_board`, `quadwords_top_right_board`, `quadwords_bottom_left_board` und `quadwords_bottom_right_board`.

## Teststrategie

- Standardtests: ohne Netzwerk, Token, Datenbank und Container
- PostgreSQL-Integration: echtes PostgreSQL über `database-integration`
- Discord-Adaptertests: JDA-Grenze ohne echte Verbindung
- Bildparser-Fixtures: reale PNGs mit Golden-Ausgabe plus synthetische Fehler- und Layoutvarianten
- JPEG-Kompatibilität: programmatisch erzeugtes Bild im Unit-Test
- manuelle Smoke-Tests: echte Discord-Verbindung und Compose-PostgreSQL
- H2 ersetzt keine PostgreSQL-Integrationstests

## Geheimnisse

Der Discord-Bot-Token gehört ausschließlich in eine lokale, nicht versionierte Konfiguration beziehungsweise später in den Secret Store des Hosts. Automatisierte Tests und Entwicklungsaufträge dürfen keinen echten Token anfordern oder verwenden.
