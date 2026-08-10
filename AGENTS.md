# AGENTS.md

Diese Datei gilt für das gesamte Repository. Untergeordnete `AGENTS.md`-Dateien können ihren eigenen Bereich ergänzen.

## 1. Dokumentautorität

Vor einer Änderung mindestens den aktuellen Auftrag und die betroffenen Dokumente lesen:

- Fachsemantik: [`docs/product/`](docs/product/overview.md)
- technische Struktur: [`docs/architecture/`](docs/architecture/overview.md)
- Entscheidungen und Status: [`docs/adr/`](docs/adr/README.md)
- lokale Arbeitsweise und Tests: [`docs/development/`](docs/development/setup.md)
- Produktion und Recovery: [`docs/operations/`](docs/operations/README.md)

Bei Widersprüchen gilt:

1. aktueller ausdrücklicher Nutzerauftrag beziehungsweise GitHub-Issue,
2. `docs/product/`,
3. `docs/architecture/`,
4. akzeptierte, nicht abgelöste ADRs,
5. `docs/development/` und `docs/operations/`,
6. diese Datei.

`docs/history/` ist nicht normativ. Echte Widersprüche aktueller gleichrangiger Quellen nicht stillschweigend auflösen, sondern als Blocker benennen.

## 2. Projekt und Grenzen

- Kleiner Discord-Bot als modularer Monolith
- Java 21, Spring Boot, Maven, JDA, PostgreSQL und Liquibase
- genau ein konfigurierter Server und Channel; dynamische Spieler
- fachliche Zeitzone `Europe/Berlin`
- keine Microservices, verteilte Queue, generative KI zur Laufzeit oder generisches Plugin-/Event-Framework
- Docker Desktop und Compose dürfen für Persistenz-, Integrations-, Container- und Smoke-Tests vorausgesetzt werden
- Produktionsziel ist ein containerisierter Debian-13-VPS gemäß ADR 0013

Einfachheit, Nachvollziehbarkeit und robuste Fehlerbehandlung gehen vor vorauseilender Abstraktion.

## 3. Architekturregeln

- Fachkern und Application Services kennen keine JDA-Typen.
- Der Fachkern kennt weder Spring, JPA/Hibernate noch Discord.
- Parser sind deterministisch und frei von Datenbank-, Discord- und Netzwerkzugriffen.
- Discord-, Datenbank-, Scheduler-, Katalog- und Observability-Code sind Adapter an klaren Ports.
- Persistenzmodelle werden nicht ungeprüft als öffentliche Domänenmodelle verwendet.
- Konfiguration wird typisiert gebunden; keine verstreuten Environment-Zugriffe.
- Der JDA-Listener filtert, kopiert unveränderliche Eingaben und delegiert; längere Arbeit läuft nicht auf dem Event-Thread.
- Nur der konfigurierte Server/Channel und menschliche Nutzer werden verarbeitet; Bots und Webhooks werden ignoriert.

Eine fremde Share-Nachricht wird ausschließlich in dieser wiederaufnehmbaren Reihenfolge ersetzt:

1. parsen und validieren,
2. idempotent persistieren,
3. kanonische Bot-Nachricht veröffentlichen,
4. Message-ID persistieren,
5. Original löschen,
6. Abschluss persistieren.

Fachlicher Code verwendet einen injizierten `Clock`. Zwischen 00:00 und 05:59:59 Uhr sind heute und gestern zulässig, ab 06:00 nur heute. Das gilt auch für Korrektur, Retry, Replay und Recovery normaler Nutzervorgänge; nur vollständig terminale Idempotenz und getrennte stille Wartungswege sind davon ausgenommen.

Historisch wirksame, spielbezogene Teilnahme und `game_result` sind kanonische Quellen. `player.active`, Rekordzustände, Achievement-Awards, Status- und Discord-Nachrichten sind Projektionen. Zusammengehörige `both`-Teilnahmeänderungen sowie fachlicher Zustand und dauerhafter Delivery-Auftrag werden atomar persistiert.

## 4. Datenbank und lokale Infrastruktur

- Schemaänderungen ausschließlich über Liquibase.
- Hibernate `ddl-auto` bleibt `validate` oder `none`.
- Fachliche Eindeutigkeiten durch Datenbank-Constraints absichern.
- Der Standardbuild bleibt ohne Datenbank und Container-Runtime ausführbar.
- PostgreSQL-Integration läuft über das Profil `database-integration`; H2 ist kein Ersatz.
- Persistenz-, Liquibase-, Claim-, Konkurrenz- oder Recoveryänderungen benötigen echte PostgreSQL-Integration lokal und in CI.
- Testcontainers darf nur in den dafür vorgesehenen Profilen initialisiert werden.

## 5. Code und Tests

- Kleine, benannte Typen und klare Kontrollflüsse bevorzugen.
- Keine Lombok-Abhängigkeit, ungeprüften `Optional.get()`, leeren Catch-Blöcke oder pauschalen `catch (Exception)` ohne Übersetzung beziehungsweise Logging.
- Logs enthalten keine Tokens, Passwörter, vollständigen Environment-Dateien, Claim-Tokens oder unnötigen fremden Nachrichteninhalt.
- Neue fachliche Logik benötigt Happy-Path-, Fehler-, Idempotenz- und Konkurrenztests im angemessenen Umfang.
- Parseränderungen benötigen Fixturetests für Erfolg, Nicht-gelöst und Fehlerfälle.
- Zeitabhängige Tests verwenden einen festen `Clock`.
- Tests öffnen keine echte Discord-Verbindung.
- Persistenz- und Recoverypfade werden gegen echtes PostgreSQL geprüft.
- Container-, Backup-, Restore- und Deploymentskripte benötigen isolierte Happy-Path-, Fehler- und Wiederholungstests.

Vor Abschluss jeder Änderung:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Bei Datenbankintegration zusätzlich:

```bash
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Bei Ausredenkatalog- oder Contentänderungen zusätzlich:

```bash
python tools/build_excuse_catalog.py --check
```

Bei Dokumentationsänderungen zusätzlich:

```bash
python tools/check_markdown_links.py
```

Weitere Gates stehen in [`docs/development/testing.md`](docs/development/testing.md).

## 6. Produktion

- Das Produktionsimage läuft als Nicht-Root-Benutzer und enthält keine Quellen, Caches, Secrets oder privaten Schlüssel.
- PostgreSQL und Managementports werden nicht öffentlich veröffentlicht.
- Produktive Secrets werden nur serverseitig mit restriktiven Rechten bereitgestellt.
- Deployment verwendet explizite unveränderliche Tags; niemals `latest`.
- Vor jedem Deployment wird ein validiertes Backup erstellt.
- Application-Rollback und Datenbank-Restore sind getrennte Vorgänge.
- Produktionsskripte validieren Eingaben, brechen bei Fehlern ab und melden Teilfehler eindeutig.
- Kein automatisches Produktionsdeployment aus GitHub Actions.

Aktive Prozeduren stehen ausschließlich unter [`docs/operations/`](docs/operations/README.md).

## 7. Abhängigkeiten und Dokumentation

Versionen nicht erfinden. Neue oder geänderte Versionen gegen Maven Central beziehungsweise offizielle Projektdokumentation prüfen; stabile Releases bevorzugen. Neue Bibliotheken nur hinzufügen, wenn Standardbibliothek oder vorhandene Abhängigkeiten nicht genügen, und Nutzen, Alternativen sowie Betriebsfolgen dokumentieren.

Fachliche Regeln nicht beiläufig im Code neu definieren. Änderungen an Modulgrenzen, Persistenzstrategie, Discord-Ersetzung, Scheduling, Testinfrastruktur, Deployment oder Technologie benötigen vor der Implementierung ein neues oder aktualisiertes ADR. Abgelöste ADRs werden nicht rückwirkend umgeschrieben, sondern sichtbar ersetzt.

Aktuelle Wahrheit gehört in `docs/product/` beziehungsweise `docs/architecture/`, ausführbare Abläufe in `docs/development/` oder `docs/operations/`, abgeschlossene Pläne und Abnahmen in `docs/history/` und redaktionelle Buildquellen in `content/`. README-Dateien verlinken auf Details, statt sie zu duplizieren.

## 8. Git und Abschluss

- Auf dem beauftragten Branch arbeiten; `main` nicht direkt ändern, sofern nicht ausdrücklich verlangt.
- Keine Änderungen außerhalb des Auftragsumfangs und keine fremden Änderungen zurücksetzen.
- Keine Secrets, lokalen Datenbanken, Dumps, Backups, Binärartefakte oder IDE-Dateien committen.
- Kleine logisch zusammengehörige Commits mit verständlichen Nachrichten erstellen.
- Pull Requests nur dann aus Draft nehmen, wenn die vereinbarten technischen und manuellen Gates vollständig erfüllt sind.

Am Ende knapp berichten:

- umgesetzter Umfang und wichtige Designentscheidungen,
- geänderte Dateien beziehungsweise Bereiche,
- ausgeführte Befehle mit konkreten Ergebnissen,
- nicht ausgeführte Tests mit Grund,
- verbleibende Risiken, Konflikte oder notwendige manuelle/CI-Prüfungen.

Nie behaupten, Build, Test, Push, Backup, Restore, Deployment, Rollback oder Discord-Smoke sei erfolgreich gewesen, wenn er nicht tatsächlich ausgeführt wurde.
