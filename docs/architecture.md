# Architektur des GridWords-Bots

**Status:** verbindliche Zielarchitektur  
**Gültig ab:** Projektgrundgerüst / Version 1  
**Verwandte Dokumente:** `anforderungsspezifikation.md`, `requirements/series-model.md`, `implementation-plan.md`, `adr/`

## 1. Architekturziel

Der Bot wird als **modularer Monolith** in einem einzigen Spring-Boot-Prozess entwickelt. Das System ist klein, soll aber bei Discord-Fehlern, Neustarts und doppelten Events keine Ergebnisse verlieren oder Nachrichten versehentlich mehrfach beziehungsweise vorzeitig löschen.

Die Architektur trennt konsequent:

- fachliches Modell,
- Anwendungsfälle,
- Parser,
- Discord-Anbindung,
- Persistenz,
- Scheduling und
- Konfiguration.

Es werden keine Microservices, kein externer Message Broker und kein universelles Plugin-System eingeführt.

## 2. Qualitätsziele

In Prioritätsreihenfolge:

1. **Daten- und Nachrichtensicherheit:** Kein gültiges Ergebnis darf durch einen Fehler bei der Discord-Ausgabe verloren gehen.
2. **Idempotenz:** Mehrfach zugestellte Events und Neustarts dürfen keine doppelten Datensätze oder Bot-Nachrichten erzeugen.
3. **Testbarkeit:** Fachliche Logik, Parser, Serien und Zeitregeln müssen ohne Discord und ohne lokal installierte Datenbank testbar sein.
4. **Nachvollziehbarkeit:** Ein Fehler muss anhand persistierter Zustände und strukturierter Logs rekonstruierbar sein.
5. **Eindeutige Fachsprache:** Aktivität, vollständige Erledigung und Lösungen der einzelnen Spiele dürfen nicht in unspezifischen Serienbegriffen vermischt werden.
6. **Einfachheit:** Nur Abstraktionen einführen, die eine konkrete Grenze oder einen konkreten Testnutzen haben.
7. **Erweiterbarkeit innerhalb der Spezifikation:** Version 2 und 3 sollen ohne Architekturbruch ergänzt werden können.

## 3. Systemkontext

Externe Systeme:

- **Discord Gateway:** liefert Nachrichten- und Änderungsereignisse.
- **Discord REST API über JDA:** sendet, bearbeitet, reagiert und löscht Nachrichten.
- **PostgreSQL:** persistiert Ergebnisse, Verarbeitungszustände, Statusnachrichten und Auslieferungen.
- **Gridgames:** wird nicht direkt angesprochen; der Bot verarbeitet ausschließlich von Nutzern geteilte Inhalte.
- **Dateisystem beziehungsweise einfacher Artefaktspeicher:** hält QuadWords-Rohbilder temporär für maximal 48 Stunden.

Der Bot benötigt keinen öffentlich erreichbaren HTTP-Endpunkt.

## 4. Logische Schichten und Abhängigkeitsrichtung

```text
┌──────────────────────────────────────────────────────────────┐
│ Adapter                                                      │
│ Discord/JDA · PostgreSQL/JPA · Scheduling · Dateispeicher    │
└───────────────────────┬──────────────────────────────────────┘
                        │ implementiert Ports
┌───────────────────────▼──────────────────────────────────────┐
│ Application                                                  │
│ Ergebnis verarbeiten · Status · Serien · Erinnerungen        │
│ Berichte · Orchestrierung und Transaktionsgrenzen            │
└───────────────────────┬──────────────────────────────────────┘
                        │ verwendet
┌───────────────────────▼──────────────────────────────────────┐
│ Domain                                                       │
│ Spieltag · Ergebnis · Tagesmerkmale · Serien · Parse-Ergebnis│
│ reine Java-Typen und fachliche Regeln                        │
└──────────────────────────────────────────────────────────────┘

Parser sind reine fachnahe Komponenten und dürfen nur von Domain-Typen
beziehungsweise kleinen Parser-Eingabetypen abhängen.
```

