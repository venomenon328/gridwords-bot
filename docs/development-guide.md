# Entwicklungsleitfaden

Dieser Leitfaden beschreibt die praktische Arbeitsweise für menschliche Entwicklung und Codex-Aufträge. Die fachlichen Anforderungen stehen in `anforderungsspezifikation.md`, die Architektur in `architecture.md`.

## 1. Arbeitsmodell

- `main` enthält nur geprüfte und mergefähige Stände.
- Jede Aufgabe wird in einem eigenen Branch umgesetzt.
- Ein GitHub-Issue beschreibt Ziel, Umfang und Abnahmekriterien.
- Codex kann mit einem kurzen Auftrag wie `Bearbeite Issue #N vollständig` gestartet werden, weil dauerhafte Regeln in `AGENTS.md` und den verlinkten Dokumenten liegen.
- Architekturänderungen werden nicht beiläufig durch ein Feature eingeführt, sondern vorab als ADR dokumentiert.

## 2. Voraussetzungen

Lokal:

- Git
- JDK 21
- Maven 3.9 oder neuer
- Docker Desktop beziehungsweise Docker Engine mit Compose
- VS Code mit Codex

Prüfen:

```bash
git --version
java -version
mvn --version
docker --version
docker compose version
```

## 3. Secrets und lokale Konfiguration

Der Discord-Token ist ein Secret.

Verbindliche Regeln:

- niemals committen,
- niemals in einen Codex-Prompt einfügen,
- niemals in Issue, PR, Log oder Screenshot veröffentlichen,
- ausschließlich lokal beziehungsweise später im Secret Store des Hosts setzen.

`.env` bleibt in `.gitignore`. Das Projekt muss eine dokumentierte und tatsächlich funktionierende Methode bereitstellen, diese Werte beim lokalen Start zu laden. Eine bloß vorhandene `.env`-Datei wird nicht automatisch von jedem Maven-/Java-Prozess eingelesen.

Zulässige Lösungen sind beispielsweise:

- ein versioniertes Startskript, das `.env` sicher lädt und den Prozess startet,
- Spring-Konfigurationsimport mit einer optionalen, nicht versionierten Property-Datei,
- IDE-Run-Konfiguration, sofern zusätzlich ein plattformtauglicher Kommandozeilenweg dokumentiert ist.

Betriebssystem-Umgebungsvariablen müssen lokale Dateiwerte übersteuern können.

## 4. Build ohne externe Systeme

Der Standardbuild muss funktionieren ohne:

- Discord-Token,
- Discord-Netzwerkverbindung,
- lokal gestartetes PostgreSQL,
- manuell vorbereitete Datenbank.

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Discord ist im Testprofil und standardmäßig deaktiviert. Datenbankintegrationstests verwenden Testcontainers.

## 5. Lokaler Infrastrukturstart

Für eine manuelle Anwendungsausführung:

```bash
docker compose up -d postgres
```

Status:

```bash
docker compose ps
```

Stoppen:

```bash
docker compose down
```

Vollständiges lokales Löschen einschließlich Datenbankvolume:

```bash
docker compose down -v
```

Dieser Schritt ist für `mvn verify` nicht erforderlich.

## 6. Lokaler Discord-Smoke-Test

Erst durchführen, wenn der Offline-Build grün ist.

Prüfziel:

1. Anwendung startet mit lokaler PostgreSQL-Instanz.
2. Discord-Verbindung wird mit lokalem Token aufgebaut.
3. Bot erscheint auf dem Testserver online.
4. Server- und Channel-Konfiguration werden erkannt.
5. Beim Shutdown wird JDA sauber beendet.

Der Smoke-Test enthält zunächst keine fachliche Ergebnisverarbeitung.

Ein erfolgreicher Smoke-Test wird nur behauptet, wenn er mit dem realen lokalen Token tatsächlich ausgeführt wurde.

## 7. Testpyramide

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

### Persistence-Integrationstests

- PostgreSQL über Testcontainers
- Liquibase wird real ausgeführt
- Constraints und Upsert-/Konfliktverhalten werden geprüft

### Discord-Adaptertests

- keine echte Netzwerkverbindung
- JDA-Grenze mocken oder hinter einer schmalen Wrapper-Komponente testen
- Umwandlung zwischen JDA-Ereignissen und internen DTOs prüfen

### Manueller Smoke-Test

- nur für echte Gateway-Verbindung und Channelrechte
- nicht Ersatz für automatisierte Tests

## 8. Fixtures

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

## 9. Codex-Aufträge

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

## 10. Erwartetes Verhalten von Codex

Codex soll:

- vor Änderungen die verlinkte Dokumentation lesen,
- den bestehenden Fehler reproduzieren,
- keine Testergebnisse erfinden,
- Abhängigkeiten nicht blind aktualisieren,
- kleine und prüfbare Änderungen bevorzugen,
- keine nicht angeforderte Fachlogik vorziehen,
- nach Implementierung `mvn clean verify` ausführen,
- im Abschlussbericht verbleibende manuelle Prüfungen nennen.

Codex soll nicht:

- Tokens oder Secrets anfordern,
- eine echte Discord-Verbindung in automatisierten Tests öffnen,
- Architekturgrenzen ohne ADR ändern,
- komplette Anwendungsteile auf Verdacht neu schreiben,
- einen roten Build als nebensächlich behandeln.

## 11. Pull-Request-Checkliste

Vor Merge:

- [ ] Issue-Umfang vollständig umgesetzt
- [ ] Keine unangeforderten Zusatzfeatures
- [ ] `mvn clean verify` erfolgreich
- [ ] GitHub Actions grün
- [ ] Neue Fachlogik mit Tests
- [ ] Fehler- und Idempotenzpfade getestet
- [ ] Keine Secrets oder lokalen Dateien committed
- [ ] Liquibase für Schemaänderungen verwendet
- [ ] README/Dokumentation aktualisiert, falls Bedienung oder Architektur betroffen
- [ ] Manueller Discord-Smoke-Test dokumentiert, sofern für diesen PR erforderlich

## 12. Commit-Konvention

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

## 13. Logging

- strukturierte, verständliche Logtexte,
- technische IDs sind zulässig,
- keine Tokens, Passwörter oder vollständigen Umgebungsvariablen,
- keine vollständigen fremden Nachrichteninhalte im normalen INFO-Log,
- erwartbare Nutzerfehler nicht als ungefilterter Stacktrace auf ERROR,
- Retry-fähige und endgültige Fehler unterscheidbar machen.

## 14. Abhängigkeiten und Versionen

Vor dem Hinzufügen oder Ändern:

1. tatsächliche Verfügbarkeit des stabilen Releases prüfen,
2. Kompatibilität mit Java 21 und der verwendeten Spring-Boot-Linie prüfen,
3. bestehende Dependency-Management-Funktionen nutzen,
4. neue transitive Laufzeitfolgen berücksichtigen,
5. `mvn dependency:tree` bei Konflikten prüfen.

Versionen nicht allein deshalb auf den neuesten Stand bringen, weil sie neuer sind. Der konkrete Nutzen und die Kompatibilität entscheiden.