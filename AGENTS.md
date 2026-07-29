# AGENTS.md

Diese Datei gilt für das gesamte Repository. Es gibt derzeit keine untergeordneten `AGENTS.md`-Dateien.

## 1. Vor jeder Änderung lesen

Lies vor der Implementierung mindestens:

1. `docs/anforderungsspezifikation.md` – verbindliche fachliche Grundanforderungen und Versionsgrenzen
2. `docs/requirements/series-model.md` – verbindliche Präzisierung und Änderung der Serien-, Tagesstatus- und Berichtssemantik
3. `docs/architecture.md` – verbindliche Architektur, Modulgrenzen und Abläufe
4. `docs/development-guide.md` – Build, Tests, Secrets und Arbeitsweise
5. `docs/implementation-plan.md` – Reihenfolge der Inkremente
6. vorhandene ADRs unter `docs/adr/`, wenn die Aufgabe die dort behandelten Entscheidungen berührt

Bei Widersprüchen gilt:

1. aktueller expliziter Nutzerauftrag beziehungsweise GitHub-Issue,
2. ausdrücklich als abgenommen gekennzeichnete Anforderungspräzisierungen unter `docs/requirements/`,
3. `docs/anforderungsspezifikation.md`,
4. akzeptierte ADRs,
5. `docs/architecture.md`,
6. diese Datei,
7. übrige Dokumentation.

Widersprüche nicht stillschweigend auflösen. Im Ergebnisbericht benennen oder vor einer weitreichenden Änderung nachfragen.

## 2. Projektcharakter

- Kleiner, einzelner Discord-Bot als modularer Monolith
- Java 21, Spring Boot, Maven, JDA, PostgreSQL, Liquibase
- Genau ein konfigurierter Server, ein Channel und zwei Spieler in Version 1
- Keine Microservices, keine verteilte Queue, kein generisches Plugin-Framework
- Keine generative KI zur Laufzeit
- Docker Desktop und Docker Compose stehen auf dem primären Entwicklungsrechner zur Verfügung und dürfen für Persistenz-, Integrations- und Smoke-Tests vorausgesetzt werden.

Einfachheit, Nachvollziehbarkeit und robuste Fehlerbehandlung sind wichtiger als abstrakte Wiederverwendbarkeit.

## 3. Verbindliche Architekturregeln

### Abhängigkeiten

- Fachlicher Kern und Application Services dürfen keine JDA-Typen kennen.
- Fachlicher Kern darf weder Spring-, JPA-/Hibernate- noch Discord-Abhängigkeiten haben.
- Discord-, Datenbank- und Scheduler-Code sind Adapter an klaren Ports.
- Parser sind deterministisch und frei von Datenbank-, Discord- und Netzwerkzugriffen.
- Persistenzmodelle dürfen nicht ungeprüft als öffentliche Domänenmodelle verwendet werden.
- Konfiguration wird typisiert gebunden; keine verstreuten direkten Zugriffe auf Umgebungsvariablen.

### Discord-Verarbeitung

- Der JDA-Listener bleibt dünn: filtern, unveränderliche Eingabedaten kopieren, an einen Use Case delegieren.
- Keine längeren Datenbank- oder Discord-Operationen auf dem JDA-Event-Thread.
- Nur der konfigurierte Server, Channel und die zwei konfigurierten Nutzer werden verarbeitet.
- Bot- und Webhook-Nachrichten werden ignoriert.

### Sichere Ergebnisersetzung

Eine fremde Originalnachricht darf niemals vorzeitig gelöscht werden. Verbindliche Reihenfolge:

1. parsen und validieren,
2. idempotent persistieren,
3. kanonische Bot-Nachricht erfolgreich veröffentlichen,
4. Bot-Message-ID persistieren,
5. erst dann Original löschen,
6. Verarbeitungsschritt persistieren.

Jeder Schritt muss nach einem Absturz oder erneut zugestellten Event gefahrlos fortsetzbar sein. Details: `docs/adr/0002-idempotent-message-replacement.md`.

