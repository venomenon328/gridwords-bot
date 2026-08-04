# ADR 0018: Rekordzustand, Auditereignisse und reconciliierte Meldungen

**Status:** akzeptiert  
**Stand:** 4. August 2026  
**Entscheidung für:** Inkrement 12  
**Verbindliches Issue:** #58

## Kontext

Rekorde werden aus kanonischen Ergebnissen und historisch wirksamen Teilnahmezeiträumen abgeleitet. Der Bot muss zugleich:

- den aktuell gültigen Allzeitrekord schnell und konkurenzsicher kennen,
- historische Rekordübernahmen und Invalidierungen auditierbar halten,
- Bootstrap, Replay, Backfill und Live-Ereignisse unterscheiden,
- mehrere Rekordfakten zu einer Discord-Meldung aggregieren,
- Meldungen nach Ergebniskorrekturen editieren oder löschen,
- Discord-Timeouts, Retries und Prozessabbrüche ohne Duplikate überstehen,
- später Achievements auf fachliche Rekordereignisse reagieren lassen,
- ohne externen Message Broker, universelle Regelmaschine oder Plugin-System auskommen.

Eine reine Neuberechnung der gesamten Historie vor jeder Submission wäre unnötig teuer. Ein ausschließlich mutierter Rekordzähler wäre dagegen eine zweite fachliche Wahrheit ohne ausreichende Auditierbarkeit. Eine einmal veröffentlichte, unveränderliche Discord-Nachricht würde nach Korrekturen sichtbar falsche Aussagen hinterlassen.

## Entscheidung

### 1. Rekorddefinitionen bleiben versionierter Anwendungscode

Der Rekordkatalog wird durch explizite Java-Typen und stabile Definitionsschlüssel beschrieben. Die fachliche Definitionsversion für den ersten Katalog lautet:

```text
records-v1
```

Eine Definition enthält mindestens:

- Schlüssel,
- Metrikart,
- Spielbezug, soweit vorhanden,
- Vergleichsraum,
- Polarität,
- Comparator beziehungsweise Tie-Breaker,
- Quellen-Eignung,
- Meldungsschwellen.

Die Regeln werden nicht als frei konfigurierbare Datenbank-DSL gespeichert. Ändert sich eine fachliche Metrik inkompatibel, erhält sie eine neue Definitionsversion und einen kontrollierten Rebuild.

Die fachliche Definitionsversion ist ausdrücklich von einer technischen Optimistic-Lock-Version getrennt.

### 2. Kanonische Ergebnisse und Teilnahmezeiträume bleiben Quelle der Wahrheit

`game_result` und die historisch wirksamen Teilnahmezeiträume bleiben die kanonischen Daten. Positive und negative Serienläufe werden daraus deterministisch abgeleitet.

Persistierte Rekordzustände sind materialisierte Projektionen und Konkurrenzanker. Sie ersetzen weder Ergebnis- noch Serienlogik.

### 3. Drei persistente Ebenen

Inkrement 12 verwendet getrennte persistente Modelle für:

1. aktuellen Rekordzustand,
2. historische Rekordereignisse,
3. logische Rekordmeldungen einschließlich Delivery-Zustand.

Zusätzlich wird der Bootstrap- beziehungsweise Rebuild-Stand pro Guild und Definitionsversion persistiert.

#### 3.1 Aktueller Rekordzustand

Ein logischer `record_state` enthält mindestens:

```text
Guild-ID
Definitionsschlüssel
Definitionsversion
Vergleichsraum
gegebenenfalls Spieler-ID oder stabilen Scope-Key
kanonischen Vergleichswert
kanonische Quellart und Quell-ID
gegebenenfalls Rekordhalter
fachlichen Spieltag oder Serienzeitraum
laufend/abgeschlossen, soweit relevant
technische Lock-Version
created_at / updated_at
```

Der fachliche Unique Key umfasst:

```text
Guild-ID
Definitionsschlüssel
Definitionsversion
Scope-Art
Scope-Key
```

Vergleichswerte werden in expliziten, typisierten Komponenten gespeichert. Für `Wenigste Versuche` sind dies beispielsweise Versuchszahl und Dauer. Es wird keine opaque serialisierte Comparator-Logik in der Datenbank abgelegt.

