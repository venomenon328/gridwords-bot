# Entwicklungsleitfaden

Dieser Leitfaden beschreibt die praktische Arbeitsweise für menschliche Entwicklung und Codex-/Terra-Aufträge. Fachliche Anforderungen stehen in `anforderungsspezifikation.md`, Architekturentscheidungen in `architecture.md` und `adr/`.

## 1. Arbeitsmodell

- `main` enthält nur geprüfte und mergefähige Stände.
- Jede Aufgabe wird in einem eigenen Branch umgesetzt.
- Ein GitHub-Issue beschreibt Ziel, Umfang, Nicht-Ziele und Abnahmekriterien.
- Ein Draft-PR wird früh angelegt und bleibt bis zur automatisierten sowie manuellen Abnahme ungemergt.
- Architekturänderungen werden vor der Implementierung als ADR dokumentiert.
- Docker Desktop und Docker Compose stehen lokal zur Verfügung und dürfen für Persistenz-, Integrations- und Smoke-Tests vorausgesetzt werden.
- Der schnelle Standardbuild bleibt trotzdem infrastrukturunabhängig.

Maßgeblich für die lokale Infrastruktur ist `adr/0010-docker-available-local-development.md`. ADR 0004 dokumentiert nur noch den historischen Ausgangspunkt.

## 2. Voraussetzungen

Verbindlich:

- Git
- JDK 21
- Maven 3.9 oder neuer
- VS Code mit Codex/Terra oder eine andere geeignete IDE
- Docker Desktop für vollständige lokale Persistenz-, Integrations- und Smoke-Tests

Prüfen:

```powershell
git --version
java -version
mvn --version
docker version
docker compose version
```

## 3. Secrets und lokale Konfiguration

Der Discord-Token ist ein Secret.

Verbindliche Regeln:

- niemals committen,
- niemals in Codex-/Terra-Prompts einfügen,
- niemals in Issue, PR, Log oder Screenshot veröffentlichen,
- ausschließlich lokal beziehungsweise später im Secret Store des Hosts setzen.

`.env` bleibt in `.gitignore`. Die Anwendung importiert sie über `spring.config.import=optional:file:.env[.properties]`. Betriebssystem-Umgebungsvariablen haben Vorrang.

Einmalig unter PowerShell:

```powershell
Copy-Item .env.example .env
```

`DISCORD_BOT_TOKEN` wird ausschließlich in diese lokale Datei eingetragen.

## 4. Schneller Standardbuild

Der Standardbuild muss ohne Discord, PostgreSQL und Container-Runtime funktionieren:

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

Er umfasst Unit-, Parser-, Domain-, Application-, Architektur- und infrastrukturlosen Discord-Adaptertests. Der Build öffnet keine echte Discord-Verbindung und startet keine Testcontainers-Umgebung.

Diese Eigenschaft ist eine bewusste Test- und Architekturgrenze. Sie verbietet nicht, Docker in der Implementierung oder bei vollständigen lokalen Prüfungen zu verwenden.

## 5. PostgreSQL-Integration lokal

Bei Änderungen an Persistenz, Liquibase, Claims, Recovery oder PostgreSQL-spezifischem Verhalten ist zusätzlich lokal auszuführen:

```powershell
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Das Profil verwendet echtes PostgreSQL und darf Testcontainers beziehungsweise eine Container-Runtime verwenden. Es wird außerdem verpflichtend in GitHub Actions ausgeführt und darf dort nicht unbemerkt übersprungen werden.

H2 ersetzt diese Tests nicht.

## 6. Bevorzugte lokale PostgreSQL-Umgebung

Docker Desktop muss laufen.

PostgreSQL starten:

```powershell
docker compose up -d postgres
docker compose ps
docker compose exec postgres pg_isready -U gridwords -d gridwords
```

Standardwerte aus `.env.example` und `compose.yaml`:

```properties
POSTGRES_DB=gridwords
POSTGRES_USER=gridwords
POSTGRES_PASSWORD=gridwords-local
POSTGRES_PORT=5432
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=gridwords-local
```

PostgreSQL stoppen, Daten behalten:

```powershell
docker compose down
```

PostgreSQL einschließlich Volume löschen:

```powershell
docker compose down -v
```

`docker compose down -v` löscht alle lokalen Testdaten.

Eine native PostgreSQL-Installation darf weiterhin als Alternative unterstützt werden, ist aber nicht die primär dokumentierte Vorgehensweise.

## 7. Lokaler Anwendungsstart

### 7.1 Vollständig offline

```powershell
$env:SPRING_PROFILES_ACTIVE = "offline"
mvn spring-boot:run
```

Solange `DISCORD_ENABLED=false` ist, werden weder Discord noch PostgreSQL kontaktiert.

### 7.2 Discord-Gateway ohne Ergebnisverarbeitung

In `.env`:

```properties
DISCORD_BOT_TOKEN=DEIN_LOKALER_TOKEN
DISCORD_ENABLED=true
```

Start:

```powershell
$env:SPRING_PROFILES_ACTIVE = "offline"
mvn spring-boot:run
```

Das Profil `offline` deaktiviert die Datenbank-Autokonfiguration. Der Gateway kann trotzdem aufgebaut werden; ein Ergebnislistener wird dort nicht registriert.

### 7.3 Vollständiger Start mit PostgreSQL und Discord

```powershell
docker compose up -d postgres
mvn "-Dspring-boot.run.profiles=database" spring-boot:run
```

Liquibase wendet dabei dieselben Migrationen an wie in CI. Discord bleibt über `DISCORD_ENABLED` steuerbar.

## 8. Datenbankzugriff

Direkt über `psql`:

```powershell
docker compose exec postgres psql -U gridwords -d gridwords
```

DBeaver kann über den veröffentlichten Host-Port zugreifen:

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

Relevante Tabellen:

- `player`
- `submission`
- `game_result`
- `canonical_delivery_attempt`

## 9. Testpyramide

### Unit-, Domain- und Parsertests

- kein Spring-Kontext, soweit möglich
- feste `Clock` für zeitabhängige Logik
- Fixture-basierte Parserfälle für Erfolg, nicht gelöst und Fehler
- getrennte Tests für alle sieben Serien

### Application-Tests

- Use Cases gegen Fakes oder Mocks der Ports
- kein JDA
- keine echte Datenbank
- Happy Path, Fehlerpfade, Retry und Idempotenz

### Architekturtests

- Domain hängt nicht von Spring, JDA oder JPA ab
- Application hängt nicht von Adapterpaketen ab
- JDA-Typen bleiben im Discord-Adapter beziehungsweise Wiring

### PostgreSQL-Integration

- echtes PostgreSQL
- Liquibase real ausführen
- Constraints, Claims, Konflikte und Recovery prüfen
- lokal mit Docker und zusätzlich in GitHub Actions

### Discord-Adaptertests

- keine echte Netzwerkverbindung
- JDA-Grenze mocken
- exakte IDs, Fehlerklassifikation und DTO-Übersetzung prüfen

### Manuelle Smoke-Tests

- echte Discord-Verbindung
- Compose-PostgreSQL
- Channelrechte und sichtbares Verhalten
- kein Ersatz für automatisierte Tests

## 10. CI-Strategie

Der Standardjob führt aus:

```powershell
mvn --batch-mode --no-transfer-progress verify
```

Der Datenbankjob führt aus:

```powershell
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Anforderungen:

- Datenbankintegration muss in den Logs eindeutig ausgeführt worden sein.
- Fehlender Containerzugriff darf nicht als erfolgreicher Skip durchgehen.
- Beide Jobs müssen vor dem Merge grün sein.

## 11. Fixtures

Vorgesehene Struktur:

```text
fixtures/
├── gridwords/
│   ├── solved/
│   ├── unsolved/
│   └── invalid/
├── quadwords/
│   ├── solved/
│   ├── unsolved/
│   └── invalid/
└── quadwords-images/
    ├── input/
    └── expected/
```

Regeln:

- Dateinamen beschreiben den fachlichen Fall.
- Originale Share-Inhalte möglichst unverändert speichern.
- Personenbezogene oder irrelevante Chatinhalte entfernen.
- Zu jedem Bildfixture existiert eine erwartete normalisierte Ausgabe.
- Parserregressionen zunächst als Fixture reproduzieren.

## 12. Codex-/Terra-Aufträge

Ein Issue enthält:

- klares Ziel,
- Ausgangslage,
- konkreten Umfang,
- explizite Nicht-Ziele,
- Abnahmekriterien,
- verlangte Tests,
- erwarteten Ergebnisbericht.

Ein kompakter Implementierungsauftrag kann danach lauten:

```text
Bearbeite GitHub-Issue #N vollständig auf dem dort genannten Branch.
Befolge AGENTS.md und führe alle verlangten Tests selbst aus.
```

Bei Persistenzaufgaben darf und soll Docker verwendet werden. Automatisierte Tests öffnen weiterhin keine echte Discord-Verbindung und verwenden keinen Bot-Token.

## 13. Erwartetes Verhalten von Codex/Terra

Soll:

- Dokumentation und Issue vor Änderungen lesen,
- kleine, prüfbare Änderungen bevorzugen,
- keine Testergebnisse erfinden,
- Standardbuild ausführen,
- bei Persistenzumfang zusätzlich das Datenbankprofil lokal mit Docker ausführen,
- CI-Ergebnisse konkret berichten,
- verbleibende manuelle Prüfungen nennen.

Soll nicht:

- Tokens anfordern oder verwenden,
- echte Discord-Verbindungen in automatisierten Tests öffnen,
- Datenbankintegration in den schnellen Standardbuild mischen,
- Architekturgrenzen ohne ADR ändern,
- komplette Anwendungsteile auf Verdacht neu schreiben,
- roten Build als nebensächlich behandeln.

## 14. Pull-Request-Checkliste

Vor Merge:

- [ ] Issue-Umfang vollständig umgesetzt
- [ ] keine unangeforderten Zusatzfeatures
- [ ] lokaler Standardbuild erfolgreich
- [ ] bei Persistenzumfang lokales Datenbankprofil mit Docker erfolgreich
- [ ] GitHub Actions vollständig grün
- [ ] neue Fachlogik automatisiert getestet
- [ ] Architekturgrenzen eingehalten
- [ ] keine Secrets oder lokalen Dateien committed
- [ ] Dokumentation aktualisiert
- [ ] notwendiger manueller Smoke-Test erfolgreich oder ausdrücklich ausstehend

Nach Merge:

- [ ] Issue geschlossen
- [ ] `main` aktualisiert
- [ ] Feature-Branch lokal und remote aufgeräumt
- [ ] nächstes Issue und nächster Branch vorbereitet

## 15. Reviewreihenfolge

1. Sicherheits- und Datenverlustblocker
2. fachliche Korrektheit
3. Zustandsmaschine und Idempotenz
4. Parser- und Zeitregeln
5. Architekturgrenzen
6. Tests und CI
7. Dokumentation
8. Wartbarkeit und Stil

Optionales Refactoring darf einen fachlich vollständigen PR nicht unnötig blockieren.

## 16. Logging und Abhängigkeiten

Logs:

- verständliche strukturierte Meldungen
- technische IDs zulässig
- keine Tokens, Passwörter oder vollständigen Umgebungsvariablen
- keine vollständigen fremden Nachrichteninhalte im INFO-Log
- retryfähige und permanente Fehler unterscheidbar

Vor neuen oder geänderten Abhängigkeiten:

1. stabiles Release prüfen,
2. Kompatibilität mit Java 21 und Spring Boot prüfen,
3. bestehendes Dependency Management nutzen,
4. transitive Laufzeitfolgen berücksichtigen,
5. bei Konflikten `mvn dependency:tree` ausführen.

## 17. Abgeschlossene manuelle Smoke-Tests

### Inkrement 4 – kanonische GridWords-Nachricht

Tobias hat den echten Discord-/PostgreSQL-Smoke-Test am 29. Juli 2026 erfolgreich durchgeführt. Bestätigt wurden insbesondere die kanonische Veröffentlichung ohne Löschung des Originals, das Edit derselben Bot-Nachricht bei einer Korrektur, unsichtbare Publication-Keys und das getrennte QuadWords-Verhalten.

### Inkrement 5 – sichere GridWords-Ersetzung

Tobias hat den vollständigen Discord-/PostgreSQL-Smoke-Test am 30. Juli 2026 erfolgreich mit Docker-Compose-PostgreSQL durchgeführt. Bestätigt wurden:

- genau eine kanonische GridWords-Bot-Nachricht,
- Quelllöschung erst nach bestätigter Veröffentlichung,
- Edit derselben Bot-Message-ID bei Korrekturen,
- keine GridWords-`✅`-Reaktion,
- unverändertes QuadWords- und Ablehnungsverhalten,
- permanenter Fehlerzustand bei fehlender Löschberechtigung ohne Rückstufung der Veröffentlichung,
- Bereinigung der aktuellen und der zuvor festhängenden supersedierten Quelle nach Wiederherstellung der Berechtigung,
- Neustart ohne Duplikate,
- Abschluss eines persistierten `ORIGINAL_MESSAGE_DELETED`-Zustands ohne sichtbare zusätzliche Discord-Aktion,
- keine offenen Claims, Leases oder Delivery-Attempts am Ende des Tests.
