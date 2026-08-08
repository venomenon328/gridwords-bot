# Inkrement 13 – Achievements

**Status:** technisch umgesetzt; reale Discord-Abnahme ausstehend  
**Stand:** 8. August 2026  
**Umbrella-Issue:** #86  
**Fachliche Grundlage:** [`../requirements/achievements.md`](../requirements/achievements.md)  
**Architekturentscheidung:** [ADR 0020](../adr/0020-achievement-state-reconciliation-and-delivery.md)

## 1. Ziel

Inkrement 13 führt den verbindlichen Katalog `achievements-v1` mit 60 einmalig freischaltbaren Achievements ein. Vergaben werden historisch rekonstruiert, dauerhaft und korrekturfähig gespeichert, bei normalen Live-Auslösern aggregiert angekündigt und über `/achievements` lesbar gemacht.

Die Umsetzung baut auf der abgeschlossenen spielbezogenen Teilnahme und dem abgeschlossenen Record-Fundament auf, bleibt aber ein eigener Fachbereich. Es entstehen weder eine generische Achievement-Regelmaschine noch ein universeller Event-Bus, eine Gamification-Plattform oder ein externes Messaging-System.

## 2. Verbindlicher Umfang

Inkrement 13 umfasst:

- vollständigen codebasierten `achievements-v1`-Katalog mit 60 Definitionen,
- global eindeutige Anzeigenamen mit `GW:`/`QW:`/`GW+QW:`-Konvention,
- Teilnahme-, Serien-, Erfolgs-, Crossgame-, Timing- und Spezialbedingungen,
- zwei bewusst boarddetailabhängige QuadWords-Achievements,
- persistierten aktuellen Vergabestatus,
- append-only Achievement-Ereignisse,
- participant-bezogene Reconciliation aus kanonischer Historie,
- Korrektur, Invalidierung und Reaktivierung,
- persistente aggregierte Live-Meldungen,
- vollständigen historischen Bootstrap,
- genau eine historische Einführungsmeldung pro Teilnehmer mit Name und Beschreibung jedes rückwirkenden Achievements,
- `/achievements` mit optionalem `user` und `game`,
- Unicode-Fallback-Emojis und einen später erweiterbaren Custom-Emoji-Auflösungspunkt,
- End-to-End-Härtung einschließlich echter PostgreSQL-, Konkurrenz-, Retry-/Restart- und Discord-Abnahme.

## 3. Nicht Bestandteil

- Opt-out für öffentliche Achievement-Meldungen,
- frei konfigurierbare Achievement-Regeln oder Admin-Editor,
- Discord-Rollen je Achievement,
- Rangliste nach Achievement-Anzahl,
- Fortschrittsanzeige gesperrter Achievements,
- wiederholbare Achievements,
- versteckte Achievements mit bewusst unbekannter Bedingung,
- neue persönliche Rekord-/Durchschnitts-/Schwierigkeits-Achievements,
- automatische Erzeugung eigener Badge-Bilder,
- generischer Event-, Query-, Gamification- oder Messaging-Unterbau.

## 4. Architekturgrenzen für alle Pakete

Alle Pakete müssen diese Regeln einhalten:

1. `game_result` und historische spielbezogene Teilnahmezeiträume bleiben kanonische Quelle.
2. Persistierter Achievement-State ist Projektion, keine zweite Ergebnis- oder Serienwahrheit.
3. Domain und reine Evaluatoren kennen weder JDA, Spring noch Persistenzentitäten.
4. Discord-I/O findet niemals innerhalb einer Datenbanktransaktion statt.
5. Liquibase bleibt einzige Quelle für Schemaänderungen.
6. PostgreSQL-spezifisches Verhalten wird mit echtem PostgreSQL geprüft; H2 ersetzt diese Tests nicht.
7. Zeitlogik verwendet die persistierten Share-Zeitpunkte, `Europe/Berlin`/konfigurierte `ZoneId` und injizierte `Clock`.
8. Replay, Retry, Restart und Konkurrenz bleiben idempotent.
9. Normale Live-Meldungen sind bis zum erfolgreichen Bootstrap von `achievements-v1` gesperrt.
10. Der vollständige Teilnehmerzustand darf in V1 grobkörnig reconciled werden; keine vorzeitige Dependency-Matrix.
11. Bestehende Serien- und Teilnahmebedingungen werden wiederverwendet oder gezielt erweitert, nicht dupliziert.
12. Fehlende QuadWords-Boarddetails führen für `Durchmarsch` und `Endgegner` schlicht zu keiner Vergabe.
13. Eigene Discord-Emojis bleiben reine Darstellungsoption mit Unicode-Fallback.
14. Keine Inhalte eines späteren Inkrements vorziehen.