### Zeit

- Fachliche Zeitzone ist `Europe/Berlin`.
- Keine Verwendung von `LocalDate.now()` oder `Instant.now()` in fachlichem Code; `Clock` injizieren.
- Das Datum im Share-Ergebnis ist der Spieltag.
- Heute und gestern sind das einzige zulässige automatische Nachtragsfenster.

### Serienmodell

- Serien werden aus den persistierten Spielergebnissen abgeleitet und eindeutig benannt.
- Persönlich zu berechnen sind Aktivitätsserie, Komplettserie, GridWords-Lösungsserie, QuadWords-Lösungsserie und Perfektserie.
- Gemeinsam zu berechnen sind gemeinsame Komplettserie und gemeinsame Perfektserie.
- Es gibt keine gemeinsame Aktivitätsserie.
- GridWords- und QuadWords-Lösungen dürfen nicht zu einer unspezifischen persönlichen Lösungsserie zusammengefasst werden.
- Maßgeblich sind die Definitionen und Testfälle in `docs/requirements/series-model.md`.

### Datenbank und lokale Infrastruktur

- Schemaänderungen ausschließlich über Liquibase.
- Hibernate `ddl-auto` bleibt `validate` oder `none`, niemals `update`/`create` in produktionsnaher Konfiguration.
- Fachliche Eindeutigkeiten durch Datenbank-Constraints absichern.
- Der lokale Standardbuild bleibt ohne Datenbank und Container-Runtime ausführbar. Das ist eine bewusste Testtrennung, kein Verbot Docker in Implementierung, Integration oder manuellen Tests zu verwenden.
- Docker Compose ist die bevorzugte lokale PostgreSQL-Umgebung; eine native PostgreSQL-Installation darf als Alternative unterstützt werden.
- PostgreSQL-Integrationstests werden über das Maven-Profil `database-integration` ausgeführt.
- Bei Persistenz-, Liquibase-, Claim-, Recovery- oder PostgreSQL-spezifischen Änderungen ist das Datenbankintegrationsprofil grundsätzlich auch lokal mit Docker auszuführen und zusätzlich in GitHub Actions verpflichtend.
- Testcontainers darf für lokale und CI-Integrationstests verwendet werden, aber nicht beim normalen lokalen `mvn verify` initialisiert werden.
- H2 ersetzt keine PostgreSQL-Integrationstests.
- Maßgeblich ist `docs/adr/0010-docker-available-local-development.md`; ADR 0004 dokumentiert nur noch den historischen Ausgangspunkt.

## 4. Code- und Testregeln

- Bevorzuge kleine, benannte Typen und klare Kontrollflüsse gegenüber cleveren Abstraktionen.
- Keine vorauseilende Generalisierung für weitere Server, Spiele oder beliebig viele Nutzer.
- Keine Lombok-Abhängigkeit.
- Keine ungeprüften `Optional.get()`, keine leeren Catch-Blöcke, keine pauschalen `catch (Exception)` ohne fachliche Übersetzung oder Logging.
- Logs dürfen niemals Tokens, Passwörter, vollständige `.env`-Inhalte oder unnötige fremde Nachrichteninhalte enthalten.
- Neue fachliche Logik benötigt Tests.
- Parseränderungen benötigen Fixture-basierte Tests für Erfolg, Nicht-gelöst-Format und Fehlerfälle.
- Serienänderungen benötigen getrennte Tests für alle sieben definierten Serien und die Regel für den unvollständigen aktuellen Tag.
- Tests dürfen keine echte Discord-Verbindung öffnen.
- Zeitabhängige Tests verwenden eine feste `Clock`.
- Fehlerpfade und Idempotenz sind ebenso zu testen wie der Happy Path.
- Unit-, Domain-, Application-, Architektur- und Discord-Adaptertests müssen ohne Container-Runtime laufen; Persistenzintegration darf und soll Docker verwenden.

