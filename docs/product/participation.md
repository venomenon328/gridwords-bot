# Spielbezogene Teilnahme

## Modell

Teilnahme wird historisch und je Spiel geführt. Für einen Spieltag `d` gelten:

- `G(d)`: GridWords-Teilnehmer
- `Q(d)`: QuadWords-Teilnehmer
- `U(d) = G(d) ∪ Q(d)`: Teilnehmer an mindestens einem Spiel
- `B(d) = G(d) ∩ Q(d)`: Teilnehmer an beiden Spielen

Ein Teilnahmezeitraum gehört immer zu einem Spieltyp. `active_from` ist einschließlich, `inactive_from` ausschließlich. Das aktuelle Feld `player.active` ist nur eine aus der heutigen Union abgeleitete Kompatibilitätsinformation und keine historische Quelle.

## Aktivierung durch Shares

Ein gültiges Share aktiviert ausschließlich seinen Spieltyp ab dem Spieltag des Shares. Ein Nutzer kann dadurch erstmals Spieler werden. Eine bestehende Teilnahme am anderen Spiel wird nicht verändert.

Der globale Reminderstatus bleibt beim Hinzufügen eines zweiten Spiels unverändert. Nach vollständiger Inaktivität wird er bei einem späteren Wiedereintritt wieder aktiviert.

## Commands

Die Teilnahme wird über `/participation join` und `/participation leave` gesteuert; `/player activate` und `/player deactivate` sind kompatible Oberflächen für denselben Zustand. Der Parameter `game` akzeptiert `gridwords`, `quadwords` oder `both` und verwendet ohne Angabe `both`.

- Join/Aktivierung wirkt ab heute.
- Leave/Deaktivierung wirkt ab morgen, damit der aktuelle Spieltag stabil bleibt.
- `both` ändert beide Spieltypen atomar; bei einem Teilfehler wird keine Seite übernommen.
- Wiederholte gleichartige Commands sind idempotent.

`/participation status`, `/player status` und `/status` lesen den aktuellen Zustand. `/status` ist eine persönliche, ephemere Übersicht und verändert weder Teilnahme noch Reminderstatus.

## Reminder-Opt-out

Reminder-Einwilligung ist global pro Spieler, nicht pro Spiel. Ein Opt-out entfernt den Spieler aus allen Reminder-Erwähnungen, beendet aber keine Teilnahme und beeinflusst keine Ergebnis-, Serien- oder Berichtsberechnung.

## Verwendung

Alle zeitabhängigen Funktionen verwenden die am betreffenden Tag historisch wirksame Menge:

- spielbezogene Ergebnisse, Lösungsserien, Reminder und Statistiknenner verwenden `G(d)` oder `Q(d)`,
- Aktivität verwendet `U(d)`,
- Komplett- und Perfektmetriken verwenden `B(d)`.

Nicht teilnehmende Spieler werden nicht als fehlend oder erfolglos gewertet.