## 5. Paketübersicht

| Paket | Inhalt | Abhängigkeit |
|---|---|---|
| 13.1 | Achievement-Domäne und vollständiger V1-Katalog | – |
| 13.2 | Historischer Snapshot und reiner 60er-Evaluator | 13.1 |
| 13.3 | Persistenzschema und PostgreSQL-Adapter | 13.1 |
| 13.4 | Konkurrenzsichere Reconciliation und State-Service | 13.2, 13.3 |
| 13.5 | Live-Integration, historischer Bootstrap und Gating | 13.4 |
| 13.6 | Discord-Renderer, aggregierte Delivery und Recovery | 13.3–13.5 |
| 13.7 | Ephemerer `/achievements`-Command | 13.3, 13.6 |
| 13.8 | End-to-End-Härtung, Abnahme und Releasevorbereitung | 13.1–13.7 |

Jedes Paket bildet einen einzeln reviewbaren Pull Request. Refactorings außerhalb des Paketumfangs sind nur zulässig, wenn sie unmittelbar erforderlich und regressionssicher getestet sind.

---

# Paket 13.1 – Achievement-Domäne und vollständiger V1-Katalog

**Status:** abgeschlossen  
**Empfohlener Branch:** `feature/13-1-achievement-domain`

## Ziel

Ein reiner transportneutraler Achievement-Kern beschreibt die vollständigen 60 Definitionen aus `achievements-v1`, ihre stabile Identität und ihre typisierten Regelparameter, ohne bereits Historie, Datenbank oder Discord anzubinden.

## Lieferumfang

- klare Paketgrenze `domain.achievement` oder fachlich gleichwertig,
- `AchievementKey`, Definitionsversion, Kategorie und Scope,
- Definitionsmodell mit Anzeigename, Beschreibung und Unicode-Fallback-Emoji,
- typisierte Regelarten und Parameter für alle in `achievements.md` vorkommenden Familien,
- vollständiger deterministischer `AchievementDefinitionCatalog`,
- Start-/Konstruktorvalidierung für:
  - exakt 60 Definitionen,
  - eindeutige technische Schlüssel,
  - global eindeutige Anzeigenamen,
  - korrekte `GW:`-/`QW:`-/`GW+QW:`-Namenskonvention,
  - nichtleere Beschreibungen und Emojis,
  - gültige Schwellen und Scope-/Regelkombinationen,
- transportneutrale Evidence- und Evaluationsresultat-Typen als Schnittstelle für 13.2,
- keine leeren Record- oder zukünftigen Performance-Abstraktionen auf Vorrat.

## Tests

Mindestens:

- exakte Vollständigkeit des 60er-Katalogs,
- Schlüssel und Anzeigenamen global eindeutig,
- alle im Requirements-Dokument festgelegten Schlüssel vorhanden,
- Kategorien/Scopes und Schwellen der Katalogfamilien korrekt,
- `1/6`, `2/6`, `3/6` sowie `4/9`, `5/9`, `6/9` als exakte, nicht hierarchische Regeln modelliert,
- Spezialdefinitionen mit korrekten Parametern,
- Custom Emoji ist keine fachliche Identität,
- Domain frei von Spring/JDA/JPA.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
```

## Definition of Done

- reiner deterministischer Domain-Katalog,
- keine Datenbankmigration,
- keine Discord-Ausgabe,
- keine Regel-DSL,
- keine Implementierung späterer Pakete.

---

# Paket 13.2 – Historischer Snapshot und reiner 60er-Evaluator

**Status:** abgeschlossen  
**Empfohlener Branch:** `feature/13-2-achievement-evaluator`

## Ziel

Ein reiner Evaluator leitet aus einem transportneutralen historischen Teilnehmer-Snapshot alle aktuell historisch belegten Achievements einschließlich `earned_on` und Evidence deterministisch ab.

## Lieferumfang

- transportneutraler `AchievementHistorySnapshot` aus gültigen Ergebnissen, ursprünglichen Share-Zeitpunkten und historischer spielbezogener Teilnahme,
- gezielte Wiederverwendung/Erweiterung der bestehenden Streak-Tagesklassifikation für spielbezogene Teilnahmeserien,
- Auswertung aller 60 Definitionen:
  - Teilnahme-Meilensteine,
  - Teilnahmeserien,
  - Erfolgsserien,
  - exakte Ergebniswerte,
  - Doppeltage und erfolgreiche Doppeltage,
  - Gesamterfahrung,
  - letzter Versuch,
  - Durchmarsch und Endgegner,
  - Punktlandung, Doppeltes Herzschlagfinale und Perfekter Doppelschlag,
  - Déjà-vu je Spiel,
  - Pleiten-Hattrick je Spiel,
  - Frühaufsteher und Nachteule,
- deterministische Wahl des fachlich frühesten qualifizierenden Belegs,
- für QuadWords normale Ergebnislogik ausschließlich aus kanonischem `n/9`,
- Board-Speziallogik ausschließlich aus bereits vorhandenen kanonischen Boarddetails,
- keinerlei Repository-/Discord-Zugriffe im Evaluator.

## Tests

Mindestens die rein fachlich prüfbaren Akzeptanzfälle 1–37 aus `achievements.md`, darunter:

- alle Grenzwerte der Progressionsfamilien,
- historische Aktivierung/Deaktivierung und Seriengrenzen,
- Lücken zwischen Ergebnisfolgen bei Déjà-vu/Pleiten-Hattrick,
- Erfolg versus Fehlschlag,
- Crossgame-Spieltagsemantik,
- `Europe/Berlin` inklusive Sommer-/Winterzeit,
- fehlende Boarddetails ergeben keinen Durchmarsch/Endgegner,
- keine Ableitung des QuadWords-Endwerts aus Boards.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
```

## Definition of Done

- Evaluator ist rein, deterministisch und transportneutral,
- bestehende Serien- und Reportsemantik bleibt unverändert,
- keine persistierten Achievement-Counter,
- keine Discord-Strings außerhalb von Definitionsmetadaten.

---

# Paket 13.3 – Persistenzschema und PostgreSQL-Adapter

**Status:** abgeschlossen  
**Empfohlener Branch:** `feature/13-3-achievement-persistence`

## Ziel

ADR 0020 wird mit der nächsten freien Liquibase-Migration und kleinen expliziten Ports für Award-State, Events, Bootstrap und Announcement-Delivery persistierbar gemacht.

## Lieferumfang

- normalisierte Tabellen beziehungsweise gleichwertige Modelle für:
  - `achievement_award_state`,
  - append-only `achievement_event`,
  - `achievement_bootstrap_state`,
  - `achievement_announcement`,
  - geordnete Announcement-Items,
- fachliche Unique Keys und Check Constraints,
- technische Lock-Version,
- `earned_on`, `detected_at`, Evidence, Invalidierungsdaten,
- Event- und Announcement-Idempotenzschlüssel,
- Claim-/Lease-/Retry-/Fehlerfelder für Bootstrap und Delivery,
- genau eine bestätigte Discord-Message-ID pro logischer Achievement-Meldung,
- kleine Outbound-Ports und primäre PostgreSQL-Adapter,
- Upgrade vom aktuellen Produktivschema und kompletter Liquibase-Neuaufbau,
- keine Comparator-/Achievement-Regellogik in SQL.

## Tests

Mindestens:

- frischer PostgreSQL-Schemaaufbau,
- Upgrade des bisherigen Schemas ohne Datenverlust,
- Unique Key pro Guild/Teilnehmer/Achievement-Schlüssel,
- Event-Idempotenz verhindert Duplikate,
- Announcement-Idempotenz verhindert doppelte logische Deliveries,
- append-only Eventhistorie bleibt erhalten,
- Award-State kann aktiv, invalidiert und erneut aktiv gespeichert werden,
- geordnete Announcement-Items bleiben stabil,
- Claim/Lease-Übergänge sind tokengebunden,
- unbekannte DB-Fehler werden nicht als fachliche Konflikte maskiert.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

## Definition of Done

- Standardbuild bleibt infrastrukturunabhängig grün,
- echtes PostgreSQL prüft Constraints und Konkurrenzanker,
- Liquibase ist einzige Schemaquelle,
- keine vorgezogene Application-/Discord-Logik.

---

# Paket 13.4 – Konkurrenzsichere Reconciliation und State-Service

**Status:** abgeschlossen  
**Empfohlener Branch:** `feature/13-4-achievement-reconciliation`

## Ziel

Ein transaktionaler Application-Service reconciled den aus 13.2 abgeleiteten Teilnehmerzustand gegen die Persistenz aus 13.3 und erzeugt idempotent Award-State, append-only Events und gewünschte Announcement-Projektionen.

## Lieferumfang

- History-Loader aus vorhandenen kanonischen Ergebnis-/Teilnahme-Ports,
- participant-bezogene Reconciliation mit `UNLOCK`, `NO_OP`, `INVALIDATE`, `REACTIVATE`,
- kurze Transaktion für State, Event und gewünschte Announcement-Fakten,
- stabile Verarbeitungsursprünge für Live, Korrektur, Replay, Bootstrap und Reparatur,
- Konkurrenzschutz gegen parallele Erstvergabe und parallele Korrektur,
- Event-Idempotenz pro fachlichem Übergang,
- Markierung bereits öffentlich angekündigter Achievement-Schlüssel zur Vermeidung von Reaktivierungs-Flapping,
- Reduktion/Unterdrückung noch nicht ausgelieferter Live-Fakten nach Korrektur,
- keine Edit-/Delete-Aktion für bereits erfolgreich veröffentlichte historische Unlock-Meldungen,
- kein Discord-I/O in Transaktionen.

## Tests

Mindestens Akzeptanzfälle 38–45 aus `achievements.md`, darunter:

- Replay/Retry `NO_OP`,
- Restart nach persistiertem State/Event ohne Duplikat,
- echte konkurrierende Erstvergabe gegen PostgreSQL,
- Invalidierung nach Korrektur,
- append-only Audit bleibt erhalten,
- spätere Reaktivierung,
- mehrere neue Keys eines Triggers ergeben einen aggregierbaren Batch,
- unbekannte technische Fehler werden nicht als fachlicher Konflikt maskiert.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

## Definition of Done

- Reconciliation ist idempotent und konkurrenzsicher,
- kein Discord-Aufruf in der Transaktion,
- kein Live-Hook vor Paket 13.5,
- keine parallele zweite Achievement-Wahrheit.

---

# Paket 13.5 – Live-Integration, historischer Bootstrap und Gating

**Status:** abgeschlossen  
**Empfohlener Branch:** `feature/13-5-achievement-integration-bootstrap`

## Ziel

Der Reconciliation-Service wird in normale Ergebnisverarbeitung und Korrektur integriert; gleichzeitig initialisiert ein restartfähiger Bootstrap `achievements-v1` aus der vollständigen Historie und sperrt Live-Meldungen bis zu seinem erfolgreichen Abschluss.

## Lieferumfang

- dünne Integration nach erfolgreicher kanonischer Submission/Korrektur,
- keine erneute Parser- oder Attachmentlogik im Achievement-Pfad,
- klare Unterscheidung normaler Live-/Korrekturursprünge von Replay, Recovery und administrativer Reparatur,
- historischer Bootstrap aller Teilnehmer für `achievements-v1`,
- Claim/Lease/Retry/Restart-Fortsetzung des Bootstraps,
- idempotente Rekonstruktion aller aktuell belegten Vergaben mit historischem `earned_on`,
- pro Teilnehmer genau eine gewünschte `HISTORICAL_INTRODUCTION`,
- Einführung enthält alle rückwirkend aktiven Achievements des Teilnehmers in deterministischer Reihenfolge,
- normale Live-Announcement-Erzeugung bis zum erfolgreichen Guild-Bootstrap gesperrt,
- Ergebnisse, die während eines laufenden Bootstrap eintreffen, dürfen in die abschließende historische Teilnehmerprojektion einfließen; nach Bootstrap-Abschluss werden neue normale Trigger live behandelt,
- Startup-Recovery für unvollständigen Bootstrap.

## Tests

Mindestens Akzeptanzfälle 46–52 aus `achievements.md`, zusätzlich:

- Bootstrap auf leerer Historie,
- Bootstrap mit beiden Spielen und historischen Teilnahmewechseln,
- Restart nach Teilfortschritt,
- wiederholter Bootstrap bleibt idempotent,
- Live-Submission während Bootstrap erzeugt keine parallele öffentliche Live-Meldung,
- Korrektur nach Bootstrap kann neue Vergabe oder Invalidierung auslösen,
- keine Alt-Submission wird durch Replay nachträglich als neue Live-Freischaltung gefeiert.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

## Definition of Done

- bestehender Submission-/Korrekturpfad bleibt regressionsfrei,
- Bootstrap ist restart- und retry-sicher,
- keine Discord-Netzwerkverbindung in diesem Paket erforderlich,
- normale öffentliche Auslieferung bleibt bis 13.6 nur persistierte gewünschte Projektion.

---

# Paket 13.6 – Discord-Renderer, aggregierte Delivery und Recovery

**Status:** abgeschlossen  
**Empfohlener Branch:** `feature/13-6-achievement-discord-delivery`

## Ziel

Persistierte Achievement-Meldungen werden als genau eine Discord-Nachricht pro Live-Batch beziehungsweise Teilnehmer-Einführung robust ausgeliefert.

## Lieferumfang

- transportneutraler Renderer für `LIVE_UNLOCK_BATCH` und `HISTORICAL_INTRODUCTION`,
- Live-Meldung mit Teilnehmer, Anzahl sowie pro Achievement Emoji, global eindeutigem Namen und Beschreibung,
- historische Einführung mit Gesamtzahl sowie jedem Achievement mit Name und Beschreibung,
- ausdrücklich keine zusätzliche Gruppierung der historischen Liste nach GW/QW/GW+QW/Allgemein,
- deterministische Reihenfolge,
- genau eine Discord-Nachricht pro logischer Meldung; mehrere Embeds/Darstellungsblöcke innerhalb dieser Nachricht sind zulässig,
- automatisierter Grenztest, dass die maximal mögliche 60er-Einführung mit den V1-Texten innerhalb der tatsächlich verwendeten Discord-Grenzen renderbar bleibt,
- Custom-Emoji-Auflösungspunkt nach Achievement-Schlüssel mit sicherem Unicode-Fallback,
- persistente Claims, Lease, Retry/Backoff, Fingerprint und bestätigte Message-ID,
- unbekannter Create-Ausgang wird reconciled; keine doppelte öffentliche Meldung nach Send-vor-ACK/Restart,
- keine öffentliche Invalidierungsnachricht und kein nachträgliches Edit/Delete bereits erfolgreich veröffentlichter Unlock-Historie,
- externe Discord-Fehler sauber von fachlichen Zuständen trennen.

## Tests

Mindestens:

- Einzel- und Mehrfach-Livefreischaltung,
- vollständige 60er-Einführung in genau einer Discord-Nachricht,
- Name und Beschreibung jedes Intro-Items vorhanden,
- Intro ohne Scope-Gruppierung,
- fehlendes Custom Emoji fällt auf Unicode zurück,
- Create, ACK, Retry, Lease-Ablauf und Restart,
- Send-vor-ACK ohne Duplikat,
- konkurrierende Delivery-Worker,
- bereits zugestellte Meldung wird nicht nach Invalidierung öffentlich zurückgenommen.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

## Definition of Done

- keine Discord-I/O innerhalb von DB-Transaktionen,
- keine Nachricht pro Einzel-Achievement,
- Intro bleibt exakt eine Discord-Nachricht pro Teilnehmer,
- keine generische Messaging-Plattform.

---

# Paket 13.7 – Ephemerer `/achievements`-Command

**Status:** abgeschlossen  
**Empfohlener Branch:** `feature/13-7-achievements-command`

## Ziel

Freigeschaltete aktuelle Achievements werden lesbar und ohne historischen Vollscan über `/achievements` dargestellt.

## Lieferumfang