## 5. Standardbefehle

Vor Abschluss jeder Implementierungsaufgabe lokal ausführen:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Dieser schnelle Standardbuild bleibt bewusst ohne Docker, PostgreSQL und Discord-Token ausführbar.

Falls die Aufgabe Datenbankintegration betrifft, muss zusätzlich lokal mit verfügbarer Container-Runtime und anschließend in GitHub Actions ausgeführt werden:

```bash
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Vor manuellen Persistenzstarts beziehungsweise Smoke-Tests wird PostgreSQL bevorzugt mit Compose gestartet:

```bash
docker compose up -d postgres
docker compose ps
```

Nicht behaupten, Datenbankintegrationstests seien erfolgreich gewesen, wenn nur der Standardbuild ausgeführt wurde.

Für lokale manuelle Ausführung gelten die Befehle aus `README.md` und `docs/development-guide.md`.

## 6. Umgang mit Abhängigkeiten

- Keine Versionsnummer erfinden.
- Neue oder geänderte Versionen gegen Maven Central beziehungsweise die offizielle Projektdokumentation prüfen.
- Stabile Releases bevorzugen; keine Snapshot-, Milestone- oder Release-Candidate-Version ohne explizite Begründung.
- Neue Bibliotheken nur hinzufügen, wenn die Standardbibliothek oder bestehende Abhängigkeiten die Aufgabe nicht angemessen lösen.
- Bei einer neuen wesentlichen Bibliothek Nutzen, Alternativen und Betriebsfolgen im PR beschreiben.

## 7. Dokumentation und Architekturänderungen

- Fachliche Anforderungen nicht beiläufig im Code neu definieren.
- Eine Änderung an Modulgrenzen, Persistenzstrategie, Discord-Ersetzungsablauf, Scheduling, Testinfrastruktur oder Technologieauswahl erfordert vor der Implementierung ein neues beziehungsweise aktualisiertes ADR.
- Bestehende ADRs werden nicht rückwirkend umgeschrieben, wenn eine Entscheidung ersetzt wird; stattdessen neues ADR mit Verweis auf das abgelöste Dokument.
- Fachliche Präzisierungen, die ältere Anforderungsformulierungen ersetzen, werden unter `docs/requirements/` dokumentiert und müssen ihren Geltungsbereich ausdrücklich nennen.
- README bleibt Einstiegsdokument und verlinkt auf detaillierte Dokumente, dupliziert sie aber nicht vollständig.

## 8. Git- und Aufgabenworkflow

- Arbeite auf dem im Auftrag genannten Branch.
- Keine Änderungen außerhalb des Auftragsumfangs.
- Keine direkte Änderung von `main`, sofern nicht ausdrücklich verlangt.
- Kleine logisch zusammengehörige Commits mit verständlichen Commit-Nachrichten.
- Bestehende Nutzeränderungen nicht zurücksetzen.
- Keine Secrets, lokalen Datenbanken, generierten Binärdateien oder IDE-Artefakte committen.
- Docker darf in Issues, Codex-/Terra-Aufträgen und lokalen Prüfungen vorausgesetzt werden, wenn der Aufgabenbereich Persistenz oder Integration betrifft. Der infrastrukturlosen Standardbuild bleibt dennoch verpflichtend.

## 9. Abschlussbericht für Codex-Aufgaben

Am Ende immer knapp berichten:

- umgesetzter Umfang,
- wichtige Designentscheidungen innerhalb der vorgegebenen Architektur,
- geänderte Dateien,
- ausgeführte Befehle und konkrete Testergebnisse,
- nicht ausgeführte Tests mit Grund,
- verbleibende Risiken oder notwendige manuelle beziehungsweise CI-Prüfungen.

Nicht behaupten, ein Build, Test oder Discord-Smoke-Test sei erfolgreich gewesen, wenn er nicht tatsächlich ausgeführt wurde.
