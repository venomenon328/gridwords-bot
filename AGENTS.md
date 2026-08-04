# AGENTS.md

Diese Datei gilt für das gesamte Repository. Es gibt derzeit keine untergeordneten `AGENTS.md`-Dateien.

## 1. Vor jeder Änderung lesen

Lies vor der Implementierung mindestens:

1. `docs/anforderungsspezifikation.md` – verbindliche fachliche Grundanforderungen und Versionsgrenzen
2. `docs/requirements/series-model.md` – verbindliche Präzisierung und Änderung der Serien-, Tagesstatus- und Berichtssemantik
3. `docs/requirements/dynamic-player-model.md` – dynamische Spieler, bisherige globale Teilnahmezeiträume und Reminder-Opt-out
4. `docs/requirements/game-specific-participation.md`, wenn die Aufgabe Teilnahme, Shares, Commands, Serien, Tagesstatus, Reminder, Ergebnisdetails, Ausreden oder Berichte ab Zwischeninkrement 10.6 betrifft
5. `docs/requirements/daily-status-reminders.md` – Tagesstatus, Scheduler und Reminder-Auslieferung
6. `docs/requirements/periodic-reports.md`, wenn die Aufgabe Wochen-/Monatsberichte, Periodenstatistiken oder Report-Delivery betrifft
7. `docs/requirements/excuses.md`, wenn die Aufgabe Ausreden, kanonische Ergebnis-Komponenten, Ausredeninteraktionen, Kataloge oder Wiederholungsschutz betrifft
8. `docs/architecture.md` – verbindliche Architektur, Modulgrenzen und Abläufe
9. `docs/development-guide.md` – Build, Tests, Secrets und Arbeitsweise
10. `docs/implementation-plan.md` – Reihenfolge der Inkremente
11. `docs/requirements/production-deployment.md`, wenn die Aufgabe Build, Container, Deployment, Secrets, Backups oder Produktion betrifft
12. vorhandene ADRs unter `docs/adr/`, wenn die Aufgabe die dort behandelten Entscheidungen berührt; für periodische Reports insbesondere ADR 0014, für spielbezogene Teilnahme ADR 0016 und für Ausreden ADR 0017

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
- Wochen- und Monatsberichte verwenden ausschließlich vollständig abgeschlossene Kalenderperioden und einen expliziten Periodenend-Stichtag.

### Spielbezogene Teilnahme ab Zwischeninkrement 10.6

- Verbindlich sind `docs/requirements/game-specific-participation.md`, ADR 0016 und der Paketplan unter `docs/increments/10.6-game-specific-participation.md`.
- Teilnahmezeiträume tragen immer einen `GameType`; neue fachliche Logik darf keinen spielunspezifischen Zeitraum voraussetzen.
- Ein gültiges Share aktiviert ausschließlich den Spieltyp des Shares.
- `player.active` ist nur eine abgeleitete aktuelle Kompatibilitätsinformation und keine historische Quelle.
- Reminderstatus bleibt global; Reminderkandidaten werden ausschließlich aus der täglichen Teilnehmermenge des betreffenden Spiels gebildet.
- `both`-Änderungen sind atomar und müssen bei einem Teilfehler vollständig zurückrollen.
- Bestehende globale Zeiträume wurden bei der Migration identisch für beide Spiele übernommen; historische Präferenzen dürfen nicht aus Ergebnissen geraten werden.
- Kein generisches Modell für beliebig viele Spiele und kein Reminderstatus pro Spiel vorziehen.

### Kontextabhängige Ausreden ab Inkrement 11