- Slash-Command `/achievements`,
- optional `user`, Standard ist der Aufrufer,
- Fremdansicht anderer Teilnehmer ohne Adminbeschränkung,
- optional `game:Alle|GridWords|QuadWords`, Standard `Alle`,
- Game-Filter zeigt ausschließlich den jeweiligen Single-Game-Scope; `CROSS_GAME` und `GLOBAL` nur unter `Alle`,
- nur aktuell aktive Achievements; keine gesperrten oder invalidierten Einträge,
- keine Fortschrittsanzeige und keine Rangliste,
- lesbare Gruppierung nach `EXPERIENCE`, `RELIABILITY`, `PERFORMANCE`, `SPECIAL`,
- Pagination beziehungsweise Discord-konforme Aufteilung innerhalb der ephemeren Interaction,
- Unicode-/Custom-Emoji-Darstellung wie 13.6,
- Query liest Award-State und aktuelle Definitionsmetadaten, kein Historien-Rebuild.

## Tests

Mindestens Akzeptanzfälle 53–60 aus `achievements.md`, soweit commandbezogen:

- Default-Aufrufer,
- Fremdansicht,
- beide Game-Filter,
- ungefilterte Sicht mit allen Scopes,
- invalidierte Einträge fehlen,
- leeres Profil,
- viele Achievements/Pagination,
- Command bleibt ephemer,
- keine Mentions und keine Schreiboperation,
- kein historischer Vollscan.

## Verifikation

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

## Definition of Done

- Command ist read-only und ephemer,
- bestehende Command-Registrierung bleibt regressionsfrei,
- keine Sichtbarkeit gesperrter Bedingungen,
- keine Performance-/Leaderboard-Erweiterung.

---

# Paket 13.8 – End-to-End-Härtung, Abnahme und Releasevorbereitung

**Status:** technische Härtung umgesetzt; reale Discord-Abnahme ausstehend  
**Empfohlener Branch:** `feature/13-8-achievements-hardening`

## Ziel

Inkrement 13 wird als vollständiger Produktionspfad gegen die 60 verbindlichen Akzeptanzfälle, reale PostgreSQL-Semantik, Konkurrenz, Restart/Retry, Upgrade und Discord-Verhalten abgenommen.

## Lieferumfang

- nachvollziehbare Matrix Akzeptanzfall 1–60 → automatisierter Test beziehungsweise realer Nachweis,
- PostgreSQL-End-to-End vom kanonischen Ergebnis bis Award-State, Event und Announcement,
- Upgrade vom bisherigen Produktivschema ohne Datenverlust,
- vollständiger historischer Bootstrap mit Restart nach Teilfortschritt,
- Live-Submission und normale Korrektur nach Bootstrap,
- Konkurrenzfälle für Award-State und Delivery mit echten Persistenzgrenzen,
- Retry-/Restart-/Send-vor-ACK-Härtung,
- Regression von Submission, Participation, Serien, Daily Status, Reminder, Reports, Ausreden und Records,
- reale Discord-Abnahme auf dem separaten Testserver für:
  - einzelne und aggregierte Live-Freischaltung,
  - historische Einführung,
  - `/achievements` inklusive Fremdansicht und Game-Filter,
  - Unicode-Fallback und, falls lokal konfiguriert, mindestens einen Custom-Emoji-Pfad,
- Betriebs-/Recovery-Dokumentation für Bootstrap, offene Deliveries, sichere Wiederholung und Invalidierungsdiagnose,
- Aktualisierung von Requirements, ADR, Schema, Architektur, Glossar und Inkrementstatus, soweit durch die Umsetzung erforderlich,
- Vorbereitung eines RC-fähigen Main-Stands; RC/Produktivrelease selbst bleibt separat.

## Tests und Verifikation

Verpflichtend:

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
mvn --batch-mode --no-transfer-progress -Pmigration-clean-install verify
```

Zusätzlich relevante vorhandene Container-, Upgrade-, Backup-/Restore- und Betriebschecks ausführen und die Ergebnisse im PR dokumentieren.

## Definition of Done

- alle 60 Akzeptanzfälle nachvollziehbar abgedeckt,
- Standardbuild, PostgreSQL-Profil und Migration-Gate grün,
- Konkurrenz-/Retry-/Restart-/Bootstrap-/Korrekturfälle belastbar geprüft,
- reale Discord-Abnahme dokumentiert und erfolgreich,
- keine offenen Inkonsistenzen zu den verbindlichen Achievement-Dokumenten,
- bestehende Funktionen regressionsfrei,
- PR bis zur vollständigen technischen und realen Abnahme Draft; erst danach Merge und separater RC-Schritt.