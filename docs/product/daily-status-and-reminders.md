# Tagesstatus und Reminder

## Kanonischer Tagesstatus

Für jeden Server, Channel und Spieltag existiert höchstens eine aktive kanonische Statusnachricht. Sie enthält alle Spieler aus `U(d)`, sortiert nach Anzeigename ohne Beachtung der Groß-/Kleinschreibung und anschließend nach Discord-ID.

Je Spieler und Spiel wird einer der Zustände gelöst, nicht gelöst (`X`), fehlt oder nicht teilnehmend dargestellt. Nicht anwendbare Werte erscheinen neutral als `—`. Zusätzlich zeigt der Status die fünf persönlichen und vier gemeinsamen Serien aus [`streaks.md`](streaks.md).

Die Statusnachricht entsteht beim ersten gültigen Ergebnis beziehungsweise spätestens bei einem fälligen Reminder, sofern Teilnehmer vorhanden sind. Externe Löschung wird über die persistierte Delivery kontrolliert reconciled; technische Delivery-Schlüssel erscheinen nicht sichtbar im Discord-Inhalt.

## Interaktion und Details

GridWords und QuadWords besitzen getrennte Select-Menüs. Eine Seite enthält höchstens 25 Optionen, pro Spiel werden höchstens zwei Menüs benötigt. Mehr als 50 Teilnehmer eines Spiels ist ein dauerhafter Renderingfehler und wird nicht durch unvollständige Ausgabe kaschiert.

Optionen zeigen den Status sowie, falls vorhanden, Versuchszahl und Dauer. Der Server prüft bei jeder Interaktion Guild, Channel, Statusnachricht, Spieltag und historische Teilnahme. Zwei reine Link-Buttons führen zu GridWords und QuadWords.

Die ephemere Ergebnisdetailansicht zeigt in dieser Reihenfolge:

1. Ergebnis beziehungsweise QuadWords-Boards,
2. ausgewählten Ausredentext,
3. aktuell von genau diesem Ergebnis gehaltene Rekorde,
4. am Spieltag erworbene aktuell aktive Achievements.

Leere optionale Bereiche werden nicht als Platzhalter ausgegeben.

## Persönlicher `/status`

Der Root-Command `/status` ist self-only, ohne Optionen, vollständig ephemer und strikt read-only. Er verwendet diese Reihenfolge:

1. **Heute:** je Spiel gelöst, `X`, offen oder keine Teilnahme einschließlich Versuchswert und Dauer bei vorhandener Einreichung,
2. **Laufende Serien:** alle fünf persönlichen Serien mit derselben vorläufigen Heute-Semantik wie der Tagesstatus,
3. **Teilnahme und Einstellungen:** aktueller GridWords-/QuadWords-Teilnahmestatus samt wirksamem Zeitraum und kompakter globaler Reminderstatus `an/aus`,
4. **Letzte Einreichungen:** je Spiel die letzte gültige Einreichung einschließlich Ergebnis, Dauer, Spieltag und tatsächlichem Empfangszeitpunkt.

Frühere Einreichungen bleiben sichtbar, auch wenn die heutige Teilnahme am betreffenden Spiel inzwischen beendet ist. `/status` enthält keine Rekord- oder Achievement-Sektion und führt keinen Player-Sync oder sonstigen Schreibzugriff aus. Für einen unbekannten Nutzer wird kein Profil angelegt.

## Reminder

Standardmäßig werden um 16:00 und 22:00 Uhr aggregierte Reminder für den aktuellen Spieltag geprüft. GridWords-Kandidaten kommen aus `G(d)`, QuadWords-Kandidaten aus `Q(d)`. Ein eingereichtes `X` zählt als vorhanden und löst keinen fehlenden Reminder aus.

Reminder-Opt-in-Spieler werden mit eingeschränkten Allowed Mentions erwähnt; Opt-out-Spieler erscheinen nur mit bereinigtem Anzeigenamen. Sind keine Ergebnisse offen, wird keine Nachricht versendet. Nach einem Neustart wird je Lauf höchstens der jüngste heute fällige Slot nachgeholt; vergangene Spieltage verfallen.

`/reminders on`, `/reminders off` und `/reminders status` antworten ephemer. Die ausführliche Antwort erklärt, dass `aus` nur echte Mentions verhindert und der bereinigte Klartextname weiterhin in der gemeinsamen Übersicht erscheinen kann. Angezeigte Reminderzeiten stammen aus der tatsächlich gebundenen Laufzeitkonfiguration und werden nicht als vermeintliche Ist-Werte hart codiert. Im persönlichen `/status` bleibt die Darstellung bewusst auf `Reminder: an/aus` beschränkt.

Die erste Reminder-Nachricht eines Tages wird erst pensioniert, wenn die zweite Stufe dauerhaft erfolgreich `SENT` oder als `NO_CANDIDATES` abgeschlossen ist. Ein laufender, retryfähiger oder permanenter Fehler der zweiten Stufe reicht dafür nicht aus.

## Tagesabschluss und Channel-Retention

Die fachliche Tagesgrenze ist 06:00 Uhr in `Europe/Berlin`. Ab diesem Cutoff wird der Vortag in dieser Reihenfolge idempotent reconciled:

1. gestrigen Tagesstatus mit historisch endgültiger Semantik sicher finalisieren oder rekonstruieren,
2. erst nach erfolgreicher Finalisierung die individuellen kanonischen Ergebnisnachrichten des Vortags pensionieren und löschen,
3. verbleibende Reminder-Nachrichten des Vortags pensionieren und löschen,
4. den heutigen Tagesstatus erzeugen oder rekonstruieren, sofern Teilnehmer vorhanden sind.

Ein Fehler der gestrigen Statusfinalisierung blockiert die Ergebnisbereinigung. Pensionierte Ergebnisnachrichten werden durch Korrektur, Retry oder Startup-Recovery nicht wieder angelegt; die Ergebnisdaten selbst bleiben vollständig in PostgreSQL erhalten. `Unknown Message` beim idempotenten Delete gilt als Erfolg, retryfähige und permanente Fehler werden persistent getrennt behandelt.

Entscheidend ist die lokale Cutoff-Zeit, nicht der tatsächliche Scheduler-Laufzeitpunkt. Startup und Scheduler verwenden dieselben wiederaufnehmbaren Use Cases. Scheduler sind nur Trigger; Fälligkeit, Claims, Leases, Retry und terminale Zustände werden persistent verarbeitet.