- Verbindlich sind `docs/requirements/excuses.md`, ADR 0017 und der Paketplan unter `docs/increments/11-contextual-excuses.md`.
- Angebots- und Tagesvergleichslogik verwendet ausschließlich die historisch wirksame Teilnehmermenge des betroffenen Spiels; `player.active` ist keine zulässige Grundlage.
- Es gibt keine generative KI, keine externe Text-API und keine freie Template- oder Regelsprache zur Laufzeit.
- Der vollständige redaktionelle Katalog wird beim Start und in einem Katalogtest validiert; ein nicht vollständig auflösbares Template wird vollständig verworfen.
- Die Zufallsquelle wird injiziert, damit jede Auswahl reproduzierbar testbar bleibt.
- Der QuadWords-Einzelboardausreißer erfordert bei vier gelösten Boards einen eindeutigen schlechtesten Quadranten, mindestens acht Versuche und mindestens drei Versuche Abstand zum zweitschlechtesten Board.
- Für jedes `game_result` gibt es nach der Migration genau eine persistierte positive oder negative Erstentscheidung; Bestandsresultate werden als `NOT_OFFERED` markiert.
- Replay, Korrektur, Boardanreicherung und Recovery dürfen kein erstmaliges Angebot erzeugen.
- Tatsächlich gezeigte Optionen werden vor der ephemeren Ausgabe persistiert. Auswahl ist nur aus der aktuellen persistierten Runde und Kontextgeneration zulässig.
- Template-, Stil-, Themen- und gerenderter Text-Snapshot werden bei Auswahl gespeichert. Der gewählte Text wird niemals stillschweigend umformuliert.
- Stilnamen erscheinen ausschließlich ephemer. In der kanonischen Ergebnisnachricht steht nach Auswahl nur der Text, ohne Stil, Überschrift oder Ausredenlabel.
- Der Ergebnisautor ist der einzige berechtigte Actor. Nur der öffentliche Open-Klick validiert seine Event-Message-ID gegen die aktuelle kanonische Message; ephemere Folgeinteraktionen validieren Guild, Channel, Ergebnis-ID, Status, Ablauf, Generation, Runde und Position ausschließlich serverseitig.
- Eine Ausredeninteraction editiert die öffentliche Discord-Nachricht niemals direkt. Zustandsübergang und dauerhafter kanonischer Refresh-Auftrag werden atomar persistiert.
- Create und Edit der kanonischen Nachricht übertragen Embed und Action Rows gemeinsam; ein terminaler Ausredenzustand darf keinen veralteten Button zurücklassen.
- Dauerhafte kanonische Refresh-Recovery findet die aktuelle publizierte Quelle auch in `ORIGINAL_MESSAGE_DELETED` und `COMPLETED`; `SUPERSEDED` und nicht aktive Retirement-Fences dürfen nie wieder veröffentlicht werden.
- Paket 8B darf keine Fachlogik oder den Katalog `2026.08.04.1` ändern. PR #46 und Issue #42 bleiben bis zur dokumentierten realen Abnahme mit separater Testanwendung und Testdatenbank offen; `EXCUSES_ENABLED` bleibt produktiv `false`.
- Inkrement 11 führt kein allgemeines Kommentar-, Event- oder Plugin-Framework ein.

### Serienmodell

- Serien werden aus den persistierten Spielergebnissen und den historisch wirksamen Teilnahmezeiträumen abgeleitet und eindeutig benannt.
- Persönlich zu berechnen sind Aktivitätsserie, Komplettserie, GridWords-Lösungsserie, QuadWords-Lösungsserie und Perfektserie.
- Gemeinsam zu berechnen sind GridWords-Lösungsserie, QuadWords-Lösungsserie, Komplettserie und Perfektserie.
- Gemeinsame GridWords- und QuadWords-Lösungsserien setzen jeweils mindestens zwei Teilnehmer des betreffenden Spiels voraus.
- Persönliche sowie gemeinsame Komplett- und Perfektmetriken bleiben Zwei-Spiele-Metriken und verwenden ausschließlich Spieler, die am betreffenden Tag an beiden Spielen teilnehmen.
- Nicht anwendbare Tage werden nicht übersprungen und pausieren keine Serie, sondern bilden eine Kalendergrenze.
- GridWords- und QuadWords-Lösungsserien werden persönlich und gemeinsam vollständig unabhängig berechnet.
- Es gibt keine gemeinsame Aktivitätsserie.
- Maßgeblich sind die neun Serienarten und zusätzlich die Teilnehmermengen aus `docs/requirements/game-specific-participation.md`.

### Periodische Berichte

- Verbindlich sind `docs/requirements/periodic-reports.md` und ADR 0014.
- Ein Teilnahmetag ist nicht mit einem Aktivitätstag gleichzusetzen.
- Spielstatistiken verwenden getrennte historisch wirksame GridWords- und QuadWords-Teilnahmetage als Nenner.
- Aktivität verwendet Teilnahmetage an mindestens einem Spiel; Komplett und Perfekt verwenden ausschließlich Zwei-Spiele-Teilnahmetage.
- Gemeinsame Komplett- und Perfektnenner verwenden ausschließlich Tage mit mindestens zwei Zwei-Spiele-Teilnehmern.
- Statistik- und Serienwerte werden bis einschließlich Periodenende abgeleitet. Daten danach dürfen den Bericht nicht beeinflussen.
- Berechnete Reportwerte werden nicht als zweite fachliche Wahrheit persistiert.
- Erfolgreich veröffentlichte Berichte sind Snapshots und werden durch spätere Ergebnisse nicht automatisch editiert.
- Wochen- und Monatsbericht verwenden denselben transportneutralen Reporting-Kern.
- Keine Mentions, Gewinnerlogik, Ranglisten oder direkten Leistungsvergleiche in Berichten.
- Mehrseitenberichte bilden eine logische Delivery mit geordnet persistierten Message-IDs.
- Der Scheduler ist nur Trigger; Fälligkeit, Claims, Retry, NO_OP und Ablauf werden in PostgreSQL nachvollziehbar persistiert.
- Pro Berichtstyp wird höchstens die jüngste noch relevante versäumte Periode nachgeholt.
- Die gemeinsamen spielbezogenen Lösungsserien aus 10.2 erweitern Berichte nicht ohne gesonderte fachliche Entscheidung.
- Keine vorauseilende manuelle Report- oder Konfigurations-Command-Oberfläche in Inkrement 10.

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
- Serienänderungen benötigen getrennte Tests für alle neun definierten Serien, spielbezogene historische Teilnahmezeiträume, nicht anwendbare Tage und die Regel für den unvollständigen aktuellen Tag.
- Änderungen an spielbezogener Teilnahme benötigen symmetrische GridWords-only-/QuadWords-only-Fälle, `both`-Atomizität, Backfill, Konkurrenz, Reminder-Audience, Statusmenüs und getrennte Reportnenner.
- Reportänderungen benötigen getrennte Tests für Wochen-/Monatsperioden, Union-, spielbezogene und Zwei-Spiele-Teilnahmetage, alle persönlichen und gemeinsamen Kennzahlen, Periodenend-Stichtag, Catch-up, NO_OP und Pagination.
- Ausredenänderungen benötigen getrennte Tests für alle absoluten Schwellen und direkten Untergrenzen, den QuadWords-Boardabstand 2 gegenüber 3, boardlose Ergebnisse, mindestens zwei andere Tagesresultate, spielbezogene Teilnehmermengen, Cooldown, Wiederholungsschutz und injizierte Zufallsquelle.
- Ausredenpersistenz benötigt Backfill-, Constraint-, Auswahl-, Ablauf-, Korrektur-, Konkurrenz- und atomare Refreshtests gegen echtes PostgreSQL.
- Ausredeninteraktionen benötigen Autorisierungs-, Message-ID-, Generation-, Runde-, Position-, Ablauf-, Doppelklick-, Worker-Queue- und Restartfälle. Stil darf in keiner kanonischen Ausgabe erscheinen.
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
- PRs für Wochen-/Monatsberichte bleiben Draft, bis beide Berichtstypen real in Discord dargestellt und duplikatsicher geprüft wurden.
- Paketweise Inkremente verwenden den jeweils aktuellen Plan unter `docs/increments/`; keine späteren Pakete oder Folge-Issues vorziehen.
- Für Issue #42 gilt die Reihenfolge aus `docs/increments/11-contextual-excuses.md`; keine spätere Interaktions-, Persistenz- oder Katalogstufe provisorisch vorziehen.
- Der Implementierungs-PR für Inkrement 11 bleibt Draft, bis Standardbuild, PostgreSQL-Profil und reale Discord-Abnahme einschließlich Auswahl, Stil-Neuwurf, Ablauf, Neustart und Korrektur vollständig sind.

## 9. Abschlussbericht für Codex-Aufgaben

Am Ende immer knapp berichten:

- umgesetzter Umfang,
- wichtige Designentscheidungen innerhalb der vorgegebenen Architektur,
- geänderte Dateien,
- ausgeführte Befehle und konkrete Testergebnisse,
- nicht ausgeführte Tests mit Grund,
- verbleibende Risiken oder notwendige manuelle beziehungsweise CI-Prüfungen.

Nicht behaupten, ein Build, Test, Imagepush, Backup, Restore, Deployment, Rollback oder Discord-Smoke-Test sei erfolgreich gewesen, wenn er nicht tatsächlich ausgeführt wurde.