#### 3.2 Historisches Rekordereignis

Ein logisches `record_event` enthält mindestens:

```text
stabile Event-ID
Eventtyp
Definitionsschlüssel und -version
Vergleichsraum
vorherigen und neuen beziehungsweise endgültigen Wert
vorherige und neue Quelle beziehungsweise Halter
auslösenden Domain-Vorgang
Quellart und Quell-ID
fachlichen Zeitraum
Verarbeitungsursprung
Erkennungszeitpunkt
Gültigkeitsstatus
gegebenenfalls invalidated_at / superseded_by
```

Initialisierung, Live-Übertreffen, Seriengleichstand, Near Miss und Serienabschluss sind unterscheidbare Ereignisse.

Ein Ereignis wird bei einer Korrektur nicht physisch entfernt. Es bleibt auditierbar und wird invalidiert oder supersedet.

#### 3.3 Logische Rekordmeldung

Ein logischer `record_announcement` repräsentiert eine aggregierte Discord-Projektion. Er enthält mindestens:

```text
Guild- und Channel-ID
stabilen Aggregations- und Idempotenzschlüssel
betroffenes Subjekt
Meldungsphase
gewünschten Darstellungszustand
Renderer-Version
Inhalts-Fingerprint
Delivery-Zustand
Claim-Token und Lease
Versuchsanzahl und nächsten Retry-Zeitpunkt
letzte Fehlerkategorie
geordnete Discord-Message-IDs
veröffentlicht/geändert/gelöscht/extern entfernt
created_at / updated_at
```

Eine Zuordnungstabelle verbindet eine Meldung mit den aktuell enthaltenen gültigen `record_event`-Fakten. Dadurch kann eine Korrektur einzelne Fakten aus einer aggregierten Nachricht entfernen, ohne die übrigen zu verlieren.

Eine logisch zusammengehörige Meldung darf wegen Discord-Grenzen aus mehreren geordneten Nachrichten oder Embeds bestehen.

#### 3.4 Bootstrap- und Rebuild-Stand

Ein persistenter Initialisierungszustand enthält mindestens:

```text
Guild-ID
Definitionsversion
Status
Claim und Lease
Start- und Abschlusszeitpunkt
Fehler- und Retryinformationen
```

Öffentliche Rekordmeldungen sind erst nach erfolgreichem Bootstrap der aktiven Definitionsversion zulässig.

### 4. Kurze transaktionale Auswertung, Discord außerhalb der Transaktion

Bei einer akzeptierten neuen Submission oder Korrektur erfolgt die fachliche Verarbeitung in klaren Phasen:

1. Das kanonische Ergebnis wird nach den bestehenden Regeln gespeichert beziehungsweise korrigiert.
2. Der Application-Service bestimmt betroffene Rekorddefinitionen und Serienläufe.
3. In einer kurzen Datenbanktransaktion werden benötigte Zustände gesperrt oder versionsgeschützt gelesen, Kandidaten verglichen, Zustände aktualisiert, Auditereignisse geschrieben und gewünschte Meldungsprojektionen upserted.
4. Die Transaktion endet.
5. Ein separater Delivery-Pfad rendert und synchronisiert Discord-Nachrichten.

Discord-I/O findet niemals innerhalb der Datenbanktransaktion statt.

### 5. Konkurrenzschutz

Für einen vorhandenen Rekordzustand wird eine transaktionale Zeilensperre oder gleichwertige atomare Compare-and-Set-Operation verwendet. Die erstmalige Anlage wird zusätzlich durch den fachlichen Unique Key geschützt und bei einem Insert-Konflikt kontrolliert neu bewertet.

Damit können zwei parallele Submissionen nicht beide denselben unveränderten Ausgangszustand übernehmen.

Sind zwei konkurrierende Werte nacheinander tatsächlich streng besser, dürfen zwei fachlich gültige Rekordereignisse entstehen. Ist die erste Meldung noch nicht ausgeliefert, wird ihre gewünschte Projektion auf den inzwischen gültigen Stand reduziert oder supersedet. Eine bereits erfolgreich veröffentlichte, zum Veröffentlichungszeitpunkt wahre Meldung bleibt bestehen.

### 6. Idempotente Domain-Auslöser

