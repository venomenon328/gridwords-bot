# Vollständige persönliche Achievement-Liste

**Status:** verbindlicher Nachtrag zu Inkrement 13; durch Inkrement 14 erweitert  
**Stand:** 9. August 2026

Dieser Nachtrag ergänzt `docs/requirements/achievements.md`, ohne die Semantik von `/achievements` zu ersetzen. Die Inkrement-14-Erweiterungen sind zusätzlich in `docs/requirements/ux-qol.md` verbindlich beschrieben.

## Command

```text
/achievement-list
```

Der Command ist eine persönliche, strikt read-only Katalogansicht. Er bleibt self-only und erhält ab Inkrement 14 optionale Filter.

## Standarddarstellung

Ohne gesetzte Filter gilt weiterhin:

- Es werden **alle 60** Definitionen aus dem aktuellen `achievements-v1`-Katalog angezeigt.
- Die Reihenfolge ist deterministisch und folgt dem Katalog; die vorhandenen Darstellungskategorien dürfen als Überschriften verwendet werden.
- Jede Definition enthält weiterhin ihr Achievement-Emoji, den vollständigen Anzeigenamen und die vollständige Beschreibung.
- Der persönliche Zustand wird ausschließlich binär dargestellt:
  - `✅` = aktuell aktive Vergabe,
  - `❌` = nicht vorhanden oder aktuell invalidiert.
- Es gibt ausdrücklich **keinen** Teilfortschritt wie `6/10`, keinen Prozentwert und keine Fortschrittsleiste.
- Die Ausgabe ist vollständig ephemeral und mention-sicher.
- Die maximale Discord-Nachrichtenkapazität darf über mehrere Embeds derselben Interaction genutzt werden; die vollständige gefilterte Ausgabe darf nicht abgeschnitten werden.

## Optionale Filter ab Inkrement 14

Alle Filter sind optional und frei kombinierbar. Eine fehlende Option bedeutet jeweils `Alle`. Die Filterung darf die relative Katalogreihenfolge der verbleibenden Definitionen nicht verändern.

### `game`

| Sichtbarer Wert | Fachlicher Filter |
|---|---|
| Alle | alle Scopes einschließlich `GLOBAL` |
| GridWords | ausschließlich `GRIDWORDS` |
| QuadWords | ausschließlich `QUADWORDS` |
| GW+QW | ausschließlich `CROSS_GAME` |

Es gibt bewusst keinen eigenen Wert `Allgemein`. Globale Achievements erscheinen nur bei `game=Alle`.

Die Zuordnung erfolgt ausschließlich über `AchievementDefinition.scope()` beziehungsweise gleichwertige typisierte Katalogmetadaten, nicht über Schlüssel- oder Namenspräfixe.

### `category`

| Sichtbarer Wert | Fachlicher Filter |
|---|---|
| Alle | alle Kategorien |
| Erfahrung | `EXPERIENCE` |
| Zuverlässigkeit | `RELIABILITY` |
| Leistung | `PERFORMANCE` |
| Besonderes | `SPECIAL` |

### `status`

| Sichtbarer Wert | Fachlicher Filter |
|---|---|
| Alle | alle Definitionen |
| Freigeschaltet | ausschließlich aktuell `ACTIVE` |
| Offen | fehlend oder aktuell `INVALIDATED` |

Ein Filterergebnis ohne Definitionen ist ein normaler Leerzustand und wird sinngemäß mit

```text
Keine Achievements entsprechen den gewählten Filtern.
```

beantwortet.

## Datenbasis

Der Command liest ausschließlich:

1. den codebasierten aktuellen Achievement-Katalog und
2. die materialisierte aktuelle Award-State-Projektion des Aufrufers.

Er löst keinen History-Scan, Evaluator, Reconciler, Player-Sync oder sonstigen Schreibzugriff aus.

Insbesondere dürfen Filter nicht dazu führen, dass gesperrte Definitionen historisch neu ausgewertet werden.

## Abgrenzung zu `/achievements`

`/achievements` bleibt die Profilansicht für aktuell aktive Achievements mit Self-/Other- sowie bestehender Game-Filter-Semantik und zeigt ab Inkrement 14 zusätzlich das fachliche Freischaltdatum `earnedOn`.

`/achievement-list` bleibt dagegen self-only und ist die persönliche Katalogansicht mit binärem `✅`/`❌`-Status. Auch mit Filtern führt sie weiterhin **kein Progress-System** ein.

## Akzeptanz

Zusätzlich zur bestehenden 60-Fälle-Matrix gelten:

1. `/achievement-list` fragt ausschließlich den Aufrufer ab und bleibt read-only/ephemeral.
2. Ohne Optionen werden alle 60 Definitionen vollständig und in stabiler Reihenfolge ausgegeben.
3. Nur aktuell `ACTIVE` Awards erhalten `✅`; fehlende und `INVALIDATED` Awards erhalten `❌`.
4. `game=GridWords`, `QuadWords`, `GW+QW` und `Alle` filtern exakt nach den festgelegten Scopes; `GLOBAL` erscheint nur bei `Alle`.
5. `category` filtert exakt nach den vier vorhandenen Kategorien.
6. `status=Freigeschaltet` enthält nur ACTIVE; `status=Offen` enthält fehlende und INVALIDATED Definitionen.
7. Alle drei Filter sind frei kombinierbar und erhalten die Katalogreihenfolge.
8. Ein leeres Filterergebnis wird neutral dargestellt.
9. Es werden keine Fortschrittswerte erzeugt und kein Player-/Event-/Achievement-State verändert.
10. Die vollständige gefilterte Ausgabe bleibt innerhalb der Discord-Embed-/Nachrichtengrenzen und wird nicht abgeschnitten.