Die Abhängigkeiten zeigen ausschließlich nach innen. Domain und Application kennen weder JDA noch JPA-Entities.

## 5. Vorgesehene Paketstruktur

```text
de.venomenon.gridwordsbot
├── config
│   └── typisierte Konfiguration und Spring-Wiring
├── domain
│   ├── model
│   ├── parsing
│   ├── streak
│   └── time
├── application
│   ├── submission
│   ├── status
│   ├── streak
│   ├── reminder
│   └── report
├── port
│   ├── in
│   └── out
├── parser
│   ├── gridwords
│   └── quadwords
└── adapter
    ├── discord
    │   ├── inbound
    │   └── outbound
    ├── persistence
    ├── scheduling
    └── storage
```

Diese Struktur ist eine Zielrichtung, kein Auftrag, leere Klassen nur zur Erfüllung des Baums anzulegen. Pakete entstehen mit dem jeweiligen Inkrement.

## 6. Zentrale Domänentypen

Voraussichtlich benötigte reine Java-Typen:

- `GameType`: `GRIDWORDS`, `QUADWORDS`
- `GameDate`: optionaler Value Type um `LocalDate`, falls fachliche Validierung dadurch klarer wird
- `PlayerId`: Discord-User-ID als Value Type oder klar benannter `long`
- `ShareOutcome`: gelöst mit Versuchszahl oder nicht gelöst (`X`)
- `ParsedGameResult`: validiertes Ergebnis des Text-/Bildparsers
- `NormalizedBoard`: normalisierte Unicode-Darstellung
- `SubmissionKey`: Spieler + Spieltyp + Spieltag
- `PersonalDaySummary`: Aktivitätstag, kompletter Tag und perfekter Tag
- `SharedDaySummary`: gemeinsam kompletter und gemeinsam perfekter Tag
- `PersonalStreakSummary`: Aktivität, komplett, GridWords gelöst, QuadWords gelöst und perfekt
- `SharedStreakSummary`: gemeinsam komplett und gemeinsam perfekt
- `ParseResult`: unterscheidet `NotApplicable`, `Parsed` und `Invalid`

Die konkrete Aufteilung auf Records oder Value Types darf bei der Implementierung angepasst werden. Die sieben Serien dürfen jedoch nicht wieder zu generischen Feldern wie `playStreak` und `solveStreak` zusammengefasst werden.

Die Parser müssen zwischen diesen Fällen unterscheiden:

- **NotApplicable:** Nachricht ist kein Ergebnis dieses Spiels; kein Fehler und keine Nutzerreaktion.
- **Parsed:** vollständig validiertes Ergebnis.
- **Invalid:** Nachricht sieht wie ein passendes Share-Ergebnis aus, ist aber unvollständig oder widersprüchlich.

`null` oder Exceptions als reguläres Parser-Ergebnis sind zu vermeiden.

## 7. Ports und Anwendungsfälle

### 7.1 Eingangsports

Vorgesehene Use Cases:

- `ProcessSharedResultUseCase`
- `ReprocessSubmissionUseCase`
- `RefreshDailyStatusUseCase`
- `SendReminderUseCase`
- `GenerateWeeklyReportUseCase`
- `GenerateMonthlyReportUseCase`

Die Namen dürfen bei der Implementierung leicht angepasst werden, solange Verantwortlichkeiten und Grenzen erhalten bleiben.

### 7.2 Ausgangsports

Mindestens:

- `GameResultRepository`
- `SubmissionRepository`
- `DailyStatusRepository`
- `DeliveryRepository`
- `DiscordMessageGateway`
- `RawArtifactStore`
- `PlayerDirectory`

Java `Clock` wird direkt injiziert; ein eigener `ApplicationClock`-Port ist nicht nötig.

`DiscordMessageGateway` verwendet eigene anwendungsnahe DTOs. JDA-Typen verlassen den Discord-Adapter nicht.

