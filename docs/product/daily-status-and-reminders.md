# Tagesstatus und Reminder

## Kanonischer Tagesstatus

Für jeden Server, Channel und Spieltag existiert genau eine kanonische Statusnachricht. Sie enthält alle Spieler aus `U(d)`, sortiert nach Anzeigename ohne Beachtung der Groß-/Kleinschreibung und anschließend nach Discord-ID.

Je Spieler und Spiel wird einer der Zustände gelöst, nicht gelöst (`X`), fehlt oder nicht teilnehmend dargestellt. Nicht anwendbare Werte erscheinen neutral als `—`. Zusätzlich zeigt der Status die fünf persönlichen und vier gemeinsamen Serien aus [`streaks.md`](streaks.md).

## Interaktion und Details

GridWords und QuadWords besitzen getrennte Select-Menüs. Eine Seite enthält höchstens 25 Optionen, pro Spiel werden höchstens zwei Menüs benötigt. Mehr als 50 Teilnehmer eines Spiels ist ein dauerhafter Renderingfehler und wird nicht durch unvollständige Ausgabe kaschiert.

Optionen zeigen den Status sowie, falls vorhanden, Versuchszahl und Dauer. Der Server prüft bei jeder Interaktion Guild, Channel, Statusnachricht, Spieltag und historische Teilnahme. Zwei Link-Buttons führen zu den Spielen.

Die ephemere Ergebnisdetailansicht zeigt in dieser Reihenfolge:

1. Ergebnis beziehungsweise QuadWords-Boards,
2. ausgewählten Ausredentext,
3. aktuell von diesem Ergebnis gehaltene Rekorde,
4. am Spieltag erworbene aktive Achievements.

## Reminder

Standardmäßig werden um 16:00 und 22:00 Uhr aggregierte Reminder für den aktuellen Spieltag geprüft. GridWords-Kandidaten kommen aus `G(d)`, QuadWords-Kandidaten aus `Q(d)`. Ein eingereichtes `X` zählt als vorhanden und löst keinen fehlenden Reminder aus.

Reminder-Opt-in-Spieler werden mit eingeschränkten Allowed Mentions erwähnt; Opt-out-Spieler erscheinen nur mit bereinigtem Anzeigenamen. Sind keine Ergebnisse offen, wird keine Nachricht versendet. Nach einem Neustart wird je Lauf höchstens der jüngste heute fällige Slot nachgeholt; vergangene Spieltage verfallen.

## Tagesabschluss

Die fachliche Tagesgrenze ist 06:00 Uhr in `Europe/Berlin`. Der Cleanup finalisiert den Vortag, entfernt veraltete Interaktionskomponenten von Ergebnissen und Remindern und stellt den aktuellen Tagesstatus her. Entscheidend ist die lokale Cutoff-Zeit, nicht der tatsächliche Scheduler-Laufzeitpunkt.

Scheduler sind nur Trigger. Fälligkeit, Claim, Retry und terminaler Zustand werden persistent und idempotent verarbeitet.
