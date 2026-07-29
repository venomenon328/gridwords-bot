# ADR 0009: Persistierte GridWords-Quellloeschung mit Recovery

- **Status:** akzeptiert
- **Datum:** 2026-07-29
- **Ergaenzt:** ADR 0002, ADR 0006 und ADR 0008

## Kontext

ADR 0002 legt fest, dass eine fremde Originalnachricht erst nach der persistierten kanonischen Bot-Message-ID geloescht werden darf. Der kanonische Publish-Ablauf besitzt bereits Claim-, Write-ahead- und Reconciliation-Semantik. Fuer die anschliessende externe Loeschung fehlt noch eine ebenso dauerhaft rekonstruierbare zweite Phase.

Discord und PostgreSQL koennen nicht gemeinsam atomar abschliessen. Insbesondere kann ein Prozess nach einem erfolgreichen Discord-Delete und vor dem Datenbank-Update sterben. Korrekturen erzeugen zudem mehrere Quellen fuer dasselbe kanonische Ergebnis.

## Entscheidung

Die bestehende kanonische Veroeffentlichung wird nicht neu entworfen. Ausschliesslich danach laeuft fuer GridWords eine eigene, kurz claim-geschuetzte Quellloeschphase.

1. Nur eine Submission im Zustand `CANONICAL_MESSAGE_PUBLISHED` darf eine Loesch-Claim erhalten. Eine supersedierte Quelle ist erst berechtigt, wenn eine neuere Quelle desselben Ergebnisses bereits kanonisch bestaetigt ist.
2. Der Persistence-Adapter speichert Claim-Token und Lease sowie die Fehlerklasse `NONE`, `RETRYABLE` oder `PERMANENT`. Das Token ist fuer das Persistieren des externen Ergebnisses zwingend.
3. Der Discord-Aufruf erfolgt ausserhalb jeder Datenbanktransaktion und immer mit exakt gespeicherter Channel- und Source-Message-ID.
4. `DELETED` und Discord `UNKNOWN_MESSAGE` bedeuten denselben idempotenten Erfolg: atomar wird `ORIGINAL_MESSAGE_DELETED` mit `original_deleted_at` persistiert, danach folgt `COMPLETED` ohne weiteren Discord-Aufruf.
5. Transiente Fehler werden als `RETRYABLE` gespeichert und kontrolliert erneut eingeplant. Permanente Rechte- oder Channel-Fehler werden als `PERMANENT` gespeichert; Quelle und kanonische Nachricht bleiben sichtbar. Der Scheduler ist nur eine Beschleunigung, nie Quelle der Wahrheit.
6. Beim Start wird die persistierte offene Arbeit erneut gelesen. Aus `ORIGINAL_MESSAGE_DELETED` wird ausschliesslich abgeschlossen. Aus einem noch nicht bestaetigten Delete wird derselbe Discord-Delete erneut versucht; nach einem Absturz ist ein moegliches `UNKNOWN_MESSAGE` deshalb sicher.
7. Ein verlorener oder abgelaufener Claim darf keinen Abschluss und keine Fehlerklasse fuer einen anderen Worker persistieren. Parallele Worker koennen somit hoechstens einen gueltigen externen Delete besitzen.
8. GridWords erhaelt nach diesem Schritt keine Erfolgsreaktion mehr, weil die Source kurz darauf verschwindet. QuadWords behaelt sein bisheriges Erfolgs-`?`, und persistente Ablehnungen behalten `??`.

## Zustandsfolge

```text
RESULT_STORED
  -> CANONICAL_MESSAGE_PUBLISHED
  -> ORIGINAL_MESSAGE_DELETED
  -> COMPLETED
```

Bei transienten oder permanenten Loeschfehlern bleibt die Submission in `CANONICAL_MESSAGE_PUBLISHED` beziehungsweise `SUPERSEDED`; nur die persistierte Fehlerklasse aendert sich. Eine noch nicht bestaetigte supersedierte Quelle ist nicht loeschbar. `COMPLETED` ist idempotent und wird bei Replay nicht erneut an Discord gesendet.

## Konsequenzen

- Die Sicherheit des etablierten Publish-Ablaufs bleibt erhalten: Kein Delete vor persistierter kanonischer ID.
- Ein unbekannter Discord-Delete-Ausgang kann wiederholt werden, ohne eine andere Nachricht zu beruehren.
- PostgreSQL besitzt zusaetzliche Spalten und Constraints; die Recovery ist daher auch ohne Scheduler nachweisbar.
- Der Ablauf ist absichtlich auf GridWords beschraenkt. QuadWords, Tagesstatus und Erinnerungen werden nicht vorgezogen.

## Verworfene Alternativen

### Loeschung direkt nach dem Discord-Publish ohne persistierte Phase

Unzulaessig: Ein Absturz zwischen Publish und Persistenz kann die alleinige sichtbare Quelle verlieren.

### Eine offene Datenbanktransaktion waehrend des Discord-Deletes

Unwirksam gegen fehlende verteilte Atomaritaet und schaedlich durch lange Sperren.

### Die Quelle bei einem unklaren Delete-Ausgang als abgeschlossen markieren

Unzulaessig: Es kann weder bewiesen werden, dass die richtige Message geloescht wurde, noch ist der Abschluss rekonstruierbar.
