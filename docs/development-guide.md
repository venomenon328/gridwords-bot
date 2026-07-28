# Entwicklungsleitfaden

Dieser Leitfaden beschreibt die praktische Arbeitsweise für menschliche Entwicklung und Codex-Aufträge. Die fachlichen Anforderungen stehen in `anforderungsspezifikation.md`, die Architektur in `architecture.md`.

## 1. Arbeitsmodell

- `main` enthält nur geprüfte und mergefähige Stände.
- Jede Aufgabe wird in einem eigenen Branch umgesetzt.
- Ein GitHub-Issue beschreibt Ziel, Umfang und Abnahmekriterien.
- Codex kann mit einem kurzen Auftrag wie `Bearbeite Issue #N vollständig` gestartet werden, weil dauerhafte Regeln in `AGENTS.md` und den verlinkten Dokumenten liegen.
- Architekturänderungen werden nicht beiläufig durch ein Feature eingeführt, sondern vorab als ADR dokumentiert.
- Docker Desktop ist keine Voraussetzung für die lokale Entwicklung.

## 2. Voraussetzungen

### 2.1 Verbindliche lokale Werkzeuge

- Git
- JDK 21
- Maven 3.9 oder neuer
- VS Code mit Codex beziehungsweise eine andere geeignete IDE

Prüfen:

```bash
git --version
java -version
mvn --version
```

### 2.2 Optionale Infrastruktur

Für die normale Parser-, Domain-, Application- und Discord-Entwicklung ist keine lokale Datenbank und keine Container-Runtime erforderlich.

Optional:

- nativ installiertes PostgreSQL für einen manuellen lokalen Persistenzstart,
- Docker Engine, Docker Desktop oder eine andere kompatible Compose-Umgebung als alternative Komfortlösung.

Die Entscheidung ist in `adr/0004-docker-optional-local-development.md` dokumentiert.

## 3. Secrets und lokale Konfiguration

Der Discord-Token ist ein Secret.

Verbindliche Regeln:

- niemals committen,
- niemals in einen Codex-Prompt einfügen,
- niemals in Issue, PR, Log oder Screenshot veröffentlichen,
- ausschließlich lokal beziehungsweise später im Secret Store des Hosts setzen.

`.env` bleibt in `.gitignore`. Die Anwendung importiert sie über `spring.config.import=optional:file:.env[.properties]`; damit liest Spring die Datei beim Anwendungsstart tatsächlich ein. Sie wird nicht als Betriebssystemumgebung exportiert. Betriebssystem-Umgebungsvariablen haben wegen der Spring-Property-Priorität Vorrang vor lokalen Dateiwerten.

Unter PowerShell wird die Datei einmalig angelegt:

```powershell
Copy-Item .env.example .env
```

`DISCORD_BOT_TOKEN` wird ausschließlich in diese lokale Datei eingetragen.

## 4. Build ohne externe Systeme

Der lokale Standardbuild muss funktionieren ohne:

- Discord-Token,
- Discord-Netzwerkverbindung,
- PostgreSQL,
- Docker oder andere Container-Runtime,
- manuell vorbereitete Infrastruktur.

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Der Standardbuild umfasst alle Unit-, Parser-, Domain-, Application-, Architektur- und infrastrukturlosen Adaptertests.

Datenbankintegrationstests werden mit dem Persistenzinkrement in einem separaten Maven-Profil eingeführt, vorgesehen:

```bash
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Dieses Profil ist im vollständigen GitHub-Actions-Build verpflichtend. Es darf in CI nicht unbemerkt übersprungen werden. Lokal muss es ohne Container-Runtime nicht ausgeführt werden können.

## 5. Lokaler Start ohne Datenbank

Die Anwendung startet standardmäßig im Profil `offline`. Dieses Profil deaktiviert die Datenbank-Autokonfiguration.

### 5.1 Vollständig offline

```powershell
$env:SPRING_PROFILES_ACTIVE = "offline"
mvn spring-boot:run
```

Dabei werden weder Discord noch PostgreSQL kontaktiert, solange `DISCORD_ENABLED=false` ist.

### 5.2 Discord-Gateway ohne Docker und PostgreSQL

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

Das Profil `offline` deaktiviert ausschließlich die Datenbank-Autokonfiguration. Die ausdrücklich aktivierte JDA-Verbindung wird trotzdem aufgebaut.

Der reale Gateway-Smoke-Test wurde am 29. Juli 2026 erfolgreich auf Tobias' Entwicklungsrechner durchgeführt:

- kein Docker Desktop,
- kein PostgreSQL,
- Bot auf dem vorgesehenen Server online,
- JDA-Gatewayverbindung erfolgreich.

Dieser Test enthält noch keine fachliche Ergebnisverarbeitung.

## 6. Lokaler Start mit PostgreSQL

Für spätere Persistenzentwicklung wird eine nativ installierte PostgreSQL-Instanz unterstützt. In `.env` werden die tatsächlichen lokalen Werte gesetzt:

```properties
DATABASE_URL=jdbc:postgresql://localhost:5432/gridwords
DATABASE_USERNAME=gridwords
DATABASE_PASSWORD=lokales-passwort
```

Start:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=database
```

