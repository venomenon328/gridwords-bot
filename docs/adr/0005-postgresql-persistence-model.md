# ADR 0005: PostgreSQL-Persistenzmodell und konfliktfeste Schreibzugriffe

- **Status:** akzeptiert
- **Datum:** 29. Juli 2026
- **Entscheidungsträger:** Tobias / Projektarchitektur

## Kontext

Nach den reinen Share-Parsern benötigt der Bot eine persistente Grundlage für Ergebnisse und den späteren sicheren Discord-Nachrichtenersetzungsablauf.

Dabei müssen insbesondere folgende Fälle korrekt funktionieren:

- dasselbe Discord-Event wird mehrfach zugestellt,
- zwei Verarbeitungen versuchen gleichzeitig, dieselbe Quellnachricht zu registrieren,
- ein Ergebnis für denselben Spieler, dasselbe Spiel und denselben Spieltag wird erneut eingereicht und korrigiert,
- der Prozess startet zwischen zwei Schritten der Discord-Verarbeitung neu,
- Discord-Aufrufe und PostgreSQL können nicht in einer gemeinsamen Transaktion ausgeführt werden,
- der lokale Standardbuild darf weiterhin keine Datenbank oder Container-Runtime voraussetzen.

ADR 0002 definiert den sicheren, persistierten Nachrichtenersetzungsablauf. ADR 0004 definiert die Docker-optionale lokale Entwicklung und verpflichtende PostgreSQL-Integrationstests in GitHub Actions.

## Entscheidung

## 1. PostgreSQL als einzige relationale Zielplattform

Das produktionsnahe Schema und alle Persistenzintegrationstests verwenden PostgreSQL.

- Liquibase ist die alleinige Quelle des Schemas.
- H2 wird nicht als Ersatzdatenbank eingeführt.
- Hibernate `ddl-auto` bleibt `validate` beziehungsweise `none`.
- PostgreSQL-spezifische Constraints und konfliktfeste Schreiboperationen dürfen bewusst verwendet werden.

## 2. Discord-IDs

Discord-Snowflakes werden in Java als `long` und in PostgreSQL als `BIGINT` gespeichert.

Nicht geheime Discord-IDs werden nicht als Strings persistiert. Für bekannte Snowflake-Spalten gelten positive Werte als Datenbankinvariante.

## 3. Spieleridentität

Die Tabelle `player` verwendet die Discord-User-ID als fachlichen Primärschlüssel:

```text
player.discord_user_id BIGINT PRIMARY KEY
```

Ein zusätzlicher künstlicher Spieler-Schlüssel wird nicht eingeführt.

Mindestens gespeichert werden:

- Discord-User-ID,
- Anzeigename,
- aktiv,
- Administratorstatus,
- Erstellungs- und Änderungszeitpunkt.

Ein Persistence-Port stellt ein idempotentes Upsert für konfigurierte Spieler bereit. Die automatische Synchronisierung beim Anwendungsstart ist nicht Bestandteil dieses Inkrements und folgt mit dem Application-/Discord-Inbound-Inkrement.

## 4. Fachliches Spielergebnis

`game_result` erhält einen internen, generierten `BIGINT`-Primärschlüssel. Fachlich eindeutig bleibt:

```text
player_id + game_type + game_date
```

Mindestens gespeichert werden:

- interner Schlüssel,
- Spieler-ID als Fremdschlüssel auf `player`,
- Spieltyp `GRIDWORDS` oder `QUADWORDS`,
- Spieltag als `DATE`,
- gelöst ja/nein,
- verwendete Versuche, bei nicht gelöst `NULL`,
- Maximalversuche,
- Dauer in Sekunden,
- optionale positive Gridgames-Flammenserie,
- optionales normalisiertes Board,
- relevanter Roh-Share-Text,
- Parser-Version,
- optionale kanonische Discord-Bot-Message-ID,
- Erstellungs- und Änderungszeitpunkt,
- technische Versionsspalte für optimistische Konflikterkennung, sofern der gewählte Adapter sie benötigt.

Datenbank-Check-Constraints sichern mindestens:

- erlaubte Spieltypen,
- GridWords verwendet Maximalwert 6,
- QuadWords verwendet Maximalwert 9,
- gelöste Ergebnisse haben einen positiven Versuchswert bis zum Maximum,
- nicht gelöste Ergebnisse haben keinen Versuchswert,
- Dauer ist nicht negativ,
- Flammenserie ist, falls vorhanden, positiv,
- GridWords hat ein Board,
- QuadWords hat in Version 1 kein Board,
- kanonische Message-ID ist, falls vorhanden, positiv.

Die exakte GridWords-Zeilenzahl bleibt zusätzlich eine Domaininvariante, weil sie sinnvoller im Java-Modell als per komplexem SQL-Constraint geprüft wird.

## 5. Submission als wiederaufnehmbarer Ablauf

`submission` wird anhand der Discord-Quell-Message-ID identifiziert:

```text
submission.source_message_id BIGINT PRIMARY KEY
```

Discord-Snowflakes sind für diesen Projektumfang als global eindeutige Quellidentität ausreichend. Guild- und Channel-ID werden dennoch gespeichert und geprüft.

Mindestens gespeichert werden:

- Quell-Message-ID,
- Guild-ID,
- Channel-ID,
- Author-/Spieler-ID,
- unveränderter relevanter Nachrichteninhalt,
- aktueller Verarbeitungszustand,
- optionaler Fremdschlüssel auf `game_result`,
- optionaler Parser-Fehlercode,
- optionaler sicherer technischer Fehlertext ohne Token oder unnötigen vollständigen Fremdinhalt,
- Zeitpunkte für Empfang und letzte Änderung,
- optionaler Zeitpunkt der Originallöschung,
- technische Versionsspalte für konfliktfeste Zustandsänderungen.

Persistierbare Zustände:

```text
RECEIVED
VALIDATED
RESULT_STORED
CANONICAL_MESSAGE_PUBLISHED
ORIGINAL_MESSAGE_DELETED
COMPLETED
PARSE_REJECTED
FAILED_RETRYABLE
FAILED_FINAL
```

Die Datenbank begrenzt die Spalte per Check-Constraint auf diese Werte. Eine spätere Änderung erfolgt ausschließlich über eine neue Liquibase-Migration.

Der kanonische Bot-Message-Verweis liegt am `game_result`, weil eine Korrektureinreichung dasselbe fachliche Ergebnis und dieselbe kanonische Nachricht aktualisiert. Die Submission referenziert das Ergebnis und trägt den Ablaufzustand.

## 6. Attachment-Snapshot

Damit ein nach `RECEIVED` abgebrochener Vorgang später ohne JDA-Objekte fortgesetzt werden kann, wird ein kleiner transportneutraler Attachment-Snapshot gespeichert.

Vorgesehen ist eine Kindtabelle `submission_attachment` mit mindestens:

- Quell-Message-ID,
- stabiler Reihenfolge beziehungsweise Index innerhalb der Nachricht,
- Dateiname,
- Content-Type,
- Größe.

Primärschlüssel:

```text
source_message_id + attachment_index
```

In diesem Inkrement werden keine Bilder heruntergeladen und keine Binärdaten gespeichert.

## 7. Tagesstatus-Grundlage

`daily_status_message` wird als kleine technische Zuordnung vorbereitet:

- Guild-ID,
- Channel-ID,
- Spieltag,
- optionale Bot-Message-ID,
- Erstellungs- und Änderungszeitpunkt.

Fachlich eindeutig:

```text
guild_id + channel_id + game_date
```

Es wird in diesem Inkrement noch keine Tagesstatusnachricht erzeugt.

## 8. Konfliktfeste Schreiboperationen

Die folgenden Operationen müssen auch bei konkurrierenden Transaktionen deterministisch sein:

- Spieler upserten,
- Quellnachricht registrieren,
- fachliches Ergebnis anhand seines Business Keys upserten.

Für diese Operationen verwendet der PostgreSQL-Adapter explizites `INSERT ... ON CONFLICT` über Spring JDBC beziehungsweise eine gleichwertig transparente PostgreSQL-native Implementierung.

Spring Data JPA darf für einfache Mappings und Lesezugriffe eingesetzt werden. JPA-Entities bleiben vollständig im Persistence-Adapter. Application und Domain kennen weder JPA-Entities noch Spring-Data-Interfaces.

Es wird kein generisches Repository-Framework über den fachlichen Ports aufgebaut.

## 9. Korrektursemantik

Eine neue gültige Share-Nachricht für denselben Spieler, Spieltyp und Spieltag:

- erzeugt eine neue `submission`, weil die Quell-Message-ID neu ist,
- aktualisiert den vorhandenen `game_result` konfliktfest,
- behält dessen internen Schlüssel,
- behält eine bereits gesetzte kanonische Bot-Message-ID, solange kein expliziter fachlicher Auftrag sie ersetzt,
- aktualisiert Ergebnisdaten, Rohtext, Parser-Version und Änderungszeitpunkt.

Eine erneute Verarbeitung derselben Quell-Message-ID legt weder eine zweite Submission noch ein zweites Ergebnis an.

## 10. Transaktionsgrenzen

- Registrierung einer Submission ist eine kurze Transaktion.
- Ergebnis-Upsert, Verknüpfung der Submission und Zustand `RESULT_STORED` müssen in einer kurzen atomaren Datenbanktransaktion möglich sein.
- Discord-Aufrufe finden nie innerhalb dieser Transaktion statt.
- Spätere Zustandsübergänge verwenden erwartete Ausgangszustände beziehungsweise optimistische Konflikterkennung, damit zwei Worker nicht unbemerkt denselben Schritt ausführen.
- Pessimistische Sperren werden erst bei nachgewiesenem Bedarf eingeführt.

## 11. Testausführung

Der lokale Standardbuild bleibt:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Er startet keine Container und benötigt kein PostgreSQL.

PostgreSQL-Integrationstests verwenden ein eigenes Maven-Profil:

```bash
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Anforderungen an das Profil:

- echte PostgreSQL-Instanz über Testcontainers,
- Integrationstests über Maven Failsafe beziehungsweise eine klar getrennte gleichwertige Ausführung,
- Fehler, wenn keine Integrationstests gefunden oder ausgeführt werden,
- kein stilles Überspringen bei fehlendem Containerzugriff in GitHub Actions,
- fest gepinntes unterstütztes PostgreSQL-Image, nicht `latest`.

GitHub Actions führt sowohl den Docker-freien Standardbuild als auch das Datenbankintegrationsprofil in getrennt erkennbaren Schritten oder Jobs aus.

## Konsequenzen

### Positiv

- Doppelte Events und konkurrierende Upserts erzeugen keine doppelten Datensätze.
- Korrekturen besitzen eine klare Identität und überschreiben nicht unkontrolliert technische Discord-Zuordnungen.
- Der spätere Nachrichtenersetzungsablauf kann nach Neustarts fortgesetzt werden.
- Die lokale Entwicklung bleibt ohne Docker Desktop möglich.
- PostgreSQL-spezifisches Verhalten wird reproduzierbar in CI geprüft.

### Negativ

- Der Adapter verwendet für konfliktkritische Schreibzugriffe bewusst PostgreSQL-spezifisches SQL.
- Submission und Ergebnis sind getrennte Tabellen und benötigen explizite Mappinglogik.
- Standardbuild und vollständiger Datenbankbuild verwenden unterschiedliche Maven-Profile.
- Ein vollständiger manueller lokaler Persistenztest erfordert natives PostgreSQL oder eine optionale Container-Runtime.

## Verworfene Alternativen

### Nur JPA-`save()` ohne explizite Konfliktbehandlung

Verworfen, weil konkurrierende Inserts auf Business Keys zu schwer nachvollziehbaren Rollback- und Retrypfaden führen können.

### Quell-Message-ID nur zusammen mit Guild und Channel eindeutig machen

Für Discord-Snowflakes unnötig. Guild und Channel werden als Kontext gespeichert; die Message-ID genügt als primäre Eventidentität.

### Spieler mit zusätzlichem Surrogatschlüssel

Für genau zwei konfigurierte Discord-Spieler ohne alternative Identitätsquelle unnötig.

### Kanonische Bot-Message-ID ausschließlich an der Submission

Verworfen, weil Korrektureinreichungen dasselbe fachliche Ergebnis und dieselbe kanonische Nachricht betreffen.

### Speicherung von Bildern oder Discord-URLs im Persistenzinkrement

Verworfen. Version 1 benötigt nur Attachment-Metadaten; Rohbildspeicherung folgt mit dem Bildparser-Inkrement.
