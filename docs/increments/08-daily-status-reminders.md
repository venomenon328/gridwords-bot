# Inkrement 8: Tagesstatus, vollständige Serien und Erinnerungen

**Status:** automatisierte Abnahme vollständig; realer Discord-/PostgreSQL-Smoke-Test durch Tobias offen

**Issue:** #21

**Branch:** `feature/daily-status-reminders`
**Draft-PR:** #22

## Ergebnis

Inkrement 8 implementiert den vollständigen täglichen Nutzungszyklus auf Basis der historisch wirksamen Teilnehmermenge:

- genau eine persistente, editierbare Tagesstatusnachricht je Guild, Channel und Spieltag,
- GridWords- und QuadWords-Zustand sowie alle fünf persönlichen Serien je aktivem Spieler,
- gemeinsame Komplett- und Perfektserie ohne gemeinsame Aktivitätsserie,
- vorläufige Semantik ausschließlich für den aktuellen Berlin-Tag und endgültige Semantik für historische Tage,
- Reminder um 18:00 und 23:00 Uhr mit konkret fehlenden Spielen,
- ausschließlich ID-basierte User-Mentions für aktive Opt-ins,
- persistente Claims, Leases, Retry-Backoff, permanente Fehlerzustände, No-op, Supersession, Ablauf und Crash-Reconciliation.

Verbindliche Grundlagen sind [`../requirements/series-model.md`](../requirements/series-model.md), [`../requirements/dynamic-player-model.md`](../requirements/dynamic-player-model.md), [`../requirements/daily-status-reminders.md`](../requirements/daily-status-reminders.md), Issue #21 sowie ADR 0012.

## Fachliche Umsetzung

Die Statusprojektion verwendet den angeforderten Spieltag als Serien-Stichtag. Fehlende Einreichungen werden nur dann vorläufig behandelt, wenn dieser Stichtag dem aktuellen Tag der injizierten `Clock` in `Europe/Berlin` entspricht. Gestern und alle anderen historischen Tage werden endgültig berechnet. Ein Vortagsnachtrag rekonstruiert persönliche und gemeinsame Serien aus den persistierten Ergebnissen und den je Tag wirksamen Teilnahmezeiträumen.

Ein gültiges Ergebnis, eine Korrektur oder ein zulässiger Nachtrag darf den Status des Spieltags erzeugen beziehungsweise aktualisieren. Eine heute wirksame Aktivierung aktualisiert ausschließlich eine bereits vorhandene Statusnachricht und erzeugt morgens vor dem ersten Ergebnis oder Reminder keinen verfrühten Status. Ein Fehler dieses entkoppelten Refresh-Hooks rollt weder Ergebnis noch Teilnahmeänderung zurück. Prospektive Deaktivierungen ab morgen verändern den heutigen Status nicht.

Der Renderer erzeugt eine Discord-Nachricht mit bei Bedarf mehreren Embeds, enthält alle aktiven Spieler, deaktiviert Mentions und prüft Feld-, Embed- und Gesamtlimits vollständig. Eine nicht vollständig darstellbare Nachricht wird kontrolliert abgelehnt.

## Betrieb und Recovery

`daily_status_message` und `reminder_delivery` werden durch Liquibase angelegt und besitzen fachliche Unique Constraints. Claims sind tokengebunden und über Leases übernehmbar. Discord-I/O findet außerhalb der kurzen Datenbankoperationen statt.

Statusinhalte erhalten einen SHA-256-Fingerabdruck. Unveränderte Zustände prüfen nur die Existenz der Discord-Nachricht; geänderte Inhalte editieren dieselbe Message-ID. Nach externer Löschung wird genau ein Ersatz erzeugt. Stabile Footer-Schlüssel gleichen einen unklaren Discord-Ausgang ab und bereinigen gegebenenfalls Duplikate deterministisch.

Reminder-Kandidaten werden bei jedem tatsächlichen Sendeversuch neu ermittelt. `X/6` und `X/9` gelten als eingereicht. Ohne Kandidaten wird die Stufe persistent als No-op abgeschlossen. Beim Start nach 23 Uhr wird ausschließlich Stufe 2 gesendet und die frühere offene Stufe superseded; bereits erfolgreich abgeschlossene Stufe 1 bleibt terminal. Offene Stufen vergangener Tage laufen ab. Retryfähige Fehler erhalten Backoff, permanente Fehler erzeugen keinen Hot-Loop.

Startup und Minutentakt verwenden dieselben idempotenten Use Cases. Der aktuelle Tag wird vor dem ersten fälligen Reminder nur aktualisiert, wenn bereits ein Status existiert. Der Vortag wird auch nach einem vollständigen Bot-Ausfall neu rekonstruiert, sofern für ihn aktive Teilnehmer existierten. Zeitberechnung und DST-Grenzen verwenden die konfigurierte `ZoneId` und eine injizierte `Clock`.

## Automatisierte Abnahme aus Issue #21

Alle 24 automatisierbaren Kriterien sind abgedeckt:

1. Status-Create, Edit derselben Message-ID und Korrektur;
2. Ersatz nach externer Löschung und Duplikatbereinigung;
3. zwei, drei, wechselnde, inaktive und reine Reminder-Profile;
4. alle fünf persönlichen und beide gemeinsamen Serien;
5. vorläufiger heutiger Tag und endgültiger historischer Tag;
6. Tageswechsel, Vortagsnachtrag und Serienrekonstruktion;
7. entkoppelte Refresh-Fehler ohne Rollback;
8. Kandidatenselektion mit konkret fehlenden Spielen einschließlich `X/6` und `X/9`;
9. zweite Stufe mit erneuter Kandidatenbewertung;
10. persistenter No-op ohne Discord-Nachricht;
11. strikt begrenzte User-Allowed-Mentions;
12. Startup vor, zwischen und nach den Reminderzeiten;
13. Supersession nach 23 Uhr und Ablauf vergangener Stufen;
14. konkurrierende Claims für Status und Reminder;
15. retryfähige und permanente Fehler ohne Hot-Loop;
16. Sommerzeitlücke und Winterzeitüberlappung in `Europe/Berlin`;
17. Discord-Feld-, Embed-, Nachrichten- und Gesamtlimits;
18. Liquibase, Constraints, Lease-Übernahme und Recovery gegen echtes PostgreSQL;
19. vollständige GridWords-/QuadWords-, Publish-, Edit-, Delete-, Parser-, Renderer- und Architekturregression.

Die zusammengefassten Punkte entsprechen vollständig den einzeln nummerierten Kriterien 1 bis 24 in Issue #21.

## Testnachweis

Finaler CI-Stand:

- `mvn --batch-mode --no-transfer-progress clean verify`: 247 Tests, 0 Fehler, 0 übersprungen.
- `mvn --batch-mode --no-transfer-progress -Pdatabase-integration clean verify`: 247 Standardtests plus 73 PostgreSQL-Integrationstests, jeweils 0 Fehler und 0 übersprungen.

Die Standardtests öffnen keine Datenbank-, Container- oder Discord-Verbindung und benötigen keinen Token. Das Profil `database-integration` verwendet PostgreSQL 16.6 über Testcontainers und führt alle realen Liquibase-Migrationen aus.

## Nicht-Ziele

Unverändert außerhalb dieses Inkrements liegen Wochen-/Monatsberichte, Statistik-Commands, konfigurierbare Reminderzeiten per Slash-Command, regelbasierte Kommentare, Mehrserverbetrieb und Änderungen an Share- oder Bildparsern.

## Noch offene manuelle Abnahme

Ausschließlich Tobias’ realer Discord-/PostgreSQL-Smoke-Test bleibt offen. Er prüft mindestens drei aktive Spieler, Status-Create/Edit, alle drei Statussymbole, echte begrenzte User-Mentions, beide Reminderstufen, Neustart-Catch-up, Vortagsnachtrag/Finalisierung und unveränderte sichere Ergebnisersetzung. PR #22 bleibt bis dahin Draft und ungemergt.