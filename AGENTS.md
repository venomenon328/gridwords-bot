# AGENTS.md

Diese Datei gilt für das gesamte Repository. Es gibt derzeit keine untergeordneten `AGENTS.md`-Dateien.

## 1. Vor jeder Änderung lesen

Lies vor der Implementierung mindestens:

1. `docs/anforderungsspezifikation.md` – verbindliche fachliche Grundanforderungen und Versionsgrenzen
2. `docs/requirements/series-model.md` – verbindliche Präzisierung und Änderung der Serien-, Tagesstatus- und Berichtssemantik
3. `docs/requirements/dynamic-player-model.md` – dynamische Spieler, Teilnahmezeiträume und Reminder-Opt-out
4. `docs/requirements/daily-status-reminders.md` – Tagesstatus, Scheduler und Reminder-Auslieferung
5. `docs/architecture.md` – verbindliche Architektur, Modulgrenzen und Abläufe
6. `docs/development-guide.md` – Build, Tests, Secrets und Arbeitsweise
7. `docs/implementation-plan.md` – Reihenfolge der Inkremente
8. `docs/requirements/production-deployment.md`, wenn die Aufgabe Build, Container, Deployment, Secrets, Backups oder Produktion betrifft
9. vorhandene ADRs unter `docs/adr/`, wenn die Aufgabe die dort behandelten Entscheidungen berührt

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
- Genau ein konfigurierter Discord-Server und ein Channel; Spieler werden dynamisch aus gültigen Shares beziehungsweise Commands verwaltet
- Keine Microservices, keine verteilte Queue, kein generisches Plugin-Framework
- Keine generative KI zur Laufzeit
- Docker Desktop und Docker Compose stehen auf dem primären Entwicklungsrechner zur Verfügung und dürfen für Persistenz-, Integrations-, Container- und Smoke-Tests vorausgesetzt werden
- Produktionsziel ist ein containerisierter Betrieb auf einem Debian-13-VPS gemäß ADR 0013

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
- Nur der konfigurierte Server und Channel werden verarbeitet.
- Jeder menschliche Nutzer kann durch ein vollständig gültiges Share Spieler werden; die dynamischen Teilnahme- und Reminderregeln sind verbindlich.
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

- Serien werden aus den persistierten Spielergebnissen und den historisch wirksamen Teilnahmezeiträumen abgeleitet und eindeutig benannt.
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

### Produktionscontainer und Betrieb

- Verbindlich sind `docs/requirements/production-deployment.md` und ADR 0013.
- Das Produktionsimage läuft als Nicht-Root-Benutzer.
- Runtimeimages enthalten keine Quellen, Maven-Caches, `.env`-Dateien, Tokens, Passwörter oder privaten Schlüssel.
- Produktive Secrets werden ausschließlich serverseitig und mit restriktiven Dateirechten bereitgestellt.
- PostgreSQL und Management-/Actuatorports werden in Produktion nicht öffentlich veröffentlicht.
- Deployment verwendet explizite unveränderliche Image-Tags; `latest` ist keine zulässige reproduzierbare Referenz.
- Vor jedem Produktionsdeployment wird ein geprüftes Datenbankbackup erstellt.
- App-Rollback und Datenbank-Restore sind getrennte Vorgänge.
- Shellskripte verwenden sichere Optionen, validieren Eingaben und melden Fehler eindeutig; kein stilles Weiterlaufen nach Teilfehlern.
- Backups werden atomar erzeugt und vor Erfolgsmeldung validiert.
- Kein automatisches Produktionsdeployment aus GitHub Actions in Inkrement 9.

## 4. Code- und Testregeln

- Bevorzuge kleine, benannte Typen und klare Kontrollflüsse gegenüber cleveren Abstraktionen.
- Keine vorauseilende Generalisierung für weitere Server, Spiele oder beliebige Infrastrukturziele.
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
- Deployment-, Backup- und Restore-Skripte benötigen automatisierte Happy-Path-, Fehler- und Wiederholungstests in einer isolierten Containerumgebung.
- Containerprüfungen müssen mindestens Nicht-Root-Runtime, Secretfreiheit, Health, leeres Volume, Neustart und fehlende PostgreSQL-Portfreigabe abdecken.

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

Bei Produktionscontaineränderungen zusätzlich mindestens:

```bash
docker build -t gridwords-bot:test .
docker compose --env-file .env.production.test -f compose.production.yaml config
docker compose --env-file .env.production.test -f compose.production.yaml up -d
docker compose --env-file .env.production.test -f compose.production.yaml ps
```

Die konkrete Test-Environment-Datei darf nur nicht geheime Testwerte enthalten und wird nicht als produktive Konfiguration verwendet.

Nicht behaupten, Datenbankintegrationstests, Container-, Backup-, Restore- oder Deploymenttests seien erfolgreich gewesen, wenn sie nicht tatsächlich ausgeführt wurden.

Für lokale manuelle Ausführung gelten die Befehle aus `README.md`, `docs/development-guide.md` und den Betriebsdokumenten.

## 6. Umgang mit Abhängigkeiten und Basisimages

- Keine Versionsnummer erfinden.
- Neue oder geänderte Versionen gegen Maven Central beziehungsweise die offizielle Projektdokumentation prüfen.
- Containerbasis- und PostgreSQL-Images bewusst auf unterstützte Versionen pinnen und die Auswahl dokumentieren.
- Stabile Releases bevorzugen; keine Snapshot-, Milestone- oder Release-Candidate-Version ohne explizite Begründung.
- Neue Bibliotheken nur hinzufügen, wenn die Standardbibliothek oder bestehende Abhängigkeiten die Aufgabe nicht angemessen lösen.
- Bei einer neuen wesentlichen Bibliothek Nutzen, Alternativen und Betriebsfolgen im PR beschreiben.

## 7. Dokumentation und Architekturänderungen

- Fachliche Anforderungen nicht beiläufig im Code neu definieren.
- Eine Änderung an Modulgrenzen, Persistenzstrategie, Discord-Ersetzungsablauf, Scheduling, Testinfrastruktur, Deployment oder Technologieauswahl erfordert vor der Implementierung ein neues beziehungsweise aktualisiertes ADR.
- Bestehende ADRs werden nicht rückwirkend umgeschrieben, wenn eine Entscheidung ersetzt wird; stattdessen neues ADR mit Verweis auf das abgelöste Dokument.
- Fachliche Präzisierungen, die ältere Anforderungsformulierungen ersetzen, werden unter `docs/requirements/` dokumentiert und müssen ihren Geltungsbereich ausdrücklich nennen.
- README bleibt Einstiegsdokument und verlinkt auf detaillierte Dokumente, dupliziert sie aber nicht vollständig.
- Betriebsanleitungen enthalten kopierbare Befehle, Voraussetzungen, erwartete Ergebnisse, Abbruchbedingungen und sichere Rückwege.

## 8. Git- und Aufgabenworkflow

- Arbeite auf dem im Auftrag genannten Branch.
- Keine Änderungen außerhalb des Auftragsumfangs.
- Keine direkte Änderung von `main`, sofern nicht ausdrücklich verlangt.
- Kleine logisch zusammengehörige Commits mit verständlichen Commit-Nachrichten.
- Bestehende Nutzeränderungen nicht zurücksetzen.
- Keine Secrets, lokalen Datenbanken, generierten Binärdateien, Backups oder IDE-Artefakte committen.
- Docker darf in Issues, Codex-/Terra-Aufträgen und lokalen Prüfungen vorausgesetzt werden, wenn der Aufgabenbereich Persistenz, Container oder Integration betrifft. Der infrastrukturlose Standardbuild bleibt dennoch verpflichtend.
- PRs für Produktionsbetrieb bleiben Draft, bis automatisierte und reale Serverabnahme vollständig sind.

## 9. Abschlussbericht für Codex-Aufgaben

Am Ende immer knapp berichten:

- umgesetzter Umfang,
- wichtige Designentscheidungen innerhalb der vorgegebenen Architektur,
- geänderte Dateien,
- ausgeführte Befehle und konkrete Testergebnisse,
- nicht ausgeführte Tests mit Grund,
- verbleibende Risiken oder notwendige manuelle beziehungsweise CI-Prüfungen.

Nicht behaupten, ein Build, Test, Imagepush, Backup, Restore, Deployment, Rollback oder Discord-Smoke-Test sei erfolgreich gewesen, wenn er nicht tatsächlich ausgeführt wurde.