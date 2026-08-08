# ADR 0020: Achievement-Zustand, Reconciliation und idempotente Delivery

**Status:** akzeptiert  
**Stand:** 8. August 2026  
**Entscheidung für:** Inkrement 13  
**Verbindliches Issue:** #86  
**Fachliche Grundlage:** [`../requirements/achievements.md`](../requirements/achievements.md)

## Kontext

Achievements werden aus einer bereits umfangreichen kanonischen Historie abgeleitet. Inkrement 13 muss zugleich:

- 60 stabile Definitionen aus unterschiedlichen Datenarten auswerten,
- historische spielbezogene Teilnahme korrekt berücksichtigen,
- Live-Submission, Korrektur, Replay, Restart und historischen Backfill konsistent behandeln,
- bereits vergebene Achievements dauerhaft und schnell für `/achievements` lesbar halten,
- Korrekturen ohne harte Löschung historischer Vergaben abbilden,
- mehrere gleichzeitige Freischaltungen zu einer Discord-Meldung aggregieren,
- bei Einführung alle historischen Vergaben rekonstruieren und pro Teilnehmer genau eine Einführungsnachricht veröffentlichen,
- Discord-Retries und Prozessabbrüche ohne doppelte Meldungen überstehen,
- ohne generische Regelmaschine, universellen Event-Bus oder externen Message Broker auskommen.

Eine rein imperative Verarbeitung „Submission kommt → einzelne Achievements unlocken“ ist für Korrekturen und Backfill zu fragil. Eine rein dynamische Neuberechnung bei jedem `/achievements`-Aufruf wäre unnötig teuer, nicht auditierbar und ungeeignet für idempotente Benachrichtigungen. Eine zweite, von `game_result` und Teilnahmezeiträumen unabhängige Fortschrittswahrheit würde die bestehende Architektur verletzen.

## Entscheidung

### 1. Eigener Achievement-Fachbereich statt Erweiterung des Record-Systems

Inkrement 13 erhält einen klar abgegrenzten `domain.achievement`- beziehungsweise gleichwertigen Fachbereich mit eigenen Definitionen, Evaluationsresultaten und Application-Services.

Das vorhandene Record-System wird nicht zu einer generischen Gamification-Plattform erweitert. Rekordzustände und Achievement-Vergaben bleiben fachlich getrennt.

Gemeinsame konkrete Hilfskomponenten dürfen wiederverwendet oder extrahiert werden, wenn dadurch tatsächliche technische Duplikation bei Claims, Retry, Fingerprints oder Discord-Delivery entfällt. Es entsteht daraus kein universelles Messaging-Framework.

### 2. Definitionen bleiben versionierter Anwendungscode

Der erste Katalog lautet:

```text
achievements-v1
```

Eine Definition enthält mindestens:

```text
stabiler Schlüssel
global eindeutiger Anzeigename
Kategorie
Scope
Unicode-Fallback-Emoji
deutsche Beschreibung
Regeltyp und typisierte Regelparameter
```

Der Katalog wird beim Start hart validiert. Schlüssel und Anzeigenamen sind global eindeutig. Es gibt keine Datenbank-DSL und keine frei konfigurierbare Regelmaschine.

Spätere Custom Discord Emojis werden ausschließlich in der Darstellung anhand des stabilen Schlüssels aufgelöst. Sie sind kein Bestandteil der fachlichen Identität.

### 3. Kanonische Historie bleibt Quelle der Wahrheit

Maßgebliche Quelle bleiben:

- `game_result`,
- ursprünglicher persistierter Share-Empfangszeitpunkt,
- historisch wirksame spielbezogene Teilnahmezeiträume,
- vorhandene kanonische QuadWords-Boarddetails,
- die bestehende transportneutrale Tages- und Serienklassifikation.

Persistierte Achievement-Zustände sind materialisierte Projektionen. Sie ersetzen keine Ergebnis-, Teilnahme- oder Serienlogik.

Der Achievement-Evaluator parst keine Discord-Nachrichten und lädt keine Attachments. `QW: Durchmarsch` und `QW: Endgegner` werden nur dann positiv belegt, wenn im kanonischen Ergebnis bereits vier geeignete Boarddetails vorhanden sind. Fehlen sie, entsteht schlicht kein Beleg für diese Definitionen.

### 4. Teilnehmerbezogene History-Projektion und grobkörnige Reconciliation

V1 optimiert nicht auf eine feingranulare Regelabhängigkeitsmatrix. Bei einem relevanten fachlichen Auslöser wird der Achievement-Zustand des betroffenen Teilnehmers aus einem transportneutralen historischen Snapshot neu bewertet.

