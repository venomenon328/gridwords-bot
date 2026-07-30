# Architektur des GridWords-Bots

**Status:** verbindlich  
**Architekturstil:** modularer Monolith  
**Laufzeit:** ein Spring-Boot-Prozess mit PostgreSQL und Discord/JDA

## 1. Ziele

Die Architektur priorisiert:

1. Sicherheit von Ergebnissen und Nutzerbeiträgen,
2. Idempotenz bei Replay, Konkurrenz und Neustart,
3. testbare Fachlogik ohne Discord oder Datenbank,
4. nachvollziehbare persistierte Zustände,
5. kleine, konkrete Ports statt allgemeiner Infrastruktur,
6. klare Trennung zwischen fachlichen Ablehnungen und technischen Fehlern.

Es gibt keine Microservices, keinen externen Message Broker, kein universelles Plugin-System und keine generative KI zur Laufzeit.

## 2. Schichten und Abhängigkeitsrichtung

```text
Discord/JDA ─┐
PostgreSQL ──┼─> Adapter ─> Ports ─> Application ─> Domain
Scheduler ───┘                         │
                                      └─> reine Parser
```

- **Domain:** reine Java-Typen und Invarianten.
- **Parser:** deterministische, fachnahe Komponenten; kein Spring, JDA, Netzwerk oder Datenbankzugriff.
- **Application:** orchestriert Parser, Ports, Zustände und Transaktionsgrenzen.
- **Ports:** kleine transportneutrale Schnittstellen.
- **Adapter:** JDA, PostgreSQL, Scheduling und Spring-Wiring.

Abhängigkeiten zeigen nach innen. JDA-Typen und Discord-URLs verlassen den Discord-Adapter nicht. Datenbankmodelle werden nicht als Domänenmodell verwendet.

## 3. Paketstruktur

```text
de.venomenon.gridwordsbot
├── domain
│   ├── model
│   ├── parsing
│   └── streak
├── parser
│   ├── gridwords
│   └── quadwords
├── application
│   ├── submission
│   └── canonical
├── port
│   ├── in
│   └── out
├── adapter
│   ├── discord
│   │   ├── inbound
│   │   └── canonical
│   └── persistence
└── config
```

## 4. Inbound-Verarbeitung

Der JDA-Listener:

1. filtert Server, Channel, Spieler, Bots und Webhooks,
2. kopiert die benötigten Werte in `InboundSharedMessage`,
3. delegiert an einen begrenzten Executor,
4. führt keine Datenbank-, Download- oder sonstige längere REST-Operation auf dem JDA-Event-Thread aus.

Der Snapshot enthält:

- Guild-, Channel-, Message- und Author-ID,
- Anzeigename und Rohtext,
- Attachment-Metadaten,
- für abrufbare Anhänge eine transportneutrale `AttachmentReference` aus Channel-, Message- und Attachment-ID,
- Empfangszeitpunkt.

Die Referenz ist absichtlich keine URL. Der Application-Kern kennt weder JDA-Proxies noch temporäre Discord-CDN-Adressen.

## 5. Parse-Pipeline

`ProcessSharedResultService` verwendet die Parser in fester Reihenfolge:

1. GridWords-Text und Unicode-Raster,
2. QuadWords-Kopfzeile,
3. nur bei passender QuadWords-Kopfzeile: eindeutige Bildauswahl, Download und Bildparse.

`ParseResult` unterscheidet:

- `NotApplicable`: Nachricht gehört nicht zu diesem Parser,
- `Parsed`: fachlich vollständiges Ergebnis,
- `Invalid`: passendes, aber stabiles ungültiges Share.

Exceptions sind kein reguläres Parser-Ergebnis.

## 6. QuadWords-Attachment-Grenze

### 6.1 Auswahl

Nach erfolgreichem Kopfparse wird aus den Metadaten genau ein plausibles Bild gewählt.

- Kein Bild: fachliche Ablehnung.
- Mehrere plausible Bilder: mehrdeutige fachliche Ablehnung.
- Genau ein Bild: Download über `AttachmentContentLoader`.

### 6.2 Downloadadapter

`JdaAttachmentContentLoader` löst exakt die gespeicherte Channel-, Message- und Attachment-ID auf. Er prüft das Byte-Limit sowohl anhand der Discord-Metadaten als auch während des Streams.

Er übersetzt Infrastrukturfehler in transportneutrale Kategorien:

- zu groß,
- Quelle beziehungsweise Zugriff nicht verfügbar,
- retryfähiger Discord-, Netzwerk- oder I/O-Fehler.

Discord-Aufrufe erfolgen außerhalb von Datenbanktransaktionen.

### 6.3 Rohbilddaten

In Inkrement 6 werden Rohbildbytes ausschließlich im Arbeitsspeicher verarbeitet. Es gibt keine Rohbildtabelle, keinen Dateispeicher und keinen Bereinigungsjob. Die in der Anforderung genannte maximale Aufbewahrung von 48 Stunden wird dadurch unterschritten. Eine spätere persistente Rohbildablage bedürfte einer eigenen Entscheidung und Ablaufbereinigung.

## 7. Reiner QuadWords-Bildparser

`QuadWordsImageParser` hängt nur von Java-Standardbibliothek und Domain-Typen ab.

### 7.1 Formate und Grenzen

Unterstützt:

- PNG,
- JPEG.

Nicht unterstützte Formate, darunter WebP, werden stabil abgelehnt. Es wird keine zusätzliche Decoderbibliothek eingeführt.

Grenzen:

```text
Eingabebytes:       höchstens 8 MiB
Breite:             höchstens 4096 Pixel
Höhe:               höchstens 4096 Pixel
Gesamtfläche:       höchstens 12.000.000 Pixel
```

Die Dimensionen werden über den `ImageReader` geprüft, bevor das vollständige Bild decodiert wird.

### 7.2 Geometrie

Der Parser erkennt:

- vier Teilboards in einer 2×2-Anordnung,
- zwei Gruppen aus jeweils fünf Spalten,
- obere und untere Zeilengruppe,
- skalierte Bilder, übliche Ränder und kleinere Abstandsvariationen.

Die kanonische Reihenfolge ist fest:

1. oben links,
2. oben rechts,
3. unten links,
4. unten rechts.

Zusätzliche erkannte aktive Zeilen oberhalb der aus der Kopfzeile erwarteten Versuchszahl werden abgelehnt. Klar fehlende nachlaufende Zeilen eines bereits früher abgeschlossenen Teilboards werden als kanonische Leerzellen ergänzt. Dadurch bleiben alle vier Boards auf der Versuchszahl des Gesamtshares ausgerichtet.

### 7.3 Farbe und Konfidenz

Jede Zelle wird über ein 7×7-Stichprobenraster innerhalb der Zellfläche bewertet. Die dominante Klasse benötigt eine Mindestquote; unsichere oder gemischte Flächen werden nicht geraten.

Kanonische Symbole:

- `⬜` leer beziehungsweise grau/weiß,
- `🟨` vorhanden, falsche Position,
- `🟩` richtige Position.

Unklare Geometrie, Farbe, Struktur oder Ressourcenüberschreitung liefert einen stabilen Fehlercode.

### 7.4 Versionierung

Die Parser-Version lautet:

```text
quadwords-image-v2
```

Sie wird gemeinsam mit dem Ergebnis persistiert.

## 8. Domänenmodell

- `NormalizedBoard` bleibt das GridWords-Board.
- `QuadWordsBoard` erzwingt ein bis neun Zeilen, genau fünf Zellen je Zeile und ausschließlich kanonische Symbole.
- `QuadWordsBoards` enthält genau vier benannte Boards in fester Reihenfolge.
- `ParsedGameResult` enthält typisierte optionale Varianten für GridWords- beziehungsweise QuadWords-Boards und erzwingt spielabhängige Invarianten.

Ein GridWords-Ergebnis kann kein QuadWords-Board enthalten. Ein neues bildgestütztes QuadWords-Ergebnis kann kein GridWords-Board enthalten und benötigt vier Boards mit der zur Kopfzeile passenden kanonischen Zeilenzahl.

## 9. Fehler- und Reaktionssemantik für QuadWords

### Fachlich stabil ungültig

Beispiele:

- fehlender oder mehrdeutiger Bildanhang,
- zu großes Bild,
- nicht unterstütztes oder beschädigtes Format,
- unsichere Geometrie oder Farbe,
- widersprüchliche aktive Zeilen.

Folge:

- Submission wird als Parserablehnung persistiert,
- kein gültiges `game_result`,
- Originalnachricht bleibt sichtbar,
- `⚠️` wird gesetzt.

### Technisch retryfähig

Beispiele:

- vorübergehender Discord-/Netzwerk-/I/O-Fehler,
- referenzierte Quelle zum Abrufzeitpunkt nicht erreichbar.

Folge:

- Submission wechselt vor der Ergebnisspeicherung nach `FAILED_RETRYABLE`,
- kein `game_result`,
- keine irreführende `✅`- oder `⚠️`-Reaktion,
- dasselbe Event kann später aus `FAILED_RETRYABLE` wieder nach `VALIDATED` aufgenommen und gespeichert werden.

### Erfolgreich

- Kopfzeile und Bild sind sicher geparst,
- genau ein fachliches Ergebnis wird gespeichert beziehungsweise korrigiert,
- vier Boards und Parser-Version werden persistiert,
- Original bleibt in Inkrement 6 sichtbar,
- `✅` wird gesetzt.

## 10. PostgreSQL-Modell und Migration

`game_result` besitzt vier explizit benannte Spalten:

- `quadwords_top_left_board`,
- `quadwords_top_right_board`,
- `quadwords_bottom_left_board`,
- `quadwords_bottom_right_board`.

Für neue bildgestützte QuadWords-Ergebnisse sind sie nur vollständig oder gar nicht befüllbar; teilweise befüllte Sätze sind ungültig.

### Legacy-Kompatibilität

Vor Inkrement 6 gespeicherte QuadWords-Ergebnisse verwenden `quadwords-share-v1` und besitzen keine Boards. Migration 009 erhält diese Datensätze unverändert. Der primäre PostgreSQL-Adapter liest beide Darstellungen. Die erste gültige bildgestützte Korrektur für denselben Spieler und Spieltag aktualisiert den vorhandenen fachlichen Datensatz in-place, behält dessen ID und setzt Boards sowie `quadwords-image-v2`.

Liquibase bleibt die einzige Quelle für Schemaänderungen. Hibernate erzeugt oder aktualisiert das Schema nicht.

## 11. Sichere GridWords-Ersetzung

Der bestehende GridWords-Ablauf bleibt unverändert:

```text
RESULT_STORED
→ CANONICAL_MESSAGE_PUBLISHED
→ ORIGINAL_MESSAGE_DELETED
→ COMPLETED
```

Eine Nutzerquelle wird ausschließlich nach persistierter kanonischer Message-ID gelöscht. Claims, Leases, Retry, Startup-Recovery, `UNKNOWN_MESSAGE` und Superseded-Reconciliation bleiben von Inkrement 6 unberührt.

QuadWords verwendet diesen Veröffentlichungs- und Löschablauf erst in Inkrement 7.

## 12. Transaktionsgrenzen

- Discord- und Attachment-Aufrufe liegen außerhalb von Datenbanktransaktionen.
- Parse und Bildverarbeitung halten keine DB-Transaktion offen.
- Ergebnis-Upsert und Submission-Zustandswechsel erfolgen in kurzen atomaren Persistenzoperationen.
- Ein Discord- oder Downloadfehler rollt kein bereits gespeichertes anderes Ergebnis zurück.
- Scheduler und In-Memory-Wake-ups sind niemals alleinige Quelle der Wahrheit; Recovery basiert auf PostgreSQL.

## 13. Tests

### Standardbuild

Ohne Netzwerk, Token, Datenbank und Container:

- Domain-Invarianten,
- Text- und Bildparser,
- reale PNG-Fixtures mit exakten Golden-Ausgaben,
- synthetische Layout- und Fehlerfixtures,
- Application-Retry, Replay und Korrektur,
- JDA-Adapter mit gemockter Grenze,
- ArchUnit-Regeln.

### PostgreSQL-Integration

Mit echtem PostgreSQL:

- Liquibase aus leerem Schema,
- vier Boardspalten und Constraints,
- Board-/Parser-Version-Round-trip,
- Replay und Korrektur,
- persistierter Pre-Result-Retry,
- Migration und In-place-Upgrade eines `quadwords-share-v1`-Datensatzes,
- Start des vollständigen Spring-Kontexts.

### Manuelle Abnahme

Der reale Discord-/PostgreSQL-Smoke-Test prüft echte gelöste und nicht gelöste Bilder, Zellfarben, Boardreihenfolge, Reaktionen, Korrektur, Neustart sowie unverändertes GridWords-Verhalten.

## 14. Lokale Infrastruktur

Der schnelle Standardbuild bleibt infrastrukturunabhängig. Für Persistenz- und Smoke-Tests ist Docker Compose die bevorzugte lokale PostgreSQL-Umgebung. Maßgeblich ist ADR 0010.

## 15. Verwandte ADRs

- ADR 0002: idempotente Nachrichtenersetzung
- ADR 0006 bis 0009: kanonische Veröffentlichung, Recovery und Quelllöschung
- ADR 0010: Docker-verfügbare lokale Entwicklung
- ADR 0011: transportneutrale QuadWords-Bildanalyse