Liquibase wendet dabei dieselben Migrationen an, die auch in CI und im späteren Betrieb verwendet werden.

Discord bleibt unabhängig steuerbar:

```properties
DISCORD_ENABLED=true
```

Eine native PostgreSQL-Installation ist nur erforderlich, wenn die Anwendung lokal tatsächlich mit Persistenz ausgeführt werden soll. Sie ist keine Voraussetzung für den Standardbuild.

## 7. Optionale Docker-/Compose-Nutzung

`compose.yaml` bleibt als Komfortoption bestehen. Bei verfügbarer Container-Runtime:

```bash
docker compose up -d postgres
docker compose ps
docker compose down
```

Vollständiges Löschen einschließlich Datenbankvolume:

```bash
docker compose down -v
```

Keiner dieser Befehle ist für den lokalen Standardbuild oder den Discord-Smoke-Test erforderlich.

## 8. Testpyramide

### Unit-Tests

Schnell und ohne Spring-Kontext, soweit möglich:

- Parser
- Datums- und Serienregeln
- Ausgabeformatierung
- Reminder-Entscheidungen
- Zustandsübergänge

### Application-Tests

- Use Cases mit In-Memory-Fakes der Ports
- kein JDA
- keine echte Datenbank
- feste `Clock`

### Architekturtests

- Domain hängt nicht von Spring, JDA oder JPA ab.
- Application hängt nicht von Adapterpaketen ab.
- JDA-Typen bleiben im Discord-Adapter beziehungsweise im Wiring.

### Persistence-Integrationstests

- echtes PostgreSQL
- Liquibase wird real ausgeführt
- Constraints und Upsert-/Konfliktverhalten werden geprüft
- Ausführung über das Maven-Profil `database-integration`
- verpflichtend in GitHub Actions
- lokal ohne Container-Runtime nicht Bestandteil des Standardbuilds

Testcontainers ist für diese CI-Tests zulässig. H2 ersetzt die PostgreSQL-Integrationstests nicht.

### Discord-Adaptertests

- keine echte Netzwerkverbindung
- JDA-Grenze mocken oder hinter einer schmalen Wrapper-Komponente testen
- Umwandlung zwischen JDA-Ereignissen und internen DTOs prüfen

### Manueller Smoke-Test

- nur für echte Gateway-Verbindung und Channelrechte
- kein PostgreSQL erforderlich
- kein Ersatz für automatisierte Tests
- bei späteren Persistenzinkrementen zusätzlicher manueller Start gegen natives PostgreSQL möglich

## 9. CI-Strategie

GitHub Actions ist die verbindliche Umgebung für Tests, die eine Container-Runtime benötigen.

Bis zum Persistenzinkrement genügt:

```bash
mvn --batch-mode --no-transfer-progress verify
```

Mit dem Persistenzinkrement wird der Workflow so erweitert, dass zusätzlich beziehungsweise stattdessen das vollständige Profil läuft:

```bash
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Anforderungen:

- Die Datenbankintegrationstests müssen in den Job-Logs eindeutig als ausgeführt erkennbar sein.
- Ein fehlender Containerzugriff oder eine nicht gestartete Datenbank darf in CI nicht als erfolgreicher Skip durchgehen.
- Der schnelle lokale Standardbuild bleibt unverändert Docker-frei.

## 10. Fixtures

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

Fixture-Regeln:

- Dateinamen beschreiben fachlichen Fall, nicht nur laufende Nummern.
- Originale Share-Inhalte möglichst unverändert speichern.
- Personenbezogene oder irrelevante Chat-Inhalte entfernen.
- Zu jedem Bildfixture existiert eine erwartete Unicode-Ausgabe.
- Parser-Regressionen werden zunächst als Fixture reproduziert und anschließend behoben.

## 11. Codex-Aufträge

Ein guter Issue-Auftrag enthält:

- klares Ziel,
- Ausgangslage,
- konkrete Aufgaben,
- explizite Nicht-Ziele,
- Abnahmekriterien,
- erwarteten Ergebnisbericht.

Der eigentliche Codex-Prompt kann danach kurz bleiben:

```text
Bearbeite GitHub-Issue #N vollständig auf dem dort genannten Branch.
Befolge AGENTS.md und führe alle verlangten Tests selbst aus.
```

Bei einem Review-Auftrag:

```text
Reviewe Pull Request #N gegen das zugehörige Issue, AGENTS.md,
docs/anforderungsspezifikation.md und docs/architecture.md.
Melde zuerst Blocker, dann wichtige Risiken und zuletzt optionale Verbesserungen.
```

## 12. Erwartetes Verhalten von Codex

Codex soll:

- vor Änderungen die verlinkte Dokumentation lesen,
- den bestehenden Fehler reproduzieren,
- keine Testergebnisse erfinden,
- Abhängigkeiten nicht blind aktualisieren,
- kleine und prüfbare Änderungen bevorzugen,
- keine nicht angeforderte Fachlogik vorziehen,
- nach Implementierung den lokalen Standardbuild ausführen,
- Datenbankintegrationstests nur dort als erfolgreich melden, wo sie tatsächlich ausgeführt wurden,
- im Abschlussbericht verbleibende manuelle und CI-Prüfungen nennen.

Codex soll nicht:

- Tokens oder Secrets anfordern,
- eine echte Discord-Verbindung in automatisierten Tests öffnen,
- Docker Desktop als lokale Voraussetzung einführen,
- Datenbankintegrationstests im lokalen Standardbuild erzwingen,
- Architekturgrenzen ohne ADR ändern,
- komplette Anwendungsteile auf Verdacht neu schreiben,
- einen roten Build als nebensächlich behandeln.

## 13. Pull-Request-Checkliste

Vor Merge:

- [ ] Issue-Umfang vollständig umgesetzt
- [ ] Keine unangeforderten Zusatzfeatures
- [ ] Lokaler `mvn clean verify` ohne Docker erfolgreich
- [ ] GitHub Actions grün
- [ ] Datenbankprofil in CI erfolgreich, sofern Persistenz betroffen
- [ ] Neue Fachlogik mit Tests
- [ ] Fehler- und Idempotenzpfade getestet
- [ ] Keine Secrets oder lokalen Dateien committed
- [ ] Liquibase für Schemaänderungen verwendet
- [ ] README/Dokumentation aktualisiert, falls Bedienung oder Architektur betroffen
- [ ] Erforderliche manuelle Discord- oder Persistenzprüfung dokumentiert

## 14. Commit-Konvention

Bevorzugte Präfixe:

```text
build: ...
chore: ...
docs: ...
feat: ...
fix: ...
refactor: ...
test: ...
```

Commits sollen logisch zusammenhängend und reviewbar sein. Kein Zwang zu einem Commit pro Datei oder zu künstlich kleinen Commits.

## 15. Logging

- strukturierte, verständliche Logtexte,
- technische IDs sind zulässig,
- keine Tokens, Passwörter oder vollständigen Umgebungsvariablen,
- keine vollständigen fremden Nachrichteninhalte im normalen INFO-Log,
- erwartbare Nutzerfehler nicht als ungefilterter Stacktrace auf ERROR,
- Retry-fähige und endgültige Fehler unterscheidbar machen.

## 16. Abhängigkeiten und Versionen

Vor dem Hinzufügen oder Ändern:

1. tatsächliche Verfügbarkeit des stabilen Releases prüfen,
2. Kompatibilität mit Java 21 und der verwendeten Spring-Boot-Linie prüfen,
3. bestehende Dependency-Management-Funktionen nutzen,
4. neue transitive Laufzeitfolgen berücksichtigen,
5. `mvn dependency:tree` bei Konflikten prüfen.

Versionen nicht allein deshalb auf den neuesten Stand bringen, weil sie neuer sind. Der konkrete Nutzen und die Kompatibilität entscheiden.