Der reine Evaluator liefert eine Menge von `AchievementEvidence`-Werten, sinngemäß:

```text
achievementKey
earnedOn
evidenceType
evidenceReference
```

Der Reconciler vergleicht diese Menge mit der persistierten aktuellen Projektion:

```text
belegt + nie vorhanden       -> UNLOCK
belegt + aktiv               -> NO_OP
belegt + invalidiert         -> REACTIVATE
nicht belegt + aktiv         -> INVALIDATE
nicht belegt + nicht aktiv   -> NO_OP
```

Damit verwenden Live-Verarbeitung, Korrektur, Replay, Restart, administrative Reparatur und historischer Backfill dieselbe fachliche Auswertung.

Bei der aktuellen Spielerzahl und 60 Definitionen ist die vollständige Teilnehmerauswertung bewusst bevorzugt: weniger Optimierungskomplexität, geringeres Risiko divergierender Inkrementalregeln und überschaubare Token-/Laufzeitkosten. Eine spätere gezielte Optimierung bleibt möglich, ohne die fachliche Schnittstelle zu ändern.

### 5. Bestehende Seriensemantik wird wiederverwendet

Erfolgsserien verwenden die bereits verbindliche spielbezogene persönliche Lösungsserien-Semantik.

Für spielbezogene Teilnahmeserien darf `StreakDayClassifier` oder eine gleichwertige gemeinsame Domain-Komponente gezielt um eine Bedingung erweitert werden, die für genau ein `GameType` prüft:

```text
historisch aktiv für dieses Spiel
UND gültiges Ergebnis dieses Spiels vorhanden
```

Die bestehende persönliche Aktivitätsserie über „mindestens eines der aktiven Spiele“ darf dafür nicht zweckentfremdet werden.

Neue Achievement-Logik darf keine zweite, widersprüchliche Definition historischer Teilnahme, Tagesgrenzen oder Serienabbrüche einführen.

### 6. Drei persistente Ebenen plus Bootstrap-Status

Inkrement 13 trennt:

1. aktuellen Vergabestatus,
2. unveränderliche Achievement-Ereignisse,
3. logische Achievement-Meldungen einschließlich Delivery-Zustand,
4. Bootstrap-/Rebuild-Status je Guild und Definitionsversion.

#### 6.1 Aktueller Vergabestatus

Ein logisches `achievement_award_state` enthält mindestens:

```text
Guild-ID
Teilnehmer-ID
Achievement-Schlüssel
aktiver/invalidierter Status
aktuelle fachliche Definitionsversion
earned_on
detected_at
Evidence-Typ und stabile Evidence-Referenz
Zeitpunkt der Invalidierung, soweit vorhanden
technische Lock-Version
created_at / updated_at
```

Der fachliche Unique Key ist mindestens:

```text
Guild-ID + Teilnehmer-ID + Achievement-Schlüssel
```

Die Definitionsversion gehört zur aktuell ausgewerteten Projektion, nicht zum fachlichen Unique Key. Eine neue Katalogversion darf denselben stabilen Achievement-Schlüssel nicht als zweite unabhängige Vergabe duplizieren.

#### 6.2 Unveränderliches Achievement-Ereignis

Ein logisches `achievement_event` ist append-only und enthält mindestens:

```text
stabile Event-ID und Idempotenzschlüssel
Guild-ID und Teilnehmer-ID
Achievement-Schlüssel und Definitionsversion
Eventtyp: UNLOCKED | INVALIDATED | REACTIVATED
earned_on beziehungsweise betroffenen fachlichen Zeitpunkt
Evidence-Typ und Evidence-Referenz
Verarbeitungsursprung
Erkennungszeitpunkt
```

Ein altes Ereignis wird bei Korrektur nicht physisch gelöscht oder umgeschrieben. Die neue fachliche Tatsache wird als weiteres Ereignis protokolliert und der aktuelle `achievement_award_state` entsprechend reconciled.

#### 6.3 Logische Achievement-Meldung

Ein logisches `achievement_announcement` repräsentiert genau eine gewünschte öffentliche Discord-Nachricht und enthält mindestens:

```text
Guild- und Channel-ID
Teilnehmer-ID
Definitionsversion
Meldungsart: LIVE_UNLOCK_BATCH | HISTORICAL_INTRODUCTION
stabilen Aggregations- und Idempotenzschlüssel
Renderer-Version und Inhalts-Fingerprint
Delivery-Zustand
Claim-Token und Lease
Versuchsanzahl / Retry-Zeitpunkt
letzte Fehlerkategorie
Discord-Message-ID, sobald bestätigt
created_at / updated_at
```

