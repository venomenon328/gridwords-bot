# Entwicklungsleitfaden

Dieser Leitfaden beschreibt die praktische Arbeitsweise für menschliche Entwicklung und Codex-/Terra-Aufträge. Fachliche Anforderungen stehen in `anforderungsspezifikation.md`, Architekturentscheidungen in `architecture.md` und `adr/`.

## 1. Arbeitsmodell

- `main` enthält nur geprüfte und mergefähige Stände.
- Jede Aufgabe wird in einem eigenen Branch umgesetzt.
- Ein GitHub-Issue beschreibt Ziel, Umfang, Nicht-Ziele und Abnahmekriterien.
- Ein Draft-PR wird früh angelegt und bleibt bis zur automatisierten und manuellen Abnahme ungemergt.
- Wesentliche Architekturentscheidungen werden als ADR dokumentiert.
- Docker Desktop und Docker Compose dürfen für Persistenz-, Integrations- und Smoke-Tests vorausgesetzt werden.
- Der schnelle Standardbuild bleibt trotzdem infrastrukturunabhängig.

Maßgeblich für die lokale Infrastruktur ist `adr/0010-docker-available-local-development.md`.

## 2. Voraussetzungen

- Git
- JDK 21
- Maven 3.9 oder neuer
- geeignete IDE
- Docker Desktop für vollständige Persistenz-, Integrations- und Smoke-Tests

```powershell
git --version
java -version
mvn --version
docker version
docker compose version
```

## 3. Secrets und lokale Konfiguration

Der Discord-Token ist ein Secret. Er wird niemals committed, in Prompts eingefügt oder in Issue, PR, Log beziehungsweise Screenshot veröffentlicht.

`.env` bleibt in `.gitignore`:

```powershell
Copy-Item .env.example .env
```

`DISCORD_BOT_TOKEN` wird ausschließlich lokal gesetzt. Betriebssystem-Umgebungsvariablen haben Vorrang vor `.env`.

## 4. Standardbuild und PostgreSQL-Integration

Schneller Standardbuild ohne Discord, PostgreSQL und Container:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

Er umfasst Unit-, Domain-, Parser-, Application-, Architektur- und Discord-Adaptertests und öffnet keine echte Discord-Verbindung.

Bei Änderungen an Persistenz, Liquibase, Claims, Recovery oder PostgreSQL-spezifischem Verhalten zusätzlich:

```powershell
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Dieses Profil verwendet echtes PostgreSQL über Testcontainers. Es läuft lokal mit Docker und zusätzlich verpflichtend in GitHub Actions. H2 ersetzt diese Tests nicht.

Der schmale Migration-Clean-Install-Gate führt zusätzlich zu den Standardtests ausschließlich die PostgreSQL-`*MigrationIT`-Tests aus. Er prüft Neuaufbau und Upgradepfade, ersetzt aber nicht die vollständige Persistenzmatrix:

```powershell
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Details stehen in [ADR 0019](../../adr/0019-migration-clean-install-gate.md).

## 5. Bevorzugte lokale PostgreSQL-Umgebung

```powershell
docker compose up -d postgres
docker compose ps
docker compose exec postgres pg_isready -U gridwords -d gridwords
```

Standardwerte:

```properties
POSTGRES_DB=gridwords
POSTGRES_USER=gridwords
POSTGRES_PASSWORD=gridwords-local
POSTGRES_PORT=5432
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=gridwords-local
```

Daten behalten:

```powershell
docker compose down
```

Daten löschen:

```powershell
docker compose down -v
```

## 6. Lokaler Anwendungsstart

Offline ohne Ergebnisverarbeitung:

```powershell
$env:SPRING_PROFILES_ACTIVE = "offline"
mvn spring-boot:run
```

Vollständiger Start mit PostgreSQL und Discord:

```powershell
docker compose up -d postgres
mvn "-Dspring-boot.run.profiles=database" spring-boot:run
```

Der Ergebnislistener wird nur im Profil `database` registriert. Liquibase verwendet dieselben Migrationen wie CI.

## 7. Datenbankzugriff

```powershell
docker compose exec postgres psql -U gridwords -d gridwords
```

DBeaver:

```text
Host: localhost
Port: 5432
Database: gridwords
Username: gridwords
Password: gridwords-local
```

Die normalisierten QuadWords-Boards stehen in:

- `quadwords_top_left_board`
- `quadwords_top_right_board`
- `quadwords_bottom_left_board`
- `quadwords_bottom_right_board`

Die verwendete Parser-Version steht in `game_result.parser_version`.

## 8. Testpyramide

### Unit-, Domain- und Parsertests

- kein Spring-Kontext, soweit möglich
- feste `Clock` für zeitabhängige Logik
- Fixture-basierte Erfolgs- und Fehlerfälle
- Konstruktorinvarianten für transportneutrale Domänentypen

### Application-Tests

- Use Cases gegen Fakes oder Mocks der Ports
- kein JDA und keine echte Datenbank
- Happy Path, fachliche Ablehnung, technischer Retry, Replay und Korrektur

### Architekturtests

- Domain hängt nicht von Spring, JDA oder JPA ab
- Application hängt nicht von Adapterpaketen ab
- JDA-Typen und Discord-Zugriffe bleiben in Adaptern beziehungsweise Wiring

### PostgreSQL-Integration

- Liquibase real ausführen
- Constraints, Round-trips, Migrationen, Korrektur, Replay und Recovery prüfen
- lokal mit Docker und zusätzlich in GitHub Actions

### Discord-Adaptertests

- keine echte Netzwerkverbindung
- JDA-Grenze mocken
- exakte IDs und Attachment-Referenzen prüfen
- Discord-, Permission-, Größen- und Netzwerkfehler fachlich übersetzen

### Manuelle Smoke-Tests

- echte Discord-Verbindung
- Compose-PostgreSQL
- Channelrechte und sichtbares Verhalten
- kein Ersatz für automatisierte Tests

## 9. QuadWords-Bildparser

### Attachment-Grenze

Der Inbound-Snapshot enthält nur eine transportneutrale Referenz aus Channel-, Message- und Attachment-ID. URLs und JDA-Typen verlassen den Discord-Adapter nicht. Die Bytes werden erst nach erfolgreichem QuadWords-Kopfparse und eindeutiger Auswahl genau eines plausiblen Bildes geladen.

Technische Downloadfehler werden getrennt von fachlichen Bildfehlern behandelt:

- transient beziehungsweise Quelle vorübergehend nicht verfügbar: `FAILED_RETRYABLE`, keine Reaktion
- Bild zu groß oder stabil fachlich ungültig: persistierte Ablehnung und `⚠️`
- erfolgreich geparst und gespeichert: `✅`

### Unterstützte Formate und Grenzen

- PNG
- JPEG
- maximal 8 MiB
- maximal 4096 × 4096 Pixel
- maximal 12.000.000 Pixel

WebP und andere Formate werden ohne zusätzliche Decoderbibliothek stabil abgelehnt.

### Fixtures

Die Bildfixtures liegen unter `fixtures/quadwords/`:

```text
fixtures/quadwords/
├── solved/
├── unsolved/
└── synthetic/
```

Zu jedem freigegebenen PNG-Fixture existiert eine gleichnamige `.expected.txt`-Datei. Das Format ist:

```text
Oben links
<Unicode-Raster>

Oben rechts
<Unicode-Raster>

Unten links
<Unicode-Raster>

Unten rechts
<Unicode-Raster>
```

Golden-Dateien werden nach visueller Prüfung des Originalbilds eingecheckt und in Tests exakt verglichen. Sie dürfen nicht blind aus einer fehlerhaften Parserausgabe regeneriert werden.