Jede Live-Auswertung verwendet einen stabilen Ursprung, beispielsweise:

```text
game_result + Ergebnisversion + Verarbeitungsart
Serienart + Scope + Spieler + Startdatum + Meldungsphase
Day-Close-Datum + Subjekt + Meldungsphase
```

Unique Constraints verhindern doppelte Ereignisse und Meldungen bei:

- mehrfach zugestellten Discord-Events,
- Replay,
- Retry,
- Startup-Recovery,
- konkurrierenden Schedulern.

### 7. Bootstrap ist still

Der historische Bootstrap berechnet den aktuellen Stand aus der vollständigen Historie und erzeugt nur stille Initialisierungsanker. Er rekonstruiert keine öffentliche Chronik vergangener Rekordwechsel.

Bei Aktivierung bereits laufende Rekordserien werden mit den schon überschrittenen Schwellen als konsumiert markiert. Dadurch wird eine alte Rekordübernahme nicht bei der nächsten Verlängerung nachträglich gefeiert. Eine spätere Abschlussmeldung desselben Laufs bleibt zulässig.

Import, Backfill, unverändertes Replay und administrative historische Reparatur erzeugen ebenfalls keine öffentlichen Meldungen.

### 8. Korrekturen erzeugen gewünschte Projektionen neu

Nach einer Korrektur werden die betroffenen Ergebnisdefinitionen und Serienläufe aus kanonischen Daten neu bestimmt. Daraus wird nicht eine imperative Discord-Aktion, sondern der gewünschte Projektionszustand abgeleitet:

- identisch: `NO_OP`,
- weiterhin vorhanden, aber verändert: `EDIT`,
- teilweise invalidiert: `EDIT` mit verbleibenden Fakten,
- vollständig invalidiert: `DELETE`,
- durch normale Live-Korrektur neu entstanden: `CREATE`.

Die Delivery-Schicht synchronisiert anschließend den tatsächlichen Discord-Zustand.

Es gibt keine zusätzliche öffentliche Aberkennungsnachricht.

### 9. Delivery verwendet bewährte konkrete Muster

Rekordmeldungen verwenden dieselben bewährten Prinzipien wie kanonische Nachrichten, Tagesstatus und Reports:

- persistente Zustände,
- tokengebundene Claims,
- begrenzte Leases,
- Retry mit Backoff,
- explizite permanente Fehler,
- Inhalts-Fingerprints,
- geordnete Message-IDs,
- Reconciliation unklarer Discord-Ausgänge,
- keine Discord-Aufrufe in Transaktionen.

Gemeinsame konkrete Hilfskomponenten dürfen extrahiert werden, wenn dadurch tatsächliche Duplikation entfällt. Es wird kein universelles Messaging-Framework eingeführt.

### 10. Extern gelöschte Meldungen werden respektiert

Ein unbekannter Ausgang während einer noch nicht bestätigten Veröffentlichung wird aktiv reconciled, um Duplikate zu vermeiden.

Wird eine bereits bestätigte Rekordmeldung später manuell im Discord gelöscht, wird sie dagegen nicht unbegrenzt wiederhergestellt. Der Zustand wechselt kontrolliert nach `EXTERNALLY_REMOVED` oder einen gleichwertigen terminalen Status. `/records` bleibt die überprüfbare aktuelle Wahrheit.

Eine spätere fachliche Korrektur versucht nicht, eine bewusst extern entfernte historische Meldung wieder sichtbar zu machen. Auditereignis und aktueller Rekordzustand werden dennoch korrekt reconciled.

### 11. Tagesabschluss ist fachlicher Trigger, nicht Zeitquelle

Der Tagesabschluss um 06:00 Uhr `Europe/Berlin` triggert die Finalisierung fehlender Tagesbedingungen und die daraus entstehenden Serienabschlüsse.

Der Scheduler darf verspätet laufen oder nach Neustart nachholen. Fachlicher Cutoff und Serienendzeitpunkt werden aus `LocalDate`, `LocalTime`, `ZoneId` und injizierter `Clock` bestimmt, nicht aus der tatsächlichen Thread-Ausführungszeit.

### 12. `/records` liest Projektionen, nicht Discord

Der lesende Command bezieht den aktuellen Zustand aus `record_state` und ergänzt fachlich erforderliche Quellinformationen. Er scannt nicht bei jedem Aufruf die gesamte Historie und wertet keine Discord-Nachrichten aus.

Bei fehlendem oder veraltetem Bootstrap wird kein scheinbar vollständiger Stand dargestellt. Der Command liefert einen neutralen vorübergehenden Hinweis oder bleibt bis zum erfolgreichen Bootstrap deaktiviert.

### 13. Vorbereitung auf Achievements ohne Event-Bus

Ein späterer `AchievementEvaluator` darf gültige Rekordereignisse über einen kleinen Application-Port beziehungsweise eine gezielte persistente Abfrage konsumieren.

Inkrement 12 führt dafür weder einen externen Broker noch einen generischen internen Event-Bus ein. Rekord- und spätere Achievement-Evaluator bleiben getrennte fachliche Komponenten.

Achievements reagieren auf Eventtyp, Definitionsschlüssel, Werte und Gültigkeitsstatus, niemals auf gerenderte Discord-Texte.

## Konsequenzen

### Positive Folgen

- Der aktuelle Rekord ist schnell lesbar und konkurrenzsicher.
- Kanonische Ergebnis- und Teilnahmedaten bleiben einzige fachliche Wahrheit.
- Historische Entscheidungen und spätere Invalidierungen sind auditierbar.
- Korrekturen hinterlassen keine dauerhaft sichtbar falschen Rekordmeldungen.
- Mehrere Fakten können ohne Nachrichtensalve aggregiert werden.
- Discord-Timeouts und Restarts erzeugen keine unkontrollierten Duplikate.
- Der spätere Achievement-Ausbau kann auf stabilen fachlichen Ereignissen aufsetzen.
- Definitionsänderungen sind kontrolliert versionierbar.

### Kosten und Risiken

- Das Feature benötigt mehrere neue Persistenzmodelle und Reconciliation-Zustände.
- Korrekturen historischer Ergebnisse können eine größere, aber gezielt begrenzte Neuberechnung betroffener Definitionen auslösen.
- Ein Lauf kann nach Korrekturen eine neue Identität erhalten; alte Ereignisse müssen sauber invalidiert werden.
- Mehrteilige Discord-Projektionen erhöhen die Zahl der Recovery-Fälle.
- Materialisierte Zustände benötigen Bootstrap-, Rebuild- und Konsistenztests.

Diese Kosten sind akzeptiert, weil eine einfachere einmalige Post-and-forget-Lösung die ausdrücklich gewünschte Korrektursemantik nicht erfüllen würde.

## Verworfene Alternativen

### A. Gesamte Historie bei jeder Submission und jedem `/records`-Aufruf neu berechnen

Verworfen wegen unnötiger Last, schwieriger Konkurrenzkontrolle und fehlendem persistentem Delivery-Anker.

### B. Nur aktuellen Rekordstand ohne Auditereignisse speichern

Verworfen, weil Invalidierungen, Bootstrap-Abgrenzung, spätere Achievements und nachvollziehbare Korrekturen nicht zuverlässig abbildbar wären.

### C. Nur Ereignisse speichern und aktuellen Zustand immer vollständig rekonstruieren

Verworfen, weil `/records`, Live-Vergleich und konkurrierende Aktualisierung unnötig teuer und komplex würden.

### D. Rekordmeldungen nach Veröffentlichung unveränderlich lassen

Verworfen, weil der ausdrücklich gewünschte Edit- und Löschweg bei Ergebniskorrekturen fehlen würde.

### E. Jede Rekorddefinition als eigene Discord-Nachricht veröffentlichen

Verworfen wegen Nachrichtensalven bei einer einzelnen starken Submission.

### F. Allgemeine Regel- oder Plugin-Engine für Rekorde und Achievements

Verworfen als vorauseilende Abstraktion. Der erste Katalog ist klein, explizit und in Anwendungscode besser testbar.

### G. Externer Message Broker

Verworfen, weil der modulare Monolith mit PostgreSQL eine zuverlässige persistente Delivery ohne zusätzliche Betriebsinfrastruktur abbilden kann.

### H. Discord-I/O innerhalb der Rekordtransaktion

Verworfen wegen langer Transaktionen, unklarer Fehlerausgänge und schlechter Recovery-Eigenschaften.