## 8. Verarbeitung einer geteilten Ergebnisnachricht

### 8.1 Eingang

Der JDA-Listener:

1. prüft Server, Channel, Nutzer, Bot-/Webhook-Status,
2. kopiert nur benötigte Daten in ein unveränderliches `InboundDiscordMessage`,
3. delegiert an einen Application Executor beziehungsweise den Use Case,
4. führt keine längeren Datenbank- oder REST-Operationen auf dem JDA-Event-Thread aus.

Benötigte Eingangsdaten:

- Guild-ID
- Channel-ID
- Message-ID
- Author-ID und Anzeigename
- Inhalt
- relevante Attachment-Metadaten
- Discord-Zeitstempel

### 8.2 Parse-Pipeline

Eine kleine Parser-Registry ruft in fester Reihenfolge GridWords und QuadWords auf. Es wird kein generisches Plugin-System benötigt.

- Kopfzeile und Text werden immer zuerst ausgewertet.
- In Version 1 validiert GridWords zusätzlich das Unicode-Raster.
- In Version 1 validiert QuadWords nur, dass ein plausibler Bildanhang vorhanden ist.
- In Version 2 wird der Bildparser nach erfolgreichem Textparse ausgeführt.

Parser haben keinen Zugriff auf Datenbank oder Discord.

### 8.3 Fachliche Validierung

Nach dem Parse:

- Spieler muss konfiguriert sein.
- Spieltag muss heute oder gestern in `Europe/Berlin` sein.
- Share-Ergebnis und Maximalversuche müssen zum Spieltyp passen.
- Dauer muss gültig sein.
- Raster beziehungsweise Attachment muss die versionsabhängigen Anforderungen erfüllen.

## 9. Sichere und idempotente Nachrichtenersetzung

Discord und PostgreSQL teilen keine gemeinsame Transaktion. Deshalb wird die Verarbeitung als persistierter, fortsetzbarer Ablauf modelliert.

### 9.1 Verarbeitungszustände

Empfohlene Zustände:

```text
RECEIVED
VALIDATED
RESULT_STORED
CANONICAL_MESSAGE_PUBLISHED
ORIGINAL_MESSAGE_DELETED
COMPLETED
FAILED_RETRYABLE
FAILED_FINAL
```

Nicht jeder Zustand muss eine eigene Tabelle oder öffentliche Enum erhalten; die beobachtbaren Übergänge müssen jedoch persistiert sein.

### 9.2 Ablauf

1. **Event registrieren:** Quell-Message-ID idempotent erfassen.
2. **Parsen und validieren:** Ergebnis oder strukturierter Fehler.
3. **Transaktion A:** Ergebnis anhand `Spieler + Spieltyp + Spieltag` upserten und Zustand `RESULT_STORED` setzen.
4. **Serienansicht ableiten:** die betroffenen persönlichen und gemeinsamen Tagesmerkmale und Serien neu berechnen.
5. **Kanonisch veröffentlichen:** außerhalb der DB-Transaktion Discord-Nachricht senden.
6. **Transaktion B:** Bot-Message-ID speichern und Zustand `CANONICAL_MESSAGE_PUBLISHED` setzen.
7. **Original löschen:** nur nach erfolgreichem Schritt 6.
8. **Transaktion C:** Löschung und Abschluss persistieren.
9. **Tagesstatus aktualisieren:** Fehler hierbei dürfen das gespeicherte Ergebnis nicht zurückrollen.

### 9.3 Persistierte GridWords-Quellloeschung

Nach der bereits persistierten kanonischen Message-ID beansprucht ein kurzer, token-geschuetzter Persistence-Schritt die Loeschung der exakten `(channelId, sourceMessageId)`. Der Discord-Delete erfolgt ausserhalb einer Datenbanktransaktion. Sein Ergebnis wird nur vom Claim-Inhaber gespeichert.

- `DELETED` und `UNKNOWN_MESSAGE` fuehren zu `ORIGINAL_MESSAGE_DELETED` mit Zeitstempel und anschliessend zu `COMPLETED`.
- transiente Fehler bleiben mit Fehlerklasse `RETRYABLE` wiederaufnehmbar; permanente Rechte- oder Channel-Fehler mit `PERMANENT` lassen die Quelle sichtbar.
- eine nach `SUPERSEDED` verschobene Quelle ist erst berechtigt, wenn die neuere kanonische Veroeffentlichung bestaetigt ist.
- der Scheduler beschleunigt Wiederholungen, die Start-Recovery liest jedoch immer die Datenbank als Wahrheit.

Die konkrete Claim- und Fehlersemantik ist in ADR 0009 festgelegt.


Bei erneutem Event oder Neustart:
### 9.4 Wiederaufnahme


- Existiert bereits ein vollständig abgeschlossenes Submission-Objekt, geschieht nichts.
- Ist das Ergebnis gespeichert, aber keine Bot-Message-ID vorhanden, wird die Veröffentlichung erneut versucht.
- Ist eine Bot-Message-ID gespeichert, wird keine zweite kanonische Nachricht erzeugt; stattdessen wird der Zustand geprüft und gegebenenfalls nur die Originallöschung fortgesetzt.
- Ist das Original bereits gelöscht, darf ein nachträgliches Discord-Delete-Event das Ergebnis nicht entfernen.

### 9.5 Eindeutigkeiten

Mindestens:

- `source_message_id` eindeutig
- `player_id + game_type + game_date` eindeutig
- `game_date + reminder_stage` eindeutig
- höchstens eine aktive Tagesstatusnachricht pro Spieltag und Channel

Details und Fehlerfälle stehen in `adr/0002-idempotent-message-replacement.md`.

## 10. Transaktionsgrenzen

- Datenbanktransaktionen enthalten keine wartenden Discord-REST-Aufrufe.
- Ein Application Service steuert die einzelnen kurzen Transaktionen.
- Persistenzadapter verwenden pessimistische Sperren nur bei nachgewiesenem Bedarf; zunächst genügen Eindeutigkeitsconstraints und konfliktfeste Upserts beziehungsweise Retry-Behandlung.
- Status- und Berichtserzeugung lesen konsistente Daten, müssen aber keine globale serielle Ausführung erzwingen.

## 11. Persistenzmodell

Die fachliche Spezifikation definiert die benötigten Tabellen. Architektonisch gelten zusätzlich:

- JPA-Entities bleiben im Persistence-Adapter.
- Application und Domain arbeiten mit eigenen Modellen beziehungsweise Repository-Port-Typen.
- Liquibase ist alleinige Quelle des Schemas.
- Spieler sind in Version 1 Konfigurationsdaten und werden beim Start idempotent als Referenzdaten synchronisiert oder anhand ihrer Discord-ID direkt referenziert. Die konkrete Wahl wird mit der ersten Datenmigration festgelegt und per ADR ergänzt, falls sie die bestehende Architektur verändert.
- Rohtexte werden nur soweit gespeichert, wie für Diagnose und Neuverarbeitung erforderlich.
- Rohbilder werden über `RawArtifactStore` verwaltet und nach 48 Stunden gelöscht.
- Serien bleiben abgeleitete Werte aus `game_result`. Sie werden nicht als unkontrolliert fortgeschriebene Zähler zur Quelle fachlicher Wahrheit.

## 12. Discord-Ausgabe

Der Application Layer erzeugt ein transportneutrales Modell, beispielsweise:

```text
CanonicalResultMessage
- playerDisplayName
- optional playerAvatarUrl
- gameType
- gameDate
- solved
- attempts/maxAttempts
- duration
- normalizedBoard
- personalActivityStreak
- relevantGameSolveStreak
- optional personalCompleteStreak
- optional personalPerfectStreak
```

`relevantGameSolveStreak` bezeichnet bei GridWords ausschließlich die GridWords-Lösungsserie und bei QuadWords ausschließlich die QuadWords-Lösungsserie.

Der Application Layer entscheidet anhand des fachlichen Kontexts, welche optionalen Serienwerte ausgegeben werden. Der Discord-Adapter entscheidet nur über Embed-/Textdetails und JDA-Aufrufe. Er darf keine Serien berechnen und keine Ergebnisregeln erfinden.

Erwähnungen werden standardmäßig deaktiviert beziehungsweise explizit auf die vorgesehenen Nutzer begrenzt, um versehentliche `@everyone`- oder Rollen-Erwähnungen aus übernommenem Text zu verhindern.

## 13. Tagesstatus und Serien

Die verbindliche Semantik steht in `requirements/series-model.md`.

Architektonisch gelten:

- Serienberechnung ist eine reine fachliche Funktion über vorhandene Tagesergebnisse.
- Persönlich werden Aktivitätsserie, Komplettserie, GridWords-Lösungsserie, QuadWords-Lösungsserie und Perfektserie berechnet.
- Gemeinsam werden gemeinsame Komplettserie und gemeinsame Perfektserie berechnet.
- Eine gemeinsame Aktivitätsserie existiert nicht.
- Jede Serie hat eine eigene Tagesbedingung und behandelt den noch unvollständigen aktuellen Tag separat.
- Nachträge für gestern führen zu einer Neuberechnung aller möglicherweise betroffenen Serien.
- Der Tagesstatus ist eine abgeleitete Ansicht, nicht die Quelle fachlicher Wahrheit.
- Kann eine Statusnachricht nicht aktualisiert werden, bleiben Ergebnisse und Serien korrekt gespeichert.
- Eine manuell gelöschte Statusnachricht kann später neu aufgebaut werden.

Ein möglicher interner Rechner erhält chronologisch geordnete Tagesergebnisse und eine explizite Serienbedingung. Wiederverwendung der Iterationslogik ist erwünscht; die fachlichen Bedingungen und Ergebnisfelder müssen trotzdem eindeutig benannt bleiben.

## 14. Scheduling

Scheduling ist ein Adapter, der Anwendungsfälle zu geplanten Zeitpunkten aufruft.

Anforderungen:

- `Clock` und `ZoneId` explizit verwenden.
- Sommer-/Winterzeit korrekt behandeln.
- Auslieferung über persistierte Delivery-Datensätze idempotent absichern.
- Nach Neustart prüfen, ob eine fällige, noch nicht ausgelieferte Erinnerung nachzuholen ist.
- Version 1 liest Uhrzeiten beim Start aus Konfiguration.
- Die interne Planung soll spätere Änderungen per Slash-Command in Version 3 ermöglichen, ohne fachliche Services umzubauen.

Für den aktuellen Umfang wird Spring `TaskScheduler` bevorzugt. Quartz wird nur bei einem nachgewiesenen Bedarf eingeführt.

## 15. Nebenläufigkeit

- JDA-Ereignisse dürfen parallel eintreffen.
- Verarbeitung derselben Quellnachricht und desselben `SubmissionKey` muss konfliktfest sein.
- Ein begrenzter Application Executor verhindert, dass langsame Discord- oder DB-Aufrufe den JDA-Thread blockieren.
- Keine unkontrollierten `CompletableFuture.runAsync`-Aufrufe auf dem globalen Pool.
- Jede asynchrone Operation besitzt Fehlerbehandlung und einen beobachtbaren Abschlusszustand.

## 16. Fehlerklassifikation

Mindestens:

- `NOT_APPLICABLE`: kein Share-Ergebnis
- `INVALID_SHARE_FORMAT`: passend, aber ungültig
- `OUTSIDE_ALLOWED_DATE_WINDOW`
- `UNSUPPORTED_ATTACHMENT`
- `PERSISTENCE_FAILURE`
- `CANONICAL_PUBLISH_FAILURE`
- `ORIGINAL_DELETE_FAILURE`
- `STATUS_UPDATE_FAILURE`
- `IMAGE_PARSE_FAILURE`

