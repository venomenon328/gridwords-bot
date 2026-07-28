# AGENTS.md

Diese Datei gilt für das gesamte Repository. Es gibt derzeit keine untergeordneten `AGENTS.md`-Dateien.

## 1. Vor jeder Änderung lesen

Lies vor der Implementierung mindestens:

1. `docs/anforderungsspezifikation.md` – verbindliche fachliche Anforderungen und Versionsgrenzen
2. `docs/architecture.md` – verbindliche Architektur, Modulgrenzen und Abläufe
3. `docs/development-guide.md` – Build, Tests, Secrets und Arbeitsweise
4. `docs/implementation-plan.md` – Reihenfolge der Inkremente
5. vorhandene ADRs unter `docs/adr/`, wenn die Aufgabe die dort behandelten Entscheidungen berührt

Bei Widersprüchen gilt:

1. aktueller expliziter Nutzerauftrag beziehungsweise GitHub-Issue,
2. `docs/anforderungsspezifikation.md`,
3. akzeptierte ADRs,
4. `docs/architecture.md`,
5. diese Datei,
6. übrige Dokumentation.

Widersprüche nicht stillschweigend auflösen. Im Ergebnisbericht benennen oder vor einer weitreichenden Änderung nachfragen.

## 2. Projektcharakter

- Kleiner, einzelner Discord-Bot als modularer Monolith
- Java 21, Spring Boot, Maven, JDA, PostgreSQL, Liquibase
- Genau ein konfigurierter Server, ein Channel und zwei Spieler in Version 1
- Keine Microservices, keine verteilte Queue, kein generisches Plugin-Framework
- Keine generative KI zur Laufzeit

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

### Datenbank

- Schemaänderungen ausschließlich über Liquibase.
- Hibernate `ddl-auto` bleibt `validate` oder `none`, niemals `update`/`create` in produktionsnaher Konfiguration.
- Fachliche Eindeutigkeiten durch Datenbank-Constraints absichern.
- Keine Testabhängigkeit von einer lokal laufenden Datenbank; Integrationstests verwenden Testcontainers.

## 4. Code- und Testregeln

- Bevorzuge kleine, benannte Typen und klare Kontrollflüsse gegenüber cleveren Abstraktionen.
- Keine vorauseilende Generalisierung für weitere Server, Spiele oder beliebig viele Nutzer.
- Keine Lombok-Abhängigkeit.
- Keine ungeprüften `Optional.get()`, keine leeren Catch-Blöcke, keine pauschalen `catch (Exception)` ohne fachliche Übersetzung oder Logging.
- Logs dürfen niemals Tokens, Passwörter, vollständige `.env`-Inhalte oder unnötige fremde Nachrichteninhalte enthalten.
- Neue fachliche Logik benötigt Tests.
- Parseränderungen benötigen Fixture-basierte Tests für Erfolg, Nicht-gelöst-Format und Fehlerfälle.
- Tests dürfen keine echte Discord-Verbindung öffnen.
- Zeitabhängige Tests verwenden eine feste `Clock`.
- Fehlerpfade und Idempotenz sind ebenso zu testen wie der Happy Path.

## 5. Standardbefehle

Vor Abschluss einer Implementierungsaufgabe ausführen:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Falls die Aufgabe Datenbankintegration betrifft, müssen die betreffenden Testcontainers-Tests Teil von `verify` sein. Ein manuell gestartetes PostgreSQL darf für den Build nicht erforderlich sein.

Für lokale manuelle Ausführung gelten die Befehle aus `README.md` und `docs/development-guide.md`.

## 6. Umgang mit Abhängigkeiten

- Keine Versionsnummer erfinden.
- Neue oder geänderte Versionen gegen Maven Central beziehungsweise die offizielle Projektdokumentation prüfen.
- Stabile Releases bevorzugen; keine Snapshot-, Milestone- oder Release-Candidate-Version ohne explizite Begründung.
- Neue Bibliotheken nur hinzufügen, wenn die Standardbibliothek oder bestehende Abhängigkeiten die Aufgabe nicht angemessen lösen.
- Bei einer neuen wesentlichen Bibliothek Nutzen, Alternativen und Betriebsfolgen im PR beschreiben.

## 7. Dokumentation und Architekturänderungen

- Fachliche Anforderungen nicht beiläufig im Code neu definieren.
- Eine Änderung an Modulgrenzen, Persistenzstrategie, Discord-Ersetzungsablauf, Scheduling oder Technologieauswahl erfordert vor der Implementierung ein neues beziehungsweise aktualisiertes ADR.
- Bestehende ADRs werden nicht rückwirkend umgeschrieben, wenn eine Entscheidung ersetzt wird; stattdessen neues ADR mit Verweis auf das abgelöste Dokument.
- README bleibt Einstiegsdokument und verlinkt auf detaillierte Dokumente, dupliziert sie aber nicht vollständig.

## 8. Git- und Aufgabenworkflow

- Arbeite auf dem im Auftrag genannten Branch.
- Keine Änderungen außerhalb des Auftragsumfangs.
- Keine direkte Änderung von `main`, sofern nicht ausdrücklich verlangt.
- Kleine logisch zusammengehörige Commits mit verständlichen Commit-Nachrichten.
- Bestehende Nutzeränderungen nicht zurücksetzen.
- Keine Secrets, lokalen Datenbanken, generierten Binärdateien oder IDE-Artefakte committen.

## 9. Abschlussbericht für Codex-Aufgaben

Am Ende immer knapp berichten:

- umgesetzter Umfang,
- wichtige Designentscheidungen innerhalb der vorgegebenen Architektur,
- geänderte Dateien,
- ausgeführte Befehle und konkrete Testergebnisse,
- nicht ausgeführte Tests mit Grund,
- verbleibende Risiken oder notwendige manuelle Prüfungen.

Nicht behaupten, ein Build, Test oder Discord-Smoke-Test sei erfolgreich gewesen, wenn er nicht tatsächlich ausgeführt wurde.