Eine zugehörige Item-Projektion referenziert die in dieser Meldung enthaltenen Achievement-Fakten in stabiler Reihenfolge.

Für V1 muss eine logische Achievement-Meldung genau einer Discord-Nachricht entsprechen. Innerhalb dieser Nachricht dürfen mehrere Embeds beziehungsweise Darstellungsblöcke verwendet werden.

#### 6.4 Bootstrap-/Rebuild-Status

Ein persistenter Status pro Guild und Definitionsversion enthält mindestens:

```text
Status
Claim/Lease
Start- und Abschlusszeitpunkt
Fehler-/Retryinformation
```

Öffentliche normale Live-Freischaltungen sind erst nach erfolgreichem Bootstrap der aktiven Definitionsversion zulässig.

### 7. Kurze Transaktion, Discord außerhalb der Transaktion

Bei einer akzeptierten Submission oder Korrektur erfolgt die Achievement-Verarbeitung in klaren Phasen:

1. Der bestehende Use Case persistiert beziehungsweise korrigiert den kanonischen Ergebniszustand nach seinen bisherigen Regeln.
2. Der Achievement-Pfad lädt den transportneutralen historischen Snapshot des betroffenen Teilnehmers.
3. Der reine Evaluator projiziert die aktuell belegten Achievements.
4. In einer kurzen Datenbanktransaktion reconciled der Application-Service `achievement_award_state`, schreibt neue append-only Events und upsertet gegebenenfalls eine logische Live-Meldung.
5. Die Transaktion endet.
6. Ein separater Delivery-Pfad rendert und synchronisiert die Discord-Nachricht.

Discord-I/O findet niemals innerhalb der Datenbanktransaktion statt.

### 8. Konkurrenz und Idempotenz

Der aktuelle Vergabestatus wird durch fachliche Unique Keys und transaktionale Zeilensperre, Optimistic Locking oder gleichwertige atomare Operationen geschützt.

Zwei parallele Auswertungen dürfen für denselben Teilnehmer und Achievement-Schlüssel nicht zwei aktive Zustände erzeugen. Event- und Announcement-Idempotenzschlüssel verhindern Duplikate bei:

- erneut zugestellten Discord-Events,
- Replay,
- Retry,
- Startup-Recovery,
- parallelen Workern,
- wiederholtem Bootstrap.

Unbekannte Datenbank- oder Discord-Fehler werden nicht als fachliche Konflikte maskiert.

### 9. Live-Meldungen werden pro fachlichem Trigger aggregiert

Alle während einer Reconciliation neu öffentlich freizuschaltenden Achievements desselben normalen Triggers bilden einen `LIVE_UNLOCK_BATCH`.

Der Batch enthält für jedes Achievement mindestens:

```text
Emoji
Anzeigename
Beschreibung
```

Es wird niemals eine Nachricht pro Achievement erzeugt.

Wird eine noch nicht erfolgreich veröffentlichte Vergabe durch eine Korrektur wieder invalidiert, darf die gewünschte noch ausstehende Meldung entsprechend reduziert oder unterdrückt werden. Eine bereits erfolgreich veröffentlichte historische Unlock-Meldung wird nach späterer Invalidierung nicht öffentlich editiert oder gelöscht; Profil und aktueller Vergabestatus werden jedoch korrigiert. Es gibt keine zusätzliche Aberkennungsnachricht.

Ein bereits öffentlich angekündigtes Achievement muss bei einer späteren Reaktivierung in V1 nicht erneut angekündigt werden.

### 10. Historischer Bootstrap erzeugt Vergaben und genau eine Einführung pro Teilnehmer

Der Bootstrap läuft über die vorhandene vollständige Teilnehmerhistorie und verwendet denselben Evaluator wie Live-Reconciliation.

Für jeden Teilnehmer werden alle historisch belegbaren Achievements mit dem tatsächlich ableitbaren `earned_on` aktiviert. Anschließend wird für diese Definitionsversion genau ein `HISTORICAL_INTRODUCTION` erzeugt.

Der semantische Idempotenzschlüssel enthält mindestens:

```text
Guild + participant + achievements-v1 + historical-introduction
```

Die Einführung enthält alle rückwirkend vergebenen Achievements mit Name und Beschreibung in deterministischer Reihenfolge. Sie gruppiert nicht nach Spielscope. Discord-Grenzen dürfen durch mehrere Embeds innerhalb derselben Nachricht behandelt werden; eine Aufteilung auf mehrere öffentliche Nachrichten pro Teilnehmer ist für V1 nicht zulässig.

Ein Restart nach Teilfortschritt setzt den Bootstrap idempotent fort und erzeugt weder doppelte Vergaben noch eine zweite Einführung.

### 11. `/achievements` liest die aktuelle Projektion

Der Command liest ausschließlich aktive `achievement_award_state`-Projektionen und die aktuelle Definitionsdarstellung.

Er löst keinen vollständigen Rebuild aus und liest keine gerenderten Discord-Nachrichten.

Andere Teilnehmerprofile dürfen in V1 ohne Administratorbeschränkung gelesen werden. Der optionale Game-Filter bildet ausschließlich die Single-Game-Scopes `GRIDWORDS` beziehungsweise `QUADWORDS` ab; `CROSS_GAME` und `GLOBAL` erscheinen nur bei der ungefilterten Sicht.

### 12. Record-Events bleiben eine optionale spätere Quelle, aber V1 dupliziert keine Rekordlogik

ADR 0018 erlaubt einem späteren Achievement-System, gültige Rekordereignisse über einen kleinen Port zu lesen. Der aktuell verbindliche 60er-Katalog enthält jedoch noch keine persönlichen Rekord- oder Rekordbruch-Achievements.

Inkrement 13 führt deshalb keine neue Record-Abhängigkeit nur auf Vorrat ein. Werden später rekordbezogene Achievements spezifiziert, dürfen sie gültige fachliche `record_event`-Fakten konsumieren und niemals gerenderte Discord-Texte oder duplizierte Comparator-Logik verwenden.

## Konsequenzen

### Positive Folgen

- Eine einzige Fachauswertung trägt Live, Korrektur, Replay und Backfill.
- Der aktuelle Profilzustand ist schnell lesbar und auditierbar.
- Kanonische Ergebnisse und Teilnahmezeiträume bleiben einzige fachliche Wahrheit.
- Korrekturen können falsche Vergaben sauber invalidieren, ohne öffentliche Rücknahmesalven.
- Mehrere Freischaltungen erzeugen keine Nachrichtensalve.
- Historische Einführung ist restart- und retry-sicher.
- Der 60er-Katalog bleibt explizit, testbar und ohne Laufzeit-DSL.
- Eigene Discord-Emojis können später rein darstellerisch ergänzt werden.
- Die Architektur nutzt bewährte Record-/Delivery-Prinzipien, ohne beide Fachbereiche zu koppeln.

### Kosten und Risiken

- Vollständige Teilnehmer-Reconciliation liest mehr Historie als eine fein granulierte inkrementelle Auswertung.
- Persistenter State, append-only Events, Bootstrap und Delivery benötigen mehrere Tabellen und Integrationspfade.
- Eine Einführung mit vielen Achievements muss Discord-Grenzen innerhalb genau einer Nachricht sicher einhalten.
- Korrekturen historischer Daten können mehrere Achievement-Zustände desselben Teilnehmers gleichzeitig ändern.
- Zwei bildabhängige QuadWords-Achievements sind für Ergebnisse ohne persistierte Boarddetails nicht rückwirkend erreichbar.

Diese Kosten sind für die aktuelle kleine Spielerzahl akzeptiert. Die gewählte Lösung priorisiert Nachvollziehbarkeit, Korrektheit und idempotente Wiederholbarkeit vor vorzeitiger Laufzeitoptimierung.

## Verworfene Alternativen

### Alles bei `/achievements` dynamisch neu berechnen

Verworfen, weil Vergabezeitpunkt, Benachrichtigungsidempotenz, Invalidierung und Auditierbarkeit fehlen würden.

### Imperative Einzel-Callbacks pro Submission

Verworfen, weil Korrektur, Replay und historischer Backfill leicht andere Semantik erhalten würden.

### Achievement-Regeln in der Datenbank konfigurieren

Verworfen, weil der kleine kuratierte Katalog keine Regel-DSL rechtfertigt und Typ-/Testbarkeit verlieren würde.

### Record-System generalisieren

Verworfen, weil Records und Achievements unterschiedliche Fachzustände und Lebenszyklen besitzen. Gemeinsame technische Muster dürfen konkret geteilt werden, aber nicht durch eine vorauseilende Plattform.

### Externer Broker oder generischer interner Event-Bus

Verworfen, weil der modulare Monolith und die aktuelle Last dies nicht benötigen und AGENTS.md solche vorauseilende Infrastruktur ausdrücklich ausschließt.