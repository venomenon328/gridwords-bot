# Inkrement 12 – Rekorde und Rekordmeldungen

**Status:** Umsetzung läuft; Pakete 12.1 bis 12.8 abgeschlossen; Paket 12.9 in Umsetzung  
**Stand:** 7. August 2026  
**Umbrella-Issue:** #58  
**Fachliche Grundlage:** [`../requirements/records.md`](../requirements/records.md)  
**Architekturentscheidung:** [ADR 0018](../adr/0018-record-state-events-and-reconciled-announcements.md)

## 1. Ziel

Inkrement 12 führt historisch korrekte persönliche, serverweite individuelle und gemeinsame Rekorde ein. Es umfasst:

- drei Ergebnisrekordmetriken je Spiel,
- positive Rekorde aller bestehenden persönlichen und gemeinsamen Serien,
- negative Serienrekorde für X-Durststrecken und Tage ohne perfekten Tag,
- stille Initialisierung aus der vollständigen Serverhistorie,
- seltene, aggregierte und korrigierbare Discord-Meldungen,
- einen ephemeren `/records`-Command,
- einen stabilen fachlichen Ereignis- und Persistenzkern als spätere Grundlage für Achievements.

Die Umsetzung bleibt ein modularer Monolith. Es entstehen weder eine generische Regelmaschine noch ein externer Message Broker oder ein universelles Messaging-Framework.

## 2. Verbindlicher Umfang

### Ergebnisrekorde je GridWords und QuadWords

- wenigste Versuche; Dauer als Tie-Breaker,
- schnellste erfolgreiche Lösung,
- langsamste erfolgreiche Lösung,
- jeweils persönlich und serverweit individuell.

### Positive Serienrekorde

- persönliche und serverweite individuelle Rekorde für Aktivität, komplett, GridWords gelöst, QuadWords gelöst und perfekt,
- gemeinsame Rekorde für gemeinsam GridWords gelöst, gemeinsam QuadWords gelöst, gemeinsam komplett und gemeinsam perfekt.

### Negative Serienrekorde

- persönliche und serverweite individuelle GridWords-Durststrecke,
- persönliche und serverweite individuelle QuadWords-Durststrecke,
- persönliche und serverweite individuelle Serie ohne perfekten Tag.

### Meldungen und Commands

- Live-Meldung beim erstmaligen strikten Übertreffen,
- Abschlussmeldung bei Rekord, Gleichstand oder relativem Near Miss,
- Aggregation zusammengehöriger Fakten,
- Edit beziehungsweise Delete bei Korrekturen,
- `/records` mit `game` und optionalem `user`; die persönliche Sicht eines anderen Nutzers ist Administratoren vorbehalten.

## 3. Nicht Bestandteil

- meiste Versuche,
- langsamstes ungelöstes Spiel,
- QuadWords-Einzelboardrekorde,
- öffentliche Ergebnisgleichstände,
- vollständige Rekordchronik,
- Ranglisten oder Gewinnerlogik,
- Discord-konfigurierbare Rekorddefinitionen,
- Achievement-System,
- generische Kommentar-, Event- oder Plugin-Plattform.

## 4. Architekturgrenzen für alle Pakete

Alle Pakete müssen diese Regeln einhalten:

1. `game_result` und historische Teilnahmezeiträume bleiben kanonische Quelle.
2. Serienläufe werden abgeleitet und nicht als zweite fortlaufende Zählerwahrheit geführt.
3. Domain und Evaluatoren kennen weder JDA noch Persistenzentitäten.
4. Discord-I/O findet nicht innerhalb von Datenbanktransaktionen statt.
5. Liquibase bleibt einzige Quelle für Schemaänderungen.
6. PostgreSQL-Verhalten wird mit echtem PostgreSQL geprüft; H2 ersetzt diese Tests nicht.
7. Zeitlogik verwendet `LocalDate`, `LocalTime`, `ZoneId` und injizierte `Clock`.
8. Konkurrenz, Retry, Replay und Restart müssen idempotent bleiben.
9. Öffentliche Meldungen entstehen erst nach abgeschlossenem Bootstrap der aktiven Definitionsversion.
10. Gemeinsame Hilfskomponenten dürfen konkrete Duplikation reduzieren, aber keine vorauseilende Plattform bilden.

## 5. Paketübersicht

| Paket | Inhalt | Abhängigkeit |
|---|---|---|
| 12.1 | Rekorddomäne und Definitionskatalog | – |
| 12.2 | Reiner Ergebnisrekord-Evaluator | 12.1 |
| 12.3 | Serienlaufanalyse und Abschlussklassifikation | 12.1 |
| 12.4 | Persistenzschema und PostgreSQL-Adapter | 12.1–12.3 |
| 12.5 | Bootstrap und konkurenzsicherer Record-State-Service | 12.2–12.4 |
| 12.6 | Live-Integration für Submission und Korrektur | 12.5 |
| 12.7 | Tagesabschluss, Teilnahmegrenzen und 06:00-Cutoff | 12.3–12.6 |
| 12.8 | Discord-Renderer, Delivery und Reconciliation | 12.4–12.7 |
| 12.9 | Ephemerer `/records`-Command | 12.5, 12.8 |
| 12.10 | End-to-End-Härtung, Abnahme und Releasevorbereitung | 12.1–12.9 |

Jedes Paket soll einen einzeln reviewbaren Pull Request bilden. Ein Paket darf nur zusätzliche refaktorierende Änderungen enthalten, wenn sie für seinen Umfang unmittelbar notwendig und durch Tests abgesichert sind.

---

# Paket 12.1 – Rekorddomäne und Definitionskatalog

**Status:** umgesetzt  
**Empfohlener Branch:** `feature/12-1-record-domain`

## Ziel

Ein reiner, transportneutraler Rekordkern beschreibt alle Definitionsschlüssel, Vergleichswerte, Scopes, Quellen, Ereignistypen und Meldungsschwellen aus `records-v1`.

## Lieferumfang

- Paket `domain.record` oder fachlich gleichwertige klare Paketgrenze,
- stabile Definitionsschlüssel für alle Ergebnis- und Serienrekorde,
- getrennte `definitionVersion` und technische Lock-Version,
- typisierte Vergleichswerte:
  - Ergebniswert mit Versuchen und Dauer,
  - Dauerwert,
  - Serienlänge und Serienzeitraum,
- explizite Comparatoren:
  - Versuchszahl/Dauer aufsteigend,
  - Dauer aufsteigend,
  - Dauer absteigend,
  - Serienlänge absteigend,
- Scope-Typen für persönlich, serverweit individuell und gemeinsam,
- Quellreferenzen für Ergebnis und Serienlauf ohne Persistenz- oder JDA-Typen,
- Verarbeitungsursprünge wie Live-Submission, normale Korrektur, Day Close, Bootstrap, Replay, Import und administrative Reparatur,
- Ereignistypen und Gültigkeitsstatus,
- zentrale Berechnung des Near-Miss-Fensters,
- codebasierter vollständiger `records-v1`-Katalog mit Startvalidierung.

## Tests

Mindestens:

- vollständige und eindeutige Definitionsschlüssel,
- korrekte Richtung und Tie-Breaker aller Comparatoren,
- `ceil(10 %)`-Grenzen einschließlich 7, 10, 15, 30, 80 und 120 Tagen,
- Scope- und Quelleninvarianten,
- keine Ergebnisdefinition akzeptiert `X`,
- kein einzelnes QuadWords-Board ist Definition des Katalogs,
- Definitionskatalog ist deterministisch und vollständig.

## Definition of Done

- reine Tests ohne Spring, Discord und Datenbank,
- keine freie Regel-DSL,
- keine leeren Achievement-Abstraktionen,
- Fachbegriffe entsprechen exakt `records.md`.

---

# Paket 12.2 – Reiner Ergebnisrekord-Evaluator

**Status:** umgesetzt  
**Empfohlener Branch:** `feature/12-2-result-record-evaluator`

## Ziel

Ein reiner Evaluator entscheidet aus einem Kandidaten und einem transportneutralen historischen Snapshot, ob persönliche oder serverweite Ergebnisrekordzustände initialisiert, verbessert oder unverändert bleiben und ob eine Meldung grundsätzlich zulässig ist.

## Lieferumfang

- transportneutraler Snapshot geeigneter gelöster Ergebnisse,
- persönliche Auswertung ab fünf früheren gelösten Ergebnissen,
- serverweite Auswertung ab zehn früheren gelösten Ergebnissen von mindestens zwei Spielern,
- Kandidat wird aus der Mindestbasis ausgeschlossen,
- stille Initialisierung bei fehlendem bisherigen Zustand,
- striktes Übertreffen und stille Gleichstände,
- deterministische erste Quelle bei Gleichwertigkeit,
- gleichzeitige Auswertung aller drei Definitionen eines Spiels,
- fachliches Resultat mit vorherigem Wert, neuem Wert, Quelle, Halter und Meldungsfähigkeit,
- explizite Abgrenzung öffentlich stiller Ursprünge.

## Tests

Mindestens:

- weniger Versuche schlägt immer mehr Versuche,
- gleiche Versuche verwenden Dauer als Tie-Breaker,
- schnellste und langsamste Lösung ignorieren Versuchszahl,
- identische Dauer bleibt Gleichstand,
- `X` wird verworfen,
- fünfte versus sechste persönliche Lösung,
- zehn frühere Serverwerte nur eines Spielers reichen nicht,
- zehn frühere Werte von zwei Spielern reichen,
- Bootstrap und Replay ändern gegebenenfalls Zustand, erzeugen aber keine Meldung,
- mehrere Rekordfakten einer Submission bleiben als ein aggregierbarer Evaluationssatz erhalten.

## Definition of Done

- Evaluator bleibt rein und deterministisch,
- keine Repository-Abfragen innerhalb des Comparators,
- keine Discord-Strings in der Fachlogik,
- keine künstliche Gesamtpunktzahl.

---

# Paket 12.3 – Serienlaufanalyse und Abschlussklassifikation

**Status:** umgesetzt  
**Empfohlener Branch:** `feature/12-3-record-series-analysis`

## Ziel

Positive und negative Serienläufe werden aus kanonischen Tageszuständen und historischen Teilnahmezeiträumen deterministisch abgeleitet. Der Kern erkennt Live-Überschreitungen, Gleichstände, Near Misses und endgültige Abschlüsse.

## Lieferumfang

- Wiederverwendung beziehungsweise gezielte Erweiterung der bestehenden Serien-Tagesbedingungen,
- transportneutrale Identität eines Serienlaufs aus Typ, Scope, Spieler und Startdatum,
- Ableitung aller fünf persönlichen positiven Serien,
- Ableitung aller vier gemeinsamen positiven Serien,
- Ableitung von GridWords- und QuadWords-X-Durststrecken,
- Ableitung der Serie ohne perfekten Tag,
- korrekte Ein-Spiel-/Zwei-Spiele-Teilnahmegrenzen,
- Unterscheidung laufend, sofort beendet und beim Day Close beendet,
- genau eine Überschreitung je Lauf und Rekorddefinition,
- getrennte persönliche, serverweite individuelle und gemeinsame Abschlussklassifikation,
- Gleichstand nur am Ende meldungsfähig,
- Referenzrekord unter Ausschluss des Kandidatenlaufs,
- relative Near-Miss-Regel ohne Drei-Tage-Cap,
- öffentliche Längenschwellen,
- transportneutrale Reconciliation-Fakten bei geteilten, verbundenen oder verschobenen Läufen.

## Tests

Mindestens:

- alle bestehenden verbindlichen Serienfälle bleiben grün,
- `X` beendet die passende positive Lösungs-/Perfektserie sofort,
- fehlendes Ergebnis beendet erst zum logischen 06:00-Abschluss,
- erfolgreiche Lösung beendet Durststrecke sofort,
- fehlendes Ergebnis trennt Durststrecken,
- nicht perfekter Zwei-Spiele-Tag verlängert die negative Serie,
- perfekter Tag beendet sie sofort,
- Ein-Spiel-Teilnahme ist Grenze und keine Pause,
- wechselnde Teilnehmermengen in gemeinsamen Serien,
- persönlicher Rekord vor serverweitem Rekord im selben Lauf,
- laufender Gleichstand bleibt still,
- abgeschlossener Gleichstand wird klassifiziert,
- 72 von 80 Tagen ist Near Miss,
- Kandidatenlauf wird nicht mit sich selbst verglichen,
- Korrektur kann Läufe teilen und verbinden,
- Sommer-/Winterzeit beeinflusst Kalendertagesfolgen nicht.

## Definition of Done

- keine persistierten mutierten Seriencounter,
- keine Vervielfältigung widersprüchlicher Tagesbedingungen,
- reine Fachtests ohne Discord und Datenbank,
- alte Serienprojektionen und Reports bleiben fachlich unverändert.

---

# Paket 12.4 – Persistenzschema und PostgreSQL-Adapter

**Status:** umgesetzt  
**Empfohlener Branch:** `feature/12-4-record-persistence`

## Ziel

ADR 0018 wird mit der nächsten freien Liquibase-Migration und kleinen, expliziten Ports umgesetzt.

## Lieferumfang

- Tabellen beziehungsweise gleichwertige normalisierte Modelle für:
  - aktuellen Rekordzustand,
  - Rekordereignisse,
  - Bootstrap-/Rebuild-Status,
  - logische Rekordmeldungen,
  - Zuordnung Meldung zu Ereignisfakten,
  - geordnete Discord-Message-IDs bei Mehrteiligkeit,
- fachliche Unique Keys und Check Constraints,
- technische Lock-Version,
- Gültigkeits-, Supersession- und Invalidierungsdaten,
- Claim-, Lease-, Retry-, Fingerprint- und Fehlerfelder der Delivery,
- kleine Outbound-Ports für State, Event, Bootstrap und Announcement,
- primäre PostgreSQL-Adapter,
- vollständiger Liquibase-Neuaufbau und Upgrade vom aktuellen Produktivschema,
- keine Hibernate-Schemaerzeugung.

## Tests

Mindestens:

- Schema-Neuaufbau mit echtem PostgreSQL,
- Upgrade des bisherigen Schemas ohne Datenverlust,
- Unique Key pro Definition/Version/Scope,
- konkurrierende Erstinitialisierung kann keinen Doppelzustand erzeugen,
- Event-Idempotenzschlüssel verhindert Duplikate,
- Announcement-Idempotenzschlüssel verhindert doppelte logische Deliveries,
- geordnete Message-IDs bleiben stabil,
- invalidierte Ereignisse bleiben auditierbar,
- Lease- und Claim-Übergänge sind tokengebunden,
- Constraint-Verletzungen werden in stabile Adapterfehler übersetzt.

## Definition of Done

- Standardbuild bleibt ohne Datenbank grün,
- PostgreSQL-Profil prüft reale SQL-Semantik,
- Migration ist vorwärtskompatibel und rollback-planbar,
- keine fachliche Comparator-Logik in SQL versteckt.

---

# Paket 12.5 – Bootstrap und konkurenzsicherer Record-State-Service

**Status:** umgesetzt  
**Empfohlener Branch:** `feature/12-5-record-bootstrap-state`

## Ziel

Die vollständige Serverhistorie wird idempotent in korrekte `records-v1`-Zustände überführt. Ein transaktionaler Application-Service aktualisiert Rekordstände ohne Lost Updates.

## Lieferumfang

- historischer Bootstrap pro Guild und Definitionsversion,
- Claim, Lease, Retry und Restart-Fortsetzung des Bootstraps,
- stille `RECORD_INITIALIZED`-Auditanker,
- Erkennung bereits laufender Rekordserien und konsumierter Schwellen,
- keine öffentliche historische Chronik,
- atomarer State-Update-Pfad mit Lock oder Compare-and-Set,
- kontrollierte Neuanlage bei konkurrierendem Unique-Konflikt,
- Rebuild-Pfad für neue Definitionsversionen,
- gezielte vollständige Neuberechnung eines betroffenen Rekordzustands nach Invalidierung,
- Read-Port für aktuelle Zustände,
- globale Ankündigungssperre bis Bootstrap erfolgreich abgeschlossen ist.

## Tests

Mindestens:

- leerer Server,
- vollständige Historie mit persönlichen, serverweiten und gemeinsamen Zuständen,
- wiederholter Bootstrap ist inhaltlicher `NO_OP`,
- Abbruch und Fortsetzung nach Restart,
- zwei Bootstrap-Worker konkurrieren ohne Doppelzustände,
- laufende historische Rekordserie meldet bei der nächsten bloßen Verlängerung nicht nachträglich,
- ihr späteres Ende bleibt meldungsfähig,
- ausgeschiedene Spieler bleiben Rekordhalter,
- Definitionsversionen überschreiben sich nicht,
- invalidierter Rekord findet deterministisch den nächsten gültigen Wert.

## Definition of Done

- Bootstrap kann vor Aktivierung öffentlicher Meldungen betrieblich geprüft werden,
- keine Discord-Abhängigkeit,
- keine vollständige Historienneuberechnung bei jeder normalen Submission,
- strukturierte Logs und Metriken für Dauer, Status und Fehler.

---

# Paket 12.6 – Live-Integration für Submission und Korrektur

**Status:** umgesetzt und abgenommen  
**Empfohlener Branch:** `feature/12-6-record-live-evaluation`

## Ziel

Neue kanonische Ergebnisse und normale Korrekturen lösen nach erfolgreicher Ergebnispersistierung die erforderliche Rekordauswertung aus. Events und gewünschte Meldungsprojektionen werden transaktional vorbereitet; Discord bleibt außen vor.

## Lieferumfang

- Integration nach erfolgreicher Neuanlage beziehungsweise Korrektur eines `game_result`,
- stabile Domain-Ursprünge und Ergebnisversionen,
- unmittelbare Auswertung aller Ergebnisrekorde,
- unmittelbare Verlängerung oder Beendigung eindeutig entscheidbarer positiver und negativer Serien,
- persönliche, serverweite individuelle und gegebenenfalls gemeinsame Schwellen,
- Erzeugung gültiger Rekordereignisse,
- Upsert aggregierter gewünschter Announcement-Projektionen,
- Korrekturreconciliation:
  - unverändert,
  - editieren,
  - teilweise reduzieren,
  - vollständig retracten,
  - durch normale Live-Korrektur neu erzeugen,
- stille Ursprünge für Replay, Import, Backfill und administrative Reparatur,
- keine Änderung der bestehenden sicheren kanonischen Ergebnisersetzung.

## Tests

Mindestens:

- eine Submission bricht mehrere Ergebnisrekorde und eine Serienrekordschwelle,
- daraus entsteht ein logischer Aggregationssatz,
- gleiche Submission per Replay erzeugt kein zweites Ereignis,
- Korrektur von Rekord zu `X` retractet betroffene Fakten,
- Korrektur von `X` zu gelöst stellt Lauf wieder her,
- Korrektur verbessert einen weiterhin gültigen Rekord und verlangt Edit,
- Korrektur entfernt nur einen Teil einer aggregierten Meldung,
- historische administrative Reparatur bleibt öffentlich still,
- parallele Ergebnisse verlieren keinen besseren Serverrekord,
- keine Discord-I/O innerhalb einer Transaktion.

## Definition of Done

- bestehende Submission-, Canonical- und Excuse-Tests bleiben grün,
- Rekordfehler gefährden nicht die sichere Persistierung des Nutzerergebnisses,
- Retry- und Recovery-Grenzen sind explizit,
- keine doppelte Fachlogik im Discord-Adapter.

---

# Paket 12.7 – Tagesabschluss, Teilnahmegrenzen und 06:00-Cutoff

**Status:** umgesetzt  
**Empfohlener Branch:** `feature/12-7-record-day-close`

## Ziel

Fehlende Tagesbedingungen werden logisch um 06:00 Uhr finalisiert. Ab diesem Zeitpunkt ist jeder normale Nutzervorgang für ein Vortagsergebnis unzulässig, auch eine Korrektur, ein Retry, Replay oder Recovery eines zuvor begonnenen Vorgangs.

## Lieferumfang

- präzisierte Zulässigkeitsregel:
  - vor 06:00 Uhr aktueller oder unmittelbar vorheriger Spieltag,
  - ab 06:00 Uhr nur aktueller Spieltag,
- keine Ausnahme über Annahmezeitpunkt, bereits gespeicherten Ergebniszustand oder vor dem Cutoff begonnenen Verarbeitungsschritt,
- vollständig terminale Vorgänge bleiben ohne weitere fachliche Verarbeitung idempotent; administrative Backfills und Reparaturen sind getrennte stille Wartungsvorgänge,
- Cutoff unabhängig von tatsächlicher Scheduler-Ausführungszeit,
- Day-Close-Auswertung aller durch fehlende Ergebnisse endenden positiven Serien,
- Day-Close-Verlängerung der Serie ohne perfekten Tag,
- Day-Close-Ende fehlender Durststrecken,
- Abschlussklassifikation und gewünschte Meldungsprojektionen,
- Teilnahmeende und Wiedereintritt als Seriengrenzen,
- Reconciliation bei historischen Teilnahmeberichtigungen,
- Catch-up nach Restart ohne doppelte Abschlüsse,
- Integration in den bestehenden täglichen Cleanup ohne parallelen Scheduler.

## Tests

Mindestens:

- Vortag 05:59:59 zulässig, 06:00:00 nicht zulässig,
- aktueller Spieltag bleibt ab 06:00 zulässig,
- Vortagskorrektur, Retry, Replay und Recovery sind ab 06:00:00 ebenso unzulässig wie eine Neuerfassung,
- verspäteter Scheduler verschiebt den fachlichen Cutoff nicht,
- fehlendes Ergebnis beendet passende Serien genau einmal,
- mehrere betroffene Serien desselben Spielers werden aggregierbar,
- Restart nach Fälligkeit holt genau einmal nach,
- `X` hatte bereits sofort beendet und wird beim Day Close nicht doppelt verarbeitet,
- Teilnahmewechsel beendet oder verhindert negative und gemeinsame Läufe korrekt,
- DST-Übergänge in `Europe/Berlin`.

## Definition of Done

- kein zweiter Tagesabschluss-Scheduler,
- bestehende Cleanup-, Reminder- und Tagesstatussemantik bleibt grün,
- Dokumentation der Vortagsgrenze ist konsistent,
- fachliche Uhrzeit wird ausschließlich über injizierte `Clock` bestimmt.

---

# Paket 12.8 – Discord-Renderer, Delivery und Reconciliation

**Status:** umgesetzt  
**Empfohlener Branch:** `feature/12-8-record-discord-delivery`

## Ziel

Persistierte gewünschte Rekordprojektionen werden als kompakte, lockere und fachlich korrekte Discord-Meldungen zuverlässig erstellt, editiert oder gelöscht.

## Lieferumfang

- transportneutraler Renderer-Input,
- deterministische Texte für:
  - Ergebnisrekord,
  - Serienüberschreitung,
  - Serienabschluss als Rekord,
  - Gleichstand,
  - Near Miss,
  - positive und negative Rekordarten,
- sachlicher Kern getrennt vom humoristischen Zusatz,
- keine Mentions und keine Allowed Mentions,
- Aggregation mehrerer Fakten,
- deterministische Seitenteilung bei Discord-Grenzen,
- Delivery-Worker mit Claim, Lease, Retry und Backoff,
- Create, Edit, partielle Reduktion und Delete,
- Fingerprint-`NO_OP`,
- Reconciliation unklarer Discord-Ausgänge,
- geordnete Message-IDs,
- terminaler Zustand bei späterer externer Löschung bestätigter Meldungen,
- globale externe Konfiguration für öffentliche Rekordmeldungen,
- keine Nachlieferung alter während der Deaktivierung entstandener Ereignisse.

## Tests

Mindestens:

- persönliche und serverweite Fakten in einer Nachricht,
- mehrere Ergebnis- und Serienfakten bleiben verständlich,
- vorheriger Halter wird korrekt dargestellt,
- Near-Miss-Abstand ist korrekt,
- keine echten oder maskierten Mentions,
- unveränderter Fingerprint erzeugt keinen Edit,
- Korrektur reduziert eine Meldung,
- vollständige Invalidierung löscht sie,
- Discord-Timeout plus Retry dupliziert nicht,
- Prozessabbruch zwischen Send und Persistierung wird reconciled,
- manuell gelöschte bestätigte Meldung wird nicht endlos neu erstellt,
- ausgeschiedener und anonymisierter Halter werden stabil dargestellt.

## Definition of Done

- JDA-Typen bleiben im Adapter,
- keine Discord-I/O in Transaktionen,
- Fehlerzustände sind strukturiert und betrieblich sichtbar,
- reale Discord-Abnahmefälle sind vorbereitet.

---

# Paket 12.9 – Ephemerer `/records`-Command

**Status:** in Umsetzung  
**Empfohlener Branch:** `feature/12-9-records-command`

## Ziel

Spieler können den aktuell gültigen, bereits materialisierten Rekordstand prüfen, ohne den Ergebniskanal mit Nachschlagevorgängen zu füllen. Der Command bleibt strikt lesend und löst keine Historienneuberechnung aus.

## Lieferumfang

- Registrierung von `/records`,
- optionale Parameter:
  - `game:all|gridwords|quadwords`,
  - `user`,
- ohne `user` beziehen sich persönliche Rekorde auf den Aufrufer,
- `user:<anderer Nutzer>` ist ausschließlich für konfigurierte Administratoren zulässig; die Autorisierung erfolgt ohne Profil- oder Teilnahme-Write,
- bei fremdem Ziel ändert sich nur der persönliche Abschnitt; serverweite individuelle und gemeinsame Rekorde bleiben unverändert,
- ausschließlich ephemere Antworten und ephemere Follow-up-Seiten,
- leere Allowed Mentions und neutralisierte externe Namen,
- Ergebnisabschnitt mit persönlichen und serverweiten Definitionen,
- Serienabschnitt mit persönlichen, serverweiten individuellen, gemeinsamen sowie positiven und negativen Rekorden,
- `game:gridwords|quadwords` filtert eindeutig spielbezogene Definitionen über die Definitionsmetadaten; spielunabhängige Serien wie Aktivität, Komplett, Perfekt und „ohne perfekten Tag“ bleiben sichtbar,
- laufende Rekordserie mit Hinweis „läuft“,
- Spieltag bei Ergebnisrekorden sowie Start-/Enddatum bei Serienläufen,
- stabile Anzeige ausgeschiedener Halter und neutraler Fallback für nicht mehr auflösbare Halter,
- neutraler Leerzustand, wenn nach erfolgreichem Bootstrap kein aktueller Rekordzustand vorhanden ist,
- deterministische Reihenfolge und Pagination innerhalb der Discord-Grenzen,
- vor erfolgreichem Bootstrap kein scheinbar vollständiger oder leerer Rekordstand.

## Tests

Mindestens:

- Standardaufruf verwendet den Aufrufer für persönliche Rekorde und enthält weiterhin globale Scopes,
- Administrator kann einen anderen Nutzer für den persönlichen Abschnitt auswählen,
- Nicht-Administrator kann keinen anderen Nutzer abfragen; vor der Ablehnung erfolgen keine Record- oder Player-Writes,
- `game:all`, `game:gridwords` und `game:quadwords`,
- spielunabhängige Serien bleiben bei gesetztem Game-Filter sichtbar,
- Ergebnis- und Serienabschnitt sowie persönliche, serverweite individuelle und gemeinsame Scopes,
- laufende und abgeschlossene Läufe,
- ausgeschiedener Rekordhalter,
- anonymisierter beziehungsweise nicht mehr auflösbarer Rekordhalter,
- leerer materialisierter Rekordstand nach erfolgreichem Bootstrap,
- Bootstrap läuft beziehungsweise ist fehlgeschlagen,
- deterministische Pagination und Discord-Längenlimits,
- ausschließlich ephemere Antwort und ephemere Follow-ups,
- keine Allowed Mentions,
- Read-only-Invariante: der Query-Pfad liest nur Bootstrapstatus, materialisierten Record-State und Spieleranzeigen.

## Definition of Done

- Command ist strikt lesend und besitzt keine mutierenden Record-, Player- oder Work-Ports,
- Antworten und Follow-ups sind ephemer,
- keine vollständige Historienneuberechnung oder History-Query pro Aufruf,
- Game-Filter basiert auf Definitionsmetadaten statt Schlüsselheuristiken,
- Slash-Command-Registrierung, Autorisierung, Renderer- und Adaptertests sind grün.

---

# Paket 12.10 – End-to-End-Härtung, Abnahme und Releasevorbereitung

**Status:** offen  
**Empfohlener Branch:** `feature/12-10-records-hardening`

## Ziel

Das vollständige Inkrement wird gegen reale Persistenz-, Konkurrenz-, Recovery-, Discord- und Betriebsbedingungen abgesichert und für einen separaten RC vorbereitet.

## Lieferumfang

- End-to-End-Szenarien vom Share bis zur Rekordmeldung,
- PostgreSQL-Integrationsmatrix für Bootstrap, Live-Update, Korrektur und Delivery,
- gezielte Konkurrenz- und Crash-Tests,
- Startup-Reconciliation,
- strukturierte Logs und Betriebsmetriken,
- Betriebsdokumentation für:
  - Bootstrapstatus,
  - Aktivierung öffentlicher Meldungen,
  - Retry und permanente Fehler,
  - manuellen Rebuild einer Definitionsversion,
  - Diagnose und sichere Reconciliation,
- Upgrade-, Backup-, Restore-, Resume- und Rollbackprüfung,
- Aktualisierung von README, Architektur, Implementierungsplan und AGENTS,
- reales Discord-Abnahmeprotokoll,
- RC-Checkliste ohne automatischen Produktionsrollout.

## Automatisierte Abnahme

Mindestens:

- Standardbuild ohne externe Infrastruktur,
- PostgreSQL-Profil gegen echtes PostgreSQL,
- Liquibase-Neuaufbau und Upgrade vom Produktivschema,
- alle 40 verbindlichen Akzeptanzfälle aus `records.md`,
- parallele Serverrekordkandidaten,
- Retry nach Discord-Timeout,
- Restart in jeder relevanten Delivery-Phase,
- Bootstrap-Abbruch und Fortsetzung,
- Korrektur mit Edit und Delete,
- Day-Close-Catch-up,
- keine Regression bei Parsern, Canonical Delivery, Ausreden, Tagesstatus, Remindern und Reports,
- vollständiges Container- und Betriebsgate.

## Reale Discord-Abnahme

Mindestens auf dem separaten Testserver:

1. öffentlicher Ergebnisrekord mit mehreren aggregierten Fakten,
2. persönliche und serverweite Serienüberschreitung,
3. Serienabschluss durch explizites `X`,
4. Serienabschluss beim 06:00-Close in kontrollierter Testzeit,
5. Near Miss und Gleichstand,
6. negative Durststrecke,
7. Korrektur mit Edit einer Rekordmeldung,
8. Korrektur mit vollständigem Delete,
9. `/records` mit `user`- und `game`-Sichten,
10. Restart und Recovery ohne Duplikat,
11. Meldungen global deaktivieren und ohne Backlog wieder aktivieren.

## Definition of Done

- Issue #58 und alle Paketissues erfüllt,
- Standardbuild und PostgreSQL-Profil grün,
- GitHub Actions vollständig grün,
- reales Discord-Abnahmeprotokoll bestanden,
- Container-, Backup-, Restore-, Resume- und Rollbackweg grün,
- keine Secrets in Code, Dokumentation oder Logs,
- Release Candidate wird separat gebaut und geprüft,
- Produktion erst nach ausdrücklicher Freigabe.

---

## 6. Empfohlene Review-Reihenfolge

Für jeden Paket-PR:

1. fachliche Abweichungen gegen `records.md` prüfen,
2. Architekturgrenzen gegen ADR 0018 prüfen,
3. Domain- und Application-Tests prüfen,
4. bei Persistenz PostgreSQL-Profil prüfen,
5. Konkurrenz-, Idempotenz- und Recovery-Fälle prüfen,
6. Dokumentation und Folgerisiken für das nächste Paket aktualisieren.

Kein Paket darf durch einen pauschalen „später in Paket 12.10“-Verweis eigene notwendige Unit- oder Integrationsprüfungen aufschieben. Paket 12.10 ergänzt End-to-End- und Betriebsabnahme, ersetzt aber nicht die paketlokale Qualitätssicherung.

## 7. Vorbereitung späterer Achievements

Inkrement 12 stellt fachliche Rekordereignisse und Gültigkeitsänderungen bereit. Es implementiert bewusst noch keinen Achievement-Evaluator.

Ein späteres Achievement-Inkrement darf:

- gültige Rekordereignisse gezielt lesen,
- auf Invalidierungen reagieren,
- aktuelle Rekordhalter aus `record_state` bestimmen.

Es darf nicht:

- Discord-Texte parsen,
- Rekordvergleichslogik duplizieren,
- Rekord- und Achievement-Auswertung in einen monolithischen generischen Regelinterpreter verschmelzen.
