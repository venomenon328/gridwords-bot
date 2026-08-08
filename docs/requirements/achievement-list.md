# Vollständige persönliche Achievement-Liste

**Status:** verbindlicher Nachtrag zu Inkrement 13 / Paket 13.8  
**Stand:** 8. August 2026

Dieser Nachtrag ergänzt `docs/requirements/achievements.md`, ohne die Semantik von `/achievements` zu verändern.

## Command

```text
/achievement-list
```

Der Command ist eine persönliche, read-only Katalogansicht und besitzt in V1 keine Optionen.

## Darstellung

- Es werden **alle 60** Definitionen aus dem aktuellen `achievements-v1`-Katalog angezeigt.
- Die Reihenfolge ist deterministisch und folgt dem Katalog; die vorhandenen Darstellungskategorien dürfen als Überschriften verwendet werden.
- Jede Definition enthält weiterhin ihr Achievement-Emoji, den vollständigen Anzeigenamen und die vollständige Beschreibung.
- Der persönliche Zustand wird ausschließlich binär dargestellt:
  - `✅` = aktuell aktive Vergabe,
  - `❌` = nicht vorhanden oder aktuell invalidiert.
- Es gibt ausdrücklich **keinen** Teilfortschritt wie `6/10`, keinen Prozentwert und keine Fortschrittsleiste.
- Die Ausgabe ist vollständig ephemeral und mention-sicher.
- Die maximale Discord-Nachrichtenkapazität darf über mehrere Embeds derselben Interaction genutzt werden; der V1-Katalog muss vollständig in eine Interaction passen und darf nicht abgeschnitten werden.

## Datenbasis

Der Command liest ausschließlich:

1. den codebasierten aktuellen Achievement-Katalog und
2. die materialisierte aktuelle Award-State-Projektion des Aufrufers.

Er löst keinen History-Scan, Evaluator, Reconciler, Player-Sync oder sonstigen Schreibzugriff aus.

## Abgrenzung zu `/achievements`

`/achievements` bleibt die Profilansicht für aktuell aktive Achievements und behält seine bestehende Self-/Other- sowie Game-Filter-Semantik.

`/achievement-list` ist dagegen bewusst self-only und zeigt den vollständigen Katalog mit binärem persönlichem Status. Die neue Ansicht führt damit weiterhin **kein Progress-System** ein.

## Akzeptanz

Zusätzlich zur bestehenden 60-Fälle-Matrix gelten für diesen Nachtrag:

1. `/achievement-list` besitzt keine Optionen und fragt ausschließlich den Aufrufer ab.
2. Alle 60 Definitionen werden vollständig und in stabiler Reihenfolge ausgegeben.
3. Nur aktuell `ACTIVE` Awards erhalten `✅`; fehlende und `INVALIDATED` Awards erhalten `❌`.
4. Es werden keine Fortschrittswerte erzeugt und der Aufruf bleibt read-only ohne Player-/Event-Seiteneffekte.
5. Die vollständige Ausgabe passt in eine ephemere Discord-Interaction innerhalb der Embed-/Zeichengrenzen.
