# Tests und Qualitätsgates

## Standardbuild

Vor Abschluss jeder Implementierungs- oder Dokumentationsänderung läuft:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

Dieser Build bleibt ohne Datenbank, Container-Runtime und Discord-Token ausführbar. Er umfasst Unit-, Domain-, Parser-, Application-, Architektur- und Discord-Adaptertests.

## PostgreSQL-Integration

Änderungen an Persistenz, Liquibase, Claims, Recovery oder PostgreSQL-spezifischem Verhalten erfordern zusätzlich:

```powershell
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Das Profil darf Docker/Testcontainers verwenden und ersetzt keine Tests durch H2. Es läuft lokal und in GitHub Actions. Für eine gezielte Clean-Install-Migrationsprüfung steht außerdem zur Verfügung:

```powershell
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

## Dokumentation und Content

Redaktionelle Ausredenquellen werden deterministisch gegen den produktiven Katalog geprüft:

```powershell
python tools/build_excuse_catalog.py --check
```

Interne Markdownlinks werden repositoryweit geprüft:

```powershell
python tools/check_markdown_links.py
```

## Testgrundsätze

- Neue fachliche Logik benötigt Happy-Path-, Fehler- und Idempotenztests.
- Zeitabhängige Tests verwenden einen festen injizierten `Clock`.
- Parseränderungen verwenden Fixtures für Erfolg, Nicht-gelöst und Fehlerfälle.
- Discord-Tests verwenden Fakes oder Mocks und keine reale Verbindung.
- Persistenz-, Claim-, Konkurrenz- und Recoveryverhalten wird gegen echtes PostgreSQL geprüft.
- Container-, Backup-, Restore- und Deploymenttests werden nur als erfolgreich gemeldet, wenn sie tatsächlich ausgeführt wurden.

Die für eine Änderung nötigen Spezialfälle ergeben sich aus den fachlichen Dokumenten unter [`../product/`](../product/overview.md) und den betroffenen Architekturmodulen.