Synthetische Fixtures decken Skalierung, Ränder, JPEG, beschädigte beziehungsweise abgeschnittene Dateien, unsichere Farben und Ressourcenlimits ab.

### Parser-Regeln

- reine Java-Bildverarbeitung mit `ImageIO` und `BufferedImage`
- kein OCR, ML oder Laufzeit-LLM
- 2×2-Anordnung und genau fünf Spalten je Board
- kanonische Reihenfolge: oben links, oben rechts, unten links, unten rechts
- Flächenstichproben statt einzelner hart codierter Pixel
- unsichere Geometrie, Farbe oder Struktur wird abgelehnt
- zusätzliche aktive Zeilen oberhalb des Ergebnisses werden abgelehnt
- klar fehlende nachlaufende Zeilen früher abgeschlossener Teilboards werden als Leerzellen normalisiert
- Parser-Version: `quadwords-image-v2`

### Legacy-Kompatibilität

Bereits vorhandene Ergebnisse mit `quadwords-share-v1` dürfen ohne Boards bestehen bleiben. Eine spätere gültige bildgestützte Korrektur aktualisiert denselben fachlichen Datensatz in-place mit vier Boards und der neuen Parser-Version. Teilweise befüllte Boardspalten sind immer ungültig.

## 10. Manueller Smoke-Test für Inkrement 6

Tobias prüft nach grünen lokalen Builds und grüner CI:

1. Compose-PostgreSQL mit frischem Volume starten.
2. Bot im Profil `database` und mit lokalem Token starten.
3. Reales gelöstes QuadWords-Share posten.
4. Prüfen: Original bleibt sichtbar, genau ein `✅`, Ergebnis und vier Boards gespeichert.
5. Boards in DBeaver oder `psql` mit dem Original vergleichen; Reihenfolge und jede Zellfarbe müssen stimmen.
6. Reales `X/9` posten und neun kanonische Zeilen je Board prüfen.
7. Fremdes, beschädigtes oder nicht unterstütztes Bild posten: kein `game_result`, Original bleibt, `⚠️`.
8. Einen technischen Attachmentfehler provozieren beziehungsweise anhand automatisierter Tests akzeptieren: keine irreführende Reaktion.
9. Korrektur für denselben Spieler und Spieltag posten: weiterhin genau ein fachliches Ergebnis.
10. Neustart ohne neue Nachricht: keine Duplikate.
11. GridWords-Ersetzung unverändert prüfen.

In diesem Inkrement gibt es keine kanonische QuadWords-Nachricht und keine QuadWords-Quelllöschung.

## 11. CI- und PR-Regeln

Vor Merge:

- [ ] Issue-Umfang vollständig umgesetzt
- [ ] Standardbuild lokal grün
- [ ] PostgreSQL-Profil lokal mit Docker grün
- [ ] beide GitHub-Actions-Jobs grün
- [ ] Architekturgrenzen eingehalten
- [ ] keine Secrets oder lokalen Dateien committed
- [ ] Dokumentation aktualisiert
- [ ] manueller Smoke-Test erfolgreich

PRs bleiben bis dahin Draft und ungemergt.

## 12. Codex-/Terra-Aufträge

Ein Issue enthält Ziel, Ausgangslage, Umfang, Nicht-Ziele, Abnahmekriterien und verlangte Tests. Implementierungsaufträge sollen keine echten Tokens oder Discord-Verbindungen verwenden. Bei Persistenzaufgaben darf und soll Docker lokal eingesetzt werden.

## 13. Logging und Abhängigkeiten

Logs enthalten keine Tokens, Passwörter, vollständigen Umgebungsvariablen oder unnötige fremde Nachrichteninhalte. Technische und fachliche Fehler bleiben unterscheidbar.

Vor neuen Abhängigkeiten werden stabiles Release, Java-21-/Spring-Boot-Kompatibilität und transitive Folgen geprüft. Für den QuadWords-Parser wurde bewusst keine zusätzliche Bilddecoderbibliothek eingeführt.