Nutzernahe Fehlermeldungen bleiben knapp. Technische Details gehören in strukturierte Logs, ohne Secrets oder unnötige vollständige Chat-Inhalte.

## 17. Testarchitektur

### Unit-Tests

- Parser mit Text-Fixtures
- alle sieben Serien mit fester `Clock`
- Aktivitäts-, Komplett- und perfekte Tagesmerkmale
- separater unvollständiger aktueller Tag je Serie
- Datumsfenster und Vortagsnachtrag
- kanonisches Nachrichtenmodell
- Reminder-Entscheidung

### Application-Tests

Mit In-Memory-Fakes für Ports:

- Happy Path
- doppeltes Event
- Publish-Fehler: Original bleibt bestehen
- DB gespeichert, Publish nach Neustart erneut
- Bot-Nachricht gespeichert, Originallöschung wird fortgesetzt
- Statusfehler rollt Ergebnis nicht zurück

### Adapter-Tests

- Persistence-Adapter mit PostgreSQL-Testcontainer und echten Liquibase-Migrationen
- Discord-Adapter ohne Netzwerk, über Mock/Fake der schmalen JDA-Grenze
- Bildparser gegen versionierte Originalbilder

### Architekturtests

Nach Stabilisierung des Grundgerüsts wird ArchUnit empfohlen, um mindestens zu prüfen:

- `domain..` hängt nicht von Spring, JDA oder JPA ab.
- `application..` hängt nicht von `adapter..` ab.
- JDA-Typen befinden sich nur unter `adapter.discord..` und Wiring-Konfiguration.

ArchUnit ist keine Voraussetzung für den allerersten grünen Build, soll aber vor der ersten fachlichen Implementierung oder mit deren erstem Inkrement eingeführt werden.

## 18. Bewusst nicht gewählte Ansätze

- **Microservices:** erhöhen Deployment- und Fehlerkomplexität ohne Nutzen.
- **Event Sourcing:** unnötig für den Datenumfang; persistierte Verarbeitungszustände reichen.
- **Externer Message Broker:** kein Bedarf bei zwei Nutzern und einem Prozess.
- **Universelles Parser-Plugin-System:** zwei bekannte Spiele rechtfertigen keine dynamische Erweiterungsplattform.
- **Direkte JDA-Nutzung in Services:** erschwert Tests und bindet Fachlogik an Discord.
- **Lange DB-Transaktion über Discord-Aufrufe:** riskant und technisch nicht atomar.
- **Löschen vor Wiederveröffentlichung:** kann Nutzerergebnisse dauerhaft verlieren.
- **H2 als Ersatz für PostgreSQL-Integrationstests:** kann Unterschiede bei Constraints, SQL und Typen verschleiern.
- **Eine generische persönliche Lösungsserie:** verschleiert, welches Spiel gelöst wurde.
- **Gemeinsame Aktivitätsserie:** ist zu schwach und kann unterschiedliche Einzelaktivitäten fälschlich als gemeinsame Routine darstellen.

## 19. Kriterien für Architekturänderungen

Ein ADR ist erforderlich, wenn eine Änderung betrifft:

- Schichten oder Abhängigkeitsrichtung,
- Datenbanktechnologie oder ORM-Strategie,
- Nachrichtenersetzungs- und Retry-Modell,
- Scheduler-Technologie,
- Hosting-/Deployment-Grundmodell,
- neue externe Infrastruktur,
- Speicherung personenbezogener oder vollständiger Chatdaten,
- Wechsel von reiner Java-Bildverarbeitung zu nativen Bibliotheken.

Kleine Klassen- oder Methodennamenänderungen und fachliche Präzisierungen innerhalb des bestehenden Modells benötigen kein ADR. Fachliche Änderungen müssen jedoch in der Anforderungsspezifikation oder unter `docs/requirements/` dokumentiert werden.