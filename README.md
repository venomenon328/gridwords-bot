# GridWords Bot

Ein einzelner Discord-Bot für GridWords und QuadWords. Er übernimmt Shares in einem konfigurierten Server/Channel, veröffentlicht kanonische Ergebnisse und verwaltet spielbezogene Teilnahme, Tagesstatus, Reminder, Serien, Ausreden, Rekorde, Achievements sowie Wochen- und Monatsberichte.

Der dokumentierte Produktionsstand ist Version **1.5.1**. Der produktiv ausgerollte Anwendungscode basiert auf Commit `832235f1ccc47900494e04fe5535e39194b70354`.

## Technik

- Java 21, Spring Boot und Maven
- JDA für Discord
- PostgreSQL 16 und Liquibase
- modularer Monolith mit Ports und Adaptern
- Docker Compose für lokale Datenbankintegration und Produktion

## Schnellstart

Voraussetzungen und lokale Konfiguration stehen in [`docs/development/setup.md`](docs/development/setup.md). Der schnelle Standardbuild benötigt weder Datenbank noch Discord-Token:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

Lokale PostgreSQL-Integration:

```powershell
docker compose up -d postgres
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Offline starten, ohne eine Discord-Verbindung zu öffnen:

```powershell
$env:SPRING_PROFILES_ACTIVE = "offline"
mvn spring-boot:run
```

Geheime Werte werden ausschließlich lokal beziehungsweise serverseitig gesetzt. `.env`, Tokens, Passwörter, private Schlüssel, Datenbankdumps und Runtime-Artefakte werden nicht committed.

## Dokumentation

[`docs/README.md`](docs/README.md) ist der zentrale Einstieg und erklärt die Dokumentautorität.

- [`docs/product/`](docs/product/overview.md): aktuelle Produktsemantik
- [`docs/architecture/`](docs/architecture/overview.md): aktuelle technische Struktur
- [`docs/adr/`](docs/adr/README.md): Architekturentscheidungen und Status
- [`docs/development/`](docs/development/setup.md): Setup, Tests und Workflow
- [`docs/operations/`](docs/operations/README.md): aktive Produktions-Runbooks
- [`docs/history/`](docs/history/README.md): nicht normative Pläne, Abnahmen und Releaseevidenz
- [`content/excuses/`](content/excuses/README.md): redaktionelle Ausredenquellen

## Wichtige Gates

```powershell
python tools/build_excuse_catalog.py --check
python tools/check_markdown_links.py
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Produktionsbetrieb, Backup, Restore und sichere Rückwege sind ausschließlich über die aktiven [`docs/operations/`](docs/operations/README.md)-Runbooks durchzuführen.
