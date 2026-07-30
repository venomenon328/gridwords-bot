# Zwischeninkrement 7.2: Dynamische Spieler, Teilnahmezeiträume und Reminder-Opt-in

**Status:** vorbereitet; Umsetzung offen  
**Issue:** #19  
**Branch:** `feature/dynamic-player-participation`

## Ziel

Die feste Zwei-Spieler-Konfiguration wird durch dynamische Spielerprofile ersetzt. Ein vollständig gültiges GridWords- oder QuadWords-Share registriert beziehungsweise reaktiviert den Autor automatisch. Historische gemeinsame Serien verwenden datierte Teilnahmezeiträume. Reminder-Opt-in wird als unabhängige Self-Service-Einstellung vorbereitet.

Verbindliche Fachdetails stehen in:

- [`docs/requirements/dynamic-player-model.md`](../requirements/dynamic-player-model.md)
- [`docs/requirements/series-model.md`](../requirements/series-model.md)
- Issue #19

## Geplanter Umfang

- Listener akzeptiert jeden menschlichen Autor im konfigurierten Server und Channel.
- Normale Texte bleiben ohne DB-Zugriff ignoriert.
- Ungültige Shares erzeugen kein Spielerprofil.
- Vollständig gültige Shares legen Spieler an oder synchronisieren sie.
- serverbezogener Anzeigename mit Fallback auf globalen Username.
- dynamische Administratorflags weiterhin aus externer Admin-ID-Liste.
- `player.active` als aktueller Status.
- datierte, nicht überlappende Teilnahmezeiträume für historische gemeinsame Serien.
- automatische Aktivierung ab fachlichem Share-Spieltag.
- Self-Service `/participation join|leave|status`.
- Admin `/player activate|deactivate|status`.
- Self-Service `/reminders on|off|status`.
- `reminder_opt_in` unabhängig von Teilnahmeaktivität.
- gemeinsame Komplett-/Perfektserie über alle am jeweiligen Tag aktiven Spieler.
- mindestens zwei aktive Spieler für einen gemeinsamen Serientag.
- transportneutraler Reminder-Kandidaten-Port für Inkrement 8.
- Entfernung der festen `PLAYER_1_*`-/`PLAYER_2_*`-Konfiguration.
- Liquibase-Backfill für bestehende Spieler und Ergebnisse.

## Wirksamkeitsregeln

- automatisches Share: Aktivierung ab fachlichem `game_date`,
- Self-Service-/Admin-Aktivierung: ab aktuellem Berlin-Tag,
- Self-Service-/Admin-Deaktivierung: ab folgendem Berlin-Tag,
- frühere Tage bleiben unverändert,
- Wiedereintritt erzeugt einen neuen Zeitraum.

## Reminder-Grundlage

Noch kein Scheduler und kein Versand. Vorbereitet werden:

- persistentes globales Opt-in,
- Auswahl aktiver Opt-ins mit mindestens einem fehlenden Spiel,
- konkrete fehlende Spieltypen je Spieler,
- ID-basierte Mention-Daten,
- spätere Allowed-Mentions ausschließlich für ausgewählte User-IDs.

## Nicht-Ziele

- Tagesstatus,
- zeitgesteuerte Reminder,
- Wochen-/Monatsberichte,
- allgemeine Statistik-Commands,
- Mehrserver-/Mehrchannel-Unterstützung,
- automatische Deaktivierung bei Serveraustritt,
- Parser- oder Rendereränderungen,
- neue Publish-/Delete-Zustandsmaschine.

## Validierung

```powershell
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify
```

Nach automatisierter Umsetzung bleibt ein realer Discord-/PostgreSQL-Smoke-Test mit mindestens drei Nutzern offen.
