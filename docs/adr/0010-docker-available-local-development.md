# ADR 0010: Docker-verfügbare lokale Entwicklung

- **Status:** akzeptiert
- **Datum:** 29. Juli 2026
- **Entscheidungsträger:** Tobias / Projektarchitektur
- **Ersetzt:** ADR 0004 hinsichtlich der lokal verfügbaren Infrastruktur und der Vorgaben für Entwicklungsaufträge

## Kontext

ADR 0004 wurde unter der Annahme beschlossen, dass Docker Desktop auf dem primären Entwicklungsrechner nicht zuverlässig verfügbar ist. Diese technische Einschränkung besteht nicht mehr: Docker Desktop und Docker Compose laufen auf dem Entwicklungsrechner und können für lokale PostgreSQL-, Integrations- und Smoke-Tests verwendet werden.

Die bisherige Trennung zwischen einem schnellen infrastrukturlosen Standardbuild und einem separaten PostgreSQL-Integrationsprofil bleibt fachlich sinnvoll. Sie wird jedoch nicht mehr als Verbot verstanden, Docker in lokalen Entwicklungsaufträgen vorauszusetzen oder Datenbankintegration ausschließlich an GitHub Actions zu delegieren.

## Entscheidung

1. Docker Desktop und Docker Compose dürfen auf dem primären Entwicklungsrechner für lokale Persistenz-, Integrations- und Smoke-Tests vorausgesetzt werden.
2. Bei Änderungen an Persistenz, Liquibase, Recovery, Claims oder PostgreSQL-spezifischem Verhalten soll das Profil `database-integration` grundsätzlich auch lokal mit verfügbarer Container-Runtime ausgeführt werden. GitHub Actions bleibt zusätzlich verbindlich.
3. Der schnelle Standardbuild

   ```bash
   mvn --batch-mode --no-transfer-progress clean verify
   ```

   bleibt weiterhin ohne Discord, PostgreSQL und Container-Runtime ausführbar. Dies ist eine bewusste Test- und Architekturgrenze, keine Forderung nach einer insgesamt Docker-freien Umsetzung.
4. `compose.yaml` ist die bevorzugte lokale PostgreSQL-Umgebung für manuelle Starts und Smoke-Tests. Die dort festgelegte PostgreSQL-Version bleibt mit CI abgestimmt.
5. Eine native PostgreSQL-Installation darf weiterhin als Alternative unterstützt werden, ist aber nicht mehr die primär dokumentierte lokale Vorgehensweise.
6. Entwicklungsaufträge und Reviews dürfen Docker nicht pauschal ausschließen. Sie sollen Docker ausdrücklich verwenden, wenn der konkrete Test- oder Implementierungsumfang davon profitiert.
7. Automatisierte Tests öffnen weiterhin keine echte Discord-Verbindung und verwenden keinen Bot-Token. Docker-Verfügbarkeit ändert nichts an den Secret- und Netzwerkregeln.
8. H2 ersetzt weiterhin keine PostgreSQL-Integrationstests.

## Folgen

### Positiv

- PostgreSQL-Integration und Liquibase-Migrationen können vor dem Push lokal vollständig geprüft werden.
- Manuelle Discord-/PostgreSQL-Smoke-Tests verwenden eine reproduzierbare, dokumentierte Datenbankumgebung.
- Codex-/Terra-Aufträge müssen Datenbanktests nicht mehr allein wegen einer früher fehlenden Container-Runtime an CI delegieren.
- Der schnelle Standardbuild bleibt dennoch unabhängig von externer Infrastruktur.

### Negativ

- Persistenzaufgaben können lokal länger dauern, weil zusätzlich Container und Integrationstests gestartet werden.
- Docker Desktop muss für vollständige lokale Persistenzprüfungen laufen.
- Standardbuild und vollständiger Integrationsbuild bleiben zwei getrennte Befehle.

## Praktische Standardbefehle

PostgreSQL starten:

```bash
docker compose up -d postgres
docker compose ps
```

Standardbuild:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Vollständige PostgreSQL-Integration:

```bash
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

PostgreSQL stoppen, Daten behalten:

```bash
docker compose down
```

PostgreSQL einschließlich Testvolume löschen:

```bash
docker compose down -v
```

## Verhältnis zu ADR 0004

ADR 0004 bleibt als historische Begründung für den infrastrukturlosen Standardbuild relevant. Seine Aussagen, Docker Desktop dürfe lokal nicht vorausgesetzt werden oder Entwicklungsaufträge müssten grundsätzlich Docker-frei bleiben, sind durch diese Entscheidung ersetzt